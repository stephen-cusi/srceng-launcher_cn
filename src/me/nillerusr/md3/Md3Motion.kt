package me.nillerusr.md3

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.valvesoftware.source.R

/**
 * Material 3 Expressive 动效。
 *
 * Expressive 的核心差异在于用「弹簧物理」替代固定时长的缓动曲线：交互反馈带有可感知的
 * 回弹与超调，而不是线性收敛。这里用 androidx.dynamicanimation 提供三档弹簧参数，
 * 对应规范里的 Fast / Default / Slow Spatial 三条曲线。
 */
object Md3Motion {

    /** Spatial（位移/缩放）弹簧：允许轻微超调，这是 Expressive 的标志性手感。 */
    const val DAMPING_SPATIAL_FAST = 0.85f
    const val DAMPING_SPATIAL_DEFAULT = 0.75f
    const val DAMPING_SPATIAL_SLOW = 0.65f

    const val STIFFNESS_FAST = 1400f
    const val STIFFNESS_DEFAULT = 700f
    const val STIFFNESS_SLOW = 380f

    /** Effects（颜色/透明度）弹簧：临界阻尼，不允许超调，避免闪烁。 */
    const val DAMPING_EFFECTS = 1.0f

    private val TAG_KEY_SPRINGS = R.id.md3_motion_springs

    private fun springOf(
        view: View,
        property: DynamicAnimation.ViewProperty,
        stiffness: Float,
        damping: Float
    ): SpringAnimation {
        @Suppress("UNCHECKED_CAST")
        var map = view.getTag(TAG_KEY_SPRINGS) as? HashMap<DynamicAnimation.ViewProperty, SpringAnimation>
        if (map == null) {
            map = HashMap()
            view.setTag(TAG_KEY_SPRINGS, map)
        }
        return map.getOrPut(property) {
            SpringAnimation(view, property).apply {
                spring = SpringForce().apply {
                    this.stiffness = stiffness
                    dampingRatio = damping
                }
                setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
            }
        }.also {
            it.spring.stiffness = stiffness
            it.spring.dampingRatio = damping
        }
    }

    /** 弹到目标值。同一 view + property 复用同一个 SpringAnimation，保证中途改向不会打架。 */
    @JvmStatic
    @JvmOverloads
    fun springTo(
        view: View,
        property: DynamicAnimation.ViewProperty,
        target: Float,
        stiffness: Float = STIFFNESS_DEFAULT,
        damping: Float = DAMPING_SPATIAL_DEFAULT
    ) {
        try {
            springOf(view, property, stiffness, damping).animateToFinalPosition(target)
        } catch (_: Throwable) {
        }
    }

    /**
     * 按压回弹：按下时缩到 [pressedScale]，抬起时用弹簧回弹到 1（带轻微超调）。
     * 监听器一律 return false，事件继续交给 View 自己处理，点击与无障碍行为不受影响，
     * 因此不需要（也不能）在这里调 performClick——那会导致点击触发两次。
     */
    @SuppressLint("ClickableViewAccessibility")
    @JvmStatic
    @JvmOverloads
    fun attachPressBounce(view: View, pressedScale: Float = 0.94f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    springTo(v, SpringAnimation.SCALE_X, pressedScale, STIFFNESS_FAST, DAMPING_SPATIAL_FAST)
                    springTo(v, SpringAnimation.SCALE_Y, pressedScale, STIFFNESS_FAST, DAMPING_SPATIAL_FAST)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    springTo(v, SpringAnimation.SCALE_X, 1f, STIFFNESS_DEFAULT, DAMPING_SPATIAL_SLOW)
                    springTo(v, SpringAnimation.SCALE_Y, 1f, STIFFNESS_DEFAULT, DAMPING_SPATIAL_SLOW)
                }
            }
            false
        }
    }

    /** 批量给可点击控件挂按压回弹。 */
    @JvmStatic
    @JvmOverloads
    fun attachPressBounce(vararg views: View?, pressedScale: Float = 0.94f) {
        for (v in views) if (v != null) attachPressBounce(v, pressedScale)
    }

    /**
     * 入场：子元素依次从下方弹入。Expressive 强调「一组元素有节奏地到位」，
     * 而不是整屏一起淡入。
     * @param stagger 相邻元素的错峰毫秒数
     */
    @JvmStatic
    @JvmOverloads
    fun enterStaggered(container: ViewGroup, stagger: Long = 45L, offsetDp: Float = 24f) {
        val density = container.resources.displayMetrics.density
        val offset = offsetDp * density
        var index = 0
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            child.alpha = 0f
            child.translationY = offset
            val delay = stagger * index
            index++
            child.postDelayed({
                springTo(child, SpringAnimation.TRANSLATION_Y, 0f, STIFFNESS_SLOW, DAMPING_SPATIAL_DEFAULT)
                child.animate().alpha(1f).setDuration(220L).start()
            }, delay)
        }
    }

    /** 展开/收起时的高度形变，用弹簧驱动 scaleY，避免 layout 抖动。 */
    @JvmStatic
    fun morphIn(view: View) {
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.alpha = 0f
        springTo(view, SpringAnimation.SCALE_X, 1f, STIFFNESS_DEFAULT, DAMPING_SPATIAL_SLOW)
        springTo(view, SpringAnimation.SCALE_Y, 1f, STIFFNESS_DEFAULT, DAMPING_SPATIAL_SLOW)
        view.animate().alpha(1f).setDuration(180L).start()
    }
}
