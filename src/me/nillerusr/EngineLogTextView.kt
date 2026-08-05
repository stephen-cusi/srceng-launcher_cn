package me.nillerusr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.max

class EngineLogTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sourceLines = IntArray(0)
    private var sourceOffsets = IntArray(0)
    private var gutterWidth = dp(52)
    private val contentInset = dp(12)

    init {
        setPadding(gutterWidth + contentInset, dp(10), contentInset, dp(24))
        linePaint.textSize = textSize
        dividerPaint.strokeWidth = dpF(1)
    }

    fun setLineMetadata(lines: IntArray, offsets: IntArray) {
        sourceLines = lines
        sourceOffsets = offsets
        val digits = max(2, (lines.lastOrNull() ?: 1).toString().length)
        gutterWidth = (linePaint.measureText("8".repeat(digits)) + dp(24)).toInt()
        setPadding(gutterWidth + contentInset, paddingTop, paddingRight, paddingBottom)
        invalidate()
    }

    fun setEditorPalette(gutter: Int, lineNumber: Int, divider: Int) {
        gutterPaint.color = gutter
        linePaint.color = lineNumber
        dividerPaint.color = divider
        invalidate()
    }

    fun setWordWrap(enabled: Boolean) {
        setSingleLine(false)
        maxLines = Integer.MAX_VALUE
        ellipsize = null
        layoutParams = layoutParams.apply {
            width = if (enabled) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        }
        requestLayout()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, gutterWidth.toFloat(), height.toFloat(), gutterPaint)
        canvas.drawLine(gutterWidth.toFloat(), 0f, gutterWidth.toFloat(), height.toFloat(), dividerPaint)
        val textLayout = layout
        if (textLayout != null && sourceLines.size == sourceOffsets.size) {
            val clip = canvas.clipBounds
            for (index in sourceLines.indices) {
                val offset = sourceOffsets[index].coerceIn(0, text.length)
                val visualLine = textLayout.getLineForOffset(offset)
                val baseline = totalPaddingTop + textLayout.getLineBaseline(visualLine)
                if (baseline >= clip.top - linePaint.textSize && baseline <= clip.bottom + linePaint.textSize) {
                    canvas.drawText(sourceLines[index].toString(), gutterWidth - dpF(10), baseline.toFloat(), linePaint)
                }
            }
        }
        super.onDraw(canvas)
    }

    private fun dp(value: Int) = dpF(value).toInt()
    private fun dpF(value: Int) = value * resources.displayMetrics.density
}
