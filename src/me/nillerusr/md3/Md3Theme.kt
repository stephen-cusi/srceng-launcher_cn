package me.nillerusr.md3

import android.annotation.TargetApi
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.*
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.widget.CompoundButtonCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.valvesoftware.source.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class Md3Theme private constructor() {
    companion object {
        const val SP_KEY_THEME_MODE = "md3_theme_mode"
        const val SP_KEY_DYNAMIC_COLOR = "md3_dynamic_color"
        const val SP_KEY_SEED_COLOR = "md3_seed_color"
        const val SP_KEY_UI_LANG = "md3_ui_lang"
        const val SP_KEY_GAME_LANG = "md3_game_lang"
        const val SP_KEY_RES_MODE = "md3_res_mode"
        const val SP_KEY_RES_PRESET_IDX = "md3_res_preset_idx"
        const val SP_KEY_RES_CUSTOM_W = "md3_res_custom_w"
        const val SP_KEY_RES_CUSTOM_H = "md3_res_custom_h"
        const val RES_MODE_DEVICE = "device"
        const val RES_MODE_PRESET = "preset"
        const val RES_MODE_CUSTOM = "custom"
        const val UI_LANG_SYSTEM = "system"
        const val UI_LANG_ZH_CN = "zh-rCN"
        const val UI_LANG_ZH_TW = "zh-rTW"
        const val UI_LANG_EN = "en"
        const val UI_LANG_RU = "ru"
        const val UI_LANG_JA = "ja"
        const val UI_LANG_KO = "ko"
        const val UI_LANG_FR = "fr"
        const val UI_LANG_DE = "de"
        const val UI_LANG_ES = "es"
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        @JvmField val RESOLUTION_PRESETS = arrayOf(
            intArrayOf(1920, 1080), intArrayOf(1280, 720), intArrayOf(2560, 1440),
            intArrayOf(3840, 2160), intArrayOf(1366, 768), intArrayOf(1600, 900),
            intArrayOf(1680, 1050), intArrayOf(1920, 1200), intArrayOf(1280, 800),
            intArrayOf(1024, 768), intArrayOf(1280, 1024), intArrayOf(800, 600)
        )
        @JvmField val UI_LANG_VALUES = arrayOf(
            UI_LANG_SYSTEM, UI_LANG_ZH_CN, UI_LANG_ZH_TW, UI_LANG_EN, UI_LANG_RU,
            UI_LANG_JA, UI_LANG_KO, UI_LANG_FR, UI_LANG_DE, UI_LANG_ES
        )
        @JvmField val GAME_LANG_VALUES = arrayOf(
            "", "schinese", "tchinese", "english", "russian", "german", "french",
            "italian", "spanish", "brazilian", "latam", "japanese", "korean", "polish",
            "dutch", "czech", "danish", "finnish", "greek", "hungarian", "norwegian",
            "portuguese", "romanian", "swedish", "thai", "turkish", "ukrainian", "bulgarian"
        )
        @JvmField val SEED_PRESETS = intArrayOf(
            0xFFF79A10.toInt(), 0xFF7C4DFF.toInt(), 0xFF0088FF.toInt(), 0xFF00A66B.toInt(),
            0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF00ACC1.toInt(), 0xFFFFB300.toInt()
        )

        @JvmStatic fun getPrefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("mod", Context.MODE_PRIVATE)
        @JvmStatic fun getThemeMode(ctx: Context) = getPrefs(ctx).getInt(SP_KEY_THEME_MODE, THEME_SYSTEM)
        @JvmStatic fun setThemeMode(ctx: Context, mode: Int) { getPrefs(ctx).edit().putInt(SP_KEY_THEME_MODE, mode).apply() }
        @JvmStatic fun getDynamicColor(ctx: Context) = Build.VERSION.SDK_INT >= 27 && getPrefs(ctx).getBoolean(SP_KEY_DYNAMIC_COLOR, true)
        @JvmStatic fun setDynamicColor(ctx: Context, value: Boolean) { getPrefs(ctx).edit().putBoolean(SP_KEY_DYNAMIC_COLOR, value).apply() }
        @JvmStatic fun getSeedColor(ctx: Context) = getPrefs(ctx).getInt(SP_KEY_SEED_COLOR, SEED_PRESETS[0])
        @JvmStatic fun setSeedColor(ctx: Context, color: Int) { getPrefs(ctx).edit().putInt(SP_KEY_SEED_COLOR, color).apply() }
        @JvmStatic fun getUiLang(ctx: Context): String = getPrefs(ctx).getString(SP_KEY_UI_LANG, UI_LANG_SYSTEM) ?: UI_LANG_SYSTEM
        @JvmStatic fun setUiLang(ctx: Context, value: String) { getPrefs(ctx).edit().putString(SP_KEY_UI_LANG, value).apply() }
        @JvmStatic fun getGameLang(ctx: Context): String = getPrefs(ctx).getString(SP_KEY_GAME_LANG, "") ?: ""
        @JvmStatic fun setGameLang(ctx: Context, value: String) { getPrefs(ctx).edit().putString(SP_KEY_GAME_LANG, value).apply() }
        @JvmStatic fun getResolutionMode(ctx: Context): String = getPrefs(ctx).getString(SP_KEY_RES_MODE, RES_MODE_DEVICE) ?: RES_MODE_DEVICE
        @JvmStatic fun setResolutionMode(ctx: Context, value: String) { getPrefs(ctx).edit().putString(SP_KEY_RES_MODE, value).apply() }
        @JvmStatic fun getResolutionPresetIdx(ctx: Context) = getPrefs(ctx).getInt(SP_KEY_RES_PRESET_IDX, 0)
        @JvmStatic fun setResolutionPresetIdx(ctx: Context, value: Int) { getPrefs(ctx).edit().putInt(SP_KEY_RES_PRESET_IDX, value).apply() }
        @JvmStatic fun getResolutionCustomW(ctx: Context) = getPrefs(ctx).getInt(SP_KEY_RES_CUSTOM_W, 1280)
        @JvmStatic fun setResolutionCustomW(ctx: Context, value: Int) { getPrefs(ctx).edit().putInt(SP_KEY_RES_CUSTOM_W, value).apply() }
        @JvmStatic fun getResolutionCustomH(ctx: Context) = getPrefs(ctx).getInt(SP_KEY_RES_CUSTOM_H, 720)
        @JvmStatic fun setResolutionCustomH(ctx: Context, value: Int) { getPrefs(ctx).edit().putInt(SP_KEY_RES_CUSTOM_H, value).apply() }

        @JvmStatic fun getResolvedResolution(ctx: Context): IntArray {
            if (getResolutionMode(ctx) == RES_MODE_DEVICE) {
                val size = getDeviceResolution(ctx)
                return intArrayOf(max(size[0], size[1]), min(size[0], size[1]))
            }
            if (getResolutionMode(ctx) == RES_MODE_PRESET) {
                var index = getResolutionPresetIdx(ctx)
                if (index !in RESOLUTION_PRESETS.indices) index = 0
                return RESOLUTION_PRESETS[index].clone()
            }
            return intArrayOf(getResolutionCustomW(ctx).coerceIn(320, 8192), getResolutionCustomH(ctx).coerceIn(240, 8192))
        }

        @JvmStatic fun getDeviceResolution(ctx: Context): IntArray {
            try {
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                if (wm != null) {
                    val display = wm.defaultDisplay
                    val size = android.graphics.Point()
                    display.getRealSize(size)
                    return intArrayOf(size.x, size.y)
                }
            } catch (_: Throwable) {}
            try { return ctx.resources.displayMetrics.let { intArrayOf(it.widthPixels, it.heightPixels) } } catch (_: Throwable) {}
            return intArrayOf(1920, 1080)
        }

        @JvmStatic fun isDynamicColorAvailable() = Build.VERSION.SDK_INT >= 27
        private fun localeForUiLang(value: String?): Locale? = when (value) {
            UI_LANG_ZH_CN -> Locale.SIMPLIFIED_CHINESE
            UI_LANG_ZH_TW -> Locale.TRADITIONAL_CHINESE
            UI_LANG_EN -> Locale.ENGLISH
            UI_LANG_RU -> Locale("ru", "RU")
            UI_LANG_JA -> Locale.JAPAN
            UI_LANG_KO -> Locale.KOREA
            UI_LANG_FR -> Locale.FRANCE
            UI_LANG_DE -> Locale.GERMANY
            UI_LANG_ES -> Locale("es", "ES")
            else -> null
        }

        @JvmStatic fun getRealSystemLocale(): Locale {
            try {
                val cfg = Resources.getSystem().configuration
                if (!cfg.locales.isEmpty) return cfg.locales[0]
            } catch (_: Throwable) {}
            return Locale.getDefault() ?: Locale.ENGLISH
        }

        @JvmStatic fun resolveUiLangLocale(uiLang: String?): Locale = localeForUiLang(uiLang) ?: getRealSystemLocale()
        @JvmStatic fun applyUiLocaleConfiguration(cfg: Configuration?, uiLang: String?) {
            if (cfg == null) return
            try {
                val desired = resolveUiLangLocale(uiLang)
                cfg.setLocale(desired)
            } catch (_: Throwable) {}
        }

        @Suppress("DEPRECATION")
        @JvmStatic fun applyUiLocale(activity: Activity) {
            val target = localeForUiLang(getUiLang(activity))
            val res = activity.resources
            var cfg = res.configuration
            val current = if (cfg.locales.isEmpty) getRealSystemLocale() else cfg.locales[0]
            val desired = target ?: getRealSystemLocale()
            if (desired == current) return
            cfg = Configuration(cfg)
            cfg.setLocale(desired)
            res.updateConfiguration(cfg, res.displayMetrics)
        }

        private fun getStrTag(v: View): String? {
            try { (v.getTag(R.id.md3_btn_style) as? String)?.let { return it } } catch (_: Throwable) {}
            try { (v.getTag(R.id.md3_text_role) as? String)?.let { return it } } catch (_: Throwable) {}
            return try { v.tag as? String } catch (_: Throwable) { null }
        }
        private fun hasStrTag(v: View, exact: String?): Boolean {
            if (exact == null) return false
            try { if (exact == v.tag) return true } catch (_: Throwable) {}
            for (id in intArrayOf(R.id.md3_btn_style, R.id.md3_text_role)) try { if (exact == v.getTag(id)) return true } catch (_: Throwable) {}
            return false
        }

        @JvmStatic fun resolveDark(ctx: Context): Boolean = when (getThemeMode(ctx)) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> {
                val ui = try { Resources.getSystem().configuration.uiMode } catch (_: Throwable) { ctx.resources.configuration.uiMode }
                ui and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
        }

        @TargetApi(27)
        private fun tryGetWallpaperSeed(ctx: Context): Int? {
            try {
                val colors = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
                val candidates = intArrayOf(colors.primaryColor?.toArgb() ?: 0, colors.secondaryColor?.toArgb() ?: 0, colors.tertiaryColor?.toArgb() ?: 0)
                var best = 0
                var bestSat = -1f
                for (color in candidates) {
                    if (color == 0 || Color.alpha(color) < 128) continue
                    val hsv = FloatArray(3)
                    Color.colorToHSV(color, hsv)
                    if (hsv[1] < .10f || hsv[2] < .15f || hsv[2] > .97f) continue
                    if (hsv[1] > bestSat) { bestSat = hsv[1]; best = color }
                }
                return best.takeIf { it != 0 }
            } catch (_: Throwable) { return null }
        }

        @JvmStatic fun resolveSeedColor(ctx: Context): Int = if (getDynamicColor(ctx)) tryGetWallpaperSeed(ctx) ?: getSeedColor(ctx) else getSeedColor(ctx)
        @JvmStatic fun buildTokens(ctx: Context): Md3Tokens {
            val dark = resolveDark(ctx)
            val hsv = FloatArray(3)
            Color.colorToHSV(resolveSeedColor(ctx), hsv)
            val hue = hsv[0]
            val sat = clamp(hsv[1], .55f, .82f)
            val value = clamp(hsv[2], .55f, .78f)
            val t = Md3Tokens()
            t.dark = dark
            fillRole(t.primary, Color.HSVToColor(floatArrayOf(hue, sat, value)), dark)
            fillRole(t.secondary, Color.HSVToColor(floatArrayOf(wrapHue(hue + 45), sat * .62f, value)), dark)
            fillRole(t.tertiary, Color.HSVToColor(floatArrayOf(wrapHue(hue - 55), sat * .75f, value)), dark)
            fillRole(t.error, 0xFFBA1A1A.toInt(), dark)
            applyStableSurfaces(t, hue, max(.05f, sat * .12f), dark)
            return t
        }

        private fun fillRole(role: Md3Tokens.Role, seed: Int, dark: Boolean) {
            if (dark) {
                role.color = tone(seed, 80, 40); role.onColor = tone(seed, 20, 20)
                role.container = tone(seed, 30, 50); role.onContainer = tone(seed, 90, 10)
            } else {
                role.color = tone(seed, 40, 60); role.onColor = tone(seed, 100, 0)
                role.container = tone(seed, 90, 8); role.onContainer = tone(seed, 10, 40)
            }
            ensureContrast(role)
        }
        private fun ensureContrast(role: Md3Tokens.Role) {
            if (luminance(role.color) > .6f) role.onColor = blacken(role.onColor)
            if (luminance(role.color) < .18f) role.onColor = whiten(role.onColor)
            if (luminance(role.container) > .7f) role.onContainer = blacken(role.onContainer)
            if (luminance(role.container) < .22f) role.onContainer = whiten(role.onContainer)
        }
        private fun applyStableSurfaces(t: Md3Tokens, hue: Float, sat: Float, dark: Boolean) {
            if (dark) {
                t.surfaceContainerLowest=n(hue,sat,.04f); t.surfaceDim=n(hue,sat,.05f); t.surface=n(hue,sat,.06f)
                t.surfaceContainerLow=n(hue,sat,.09f); t.surfaceContainer=n(hue,sat,.12f); t.surfaceContainerHigh=n(hue,sat,.16f)
                t.surfaceContainerHighest=n(hue,sat,.22f); t.surfaceBright=n(hue,sat,.26f)
                t.onSurface=ensureAgainst(t.surface,0xFFF2EFE8.toInt(),0xFF0D0C0A.toInt(),true)
                t.onSurfaceVariant=ensureAgainst(t.surfaceContainerHigh,0xFFC8BDB1.toInt(),0xFF4E443A.toInt(),true)
                t.outline=ensureAgainst(t.surfaceContainerHigh,0xFF988B7E.toInt(),0xFF685D53.toInt(),true)
                t.outlineVariant=ensureAgainst(t.surfaceContainerLow,0xFF4A4036.toInt(),0xFFD8C8BB.toInt(),true)
                t.inverseSurface=0xFFEDE6DF.toInt(); t.inverseOnSurface=0xFF201C18.toInt(); t.inversePrimary=t.primary.color
                t.statusBar=t.surface; t.navBar=t.surfaceContainer
            } else {
                t.surfaceContainerLowest=Color.WHITE; t.surfaceBright=n(hue,sat,.995f); t.surface=n(hue,sat,.985f)
                t.surfaceContainerLow=n(hue,sat,.965f); t.surfaceContainer=n(hue,sat,.945f); t.surfaceContainerHigh=n(hue,sat,.915f)
                t.surfaceContainerHighest=n(hue,sat,.885f); t.surfaceDim=n(hue,sat,.855f)
                t.onSurface=ensureAgainst(t.surface,0xFF1C1B17.toInt(),0xFFF2EFE8.toInt(),false)
                t.onSurfaceVariant=ensureAgainst(t.surfaceContainerHigh,0xFF4A443B.toInt(),0xFFC8BDB1.toInt(),false)
                t.outline=ensureAgainst(t.surfaceContainerHigh,0xFF796C60.toInt(),0xFF958B7E.toInt(),false)
                t.outlineVariant=ensureAgainst(t.surfaceContainerLow,0xFFCAC0B4.toInt(),0xFF4C4037.toInt(),false)
                t.inverseSurface=0xFF322C27.toInt(); t.inverseOnSurface=0xFFF7F1EA.toInt(); t.inversePrimary=tone(t.primary.color,80,0)
                t.statusBar=t.surface; t.navBar=t.surfaceContainerHighest
            }
        }
        private fun ensureAgainst(bg:Int, light:Int, dark:Int, darkSide:Boolean):Int { if(darkSide)return light; val l=luminance(bg); return if(l>.75f)light else if(l<.30f)dark else light }
        private fun n(h:Float,s:Float,v:Float)=0xFF000000.toInt() or (0xFFFFFF and Color.HSVToColor(floatArrayOf(wrapHue(h),clamp(s,.03f,.4f),clamp(v,0f,1f))))
        private fun tone(seed:Int,target:Int,boost:Int):Int {
            val hsv=FloatArray(3); Color.colorToHSV(seed,hsv); var s=hsv[1]; var v=hsv[2]
            if(target>=90){v=.93f+.07f*(target-90)/10f;s=max(.03f,s*(.25f+.5f*(target-90)/10f)+boost/255f)}
            else if(target>=80){v=.82f+.12f*(target-80)/10f;s=max(.05f,s*(.55f+.45f*(target-80)/10f))}
            else if(target>=50){v=.55f+.30f*(target-50)/30f;s=s*(.85f+.15f*(target-50)/30f)+boost/255f}
            else if(target>=30)v=.30f+.25f*(target-30)/20f
            else if(target>=10){v=.10f+.22f*(target-10)/20f;s*=.80f+.20f*(target-10)/20f}
            else {v=.04f+.07f*target/10f;s=min(s,.35f)}
            return 0xFF000000.toInt() or (0xFFFFFF and Color.HSVToColor(floatArrayOf(wrapHue(hsv[0]),clamp(s,.02f,.96f),clamp(v,.03f,.995f))))
        }
        private fun clamp(v:Float,lo:Float,hi:Float)=if(v<lo)lo else min(v,hi)
        private fun wrapHue(value:Float):Float { var h=value; while(h<0)h+=360f; while(h>=360)h-=360f; return h }
        private fun luminance(c:Int)=(.2126f*Color.red(c)+.7152f*Color.green(c)+.0722f*Color.blue(c))/255f
        private fun blacken(c:Int)=Color.argb(Color.alpha(c),max(0,Color.red(c)-160),max(0,Color.green(c)-160),max(0,Color.blue(c)-160))
        private fun whiten(c:Int)=Color.argb(Color.alpha(c),min(255,Color.red(c)+160),min(255,Color.green(c)+160),min(255,Color.blue(c)+160))

        @JvmStatic fun applyBeforeOnCreate(activity: Activity) {
            applyUiLocale(activity)
            val dark=resolveDark(activity)
            activity.setTheme(if(dark) R.style.SrcEng_MD3_Dark else R.style.SrcEng_MD3)
            var cfg=activity.resources.configuration
            val wanted=if(dark)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            if(cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK != wanted){cfg=Configuration(cfg);cfg.uiMode=cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or wanted;activity.resources.updateConfiguration(cfg,activity.resources.displayMetrics)}
        }
        @JvmStatic fun applyAfterSetContentView(activity: Activity) {
            val t=buildTokens(activity); applyWindow(activity,t); val root=activity.findViewById<View>(android.R.id.content)
            try { root?.setBackgroundDrawable(ColorDrawable(t.surface)) } catch (_:Throwable) {}
            if(root!=null)applyViewTree(root,t)
        }
        @TargetApi(21) private fun applyWindow(a:Activity,t:Md3Tokens) {
            val w=a.window?:return; try{w.setBackgroundDrawable(ColorDrawable(t.surface))}catch(_:Throwable){}
            try{w.statusBarColor=t.statusBar;w.navigationBarColor=t.navBar;w.decorView?.let{it.systemUiVisibility=it.systemUiVisibility and 0x400.inv() and 0x100.inv()}}catch(_:Throwable){}
            val d=w.decorView?:return;var sys=d.systemUiVisibility;val light=!t.dark&&luminance(t.statusBar)>.70f;sys=if(light)sys or 0x2000 else sys and 0x2000.inv();if(Build.VERSION.SDK_INT>=26){val nav=!t.dark&&luminance(t.navBar)>.70f;sys=if(nav)sys or 0x08000000 else sys and 0x08000000.inv()};d.systemUiVisibility=sys
        }
        private fun applyViewTree(v:View,t:Md3Tokens){applySingleView(v,t);if(v is ViewGroup)for(i in 0 until v.childCount)applyViewTree(v.getChildAt(i),t)}
        private fun applySingleView(v:View,t:Md3Tokens) {
            if(v.getTag(R.id.md3_tag_applied)==null){try{v.setTag(R.id.md3_original_padding,intArrayOf(v.paddingLeft,v.paddingTop,v.paddingRight,v.paddingBottom))}catch(_:Throwable){};try{v.setTag(R.id.md3_tag_applied,true)}catch(_:Throwable){}}
            val op=try{v.getTag(R.id.md3_original_padding) as? IntArray}catch(_:Throwable){null}
            val l=op?.get(0)?:v.paddingLeft;val top=op?.get(1)?:v.paddingTop;val r=op?.get(2)?:v.paddingRight;val bottom=op?.get(3)?:v.paddingBottom
            if(v.id==R.id.md3_preserve_bg)return
            if(v.id==R.id.md3_app_bar)setBg(v,t.surface,0,Color.TRANSPARENT,0,0,0)
            if(v is MaterialCardView){val outlined=hasStrTag(v,"card_outlined");v.setCardBackgroundColor(if(hasStrTag(v,"preview_primary"))t.primary.container else if(outlined)t.surfaceContainerLow else t.surfaceContainerHigh);v.strokeColor=if(outlined)t.outlineVariant else Color.TRANSPARENT;v.strokeWidth=if(outlined)dp(v.context,1) else 0;v.radius=dpF(v.context,20)}
            else {if(hasStrTag(v,"card"))setBg(v,t.surfaceContainerHigh,20,Color.TRANSPARENT,0,0,0);if(hasStrTag(v,"card_outlined"))setBg(v,t.surfaceContainerLow,20,t.outlineVariant,1,0,0);if(hasStrTag(v,"card_filled")||hasStrTag(v,"preview_primary"))setBg(v,t.primary.container,20,Color.TRANSPARENT,0,0,0)}
            if(hasStrTag(v,"divider"))setBg(v,t.outlineVariant,0,Color.TRANSPARENT,0,0,0)
            if(v is TextView&&v !is Button&&v !is EditText&&v !is CompoundButton){v.setTextColor(when(getStrTag(v)){"on_primary_container"->t.primary.onContainer;"on_secondary_container"->t.secondary.onContainer;"on_surface_variant","subtitle"->t.onSurfaceVariant;"outline"->t.outline;"primary"->t.primary.color;else->t.onSurface});v.setHintTextColor(t.outline)}
            if(v is MaterialButton)applyMaterialButtonStyle(v,getStrTag(v)?:"filled",t)
            else if(v is Button&&v !is CompoundButton){applyButtonStyle(v,getStrTag(v)?:"filled",t);v.minHeight=dp(v.context,40);val h=dp(v.context,24);val pv=dp(v.context,10);v.setPadding(l+h,top+pv,r+h,bottom+pv);v.isAllCaps=false}
            if(v is TextInputLayout){v.boxBackgroundColor=t.surfaceContainerHigh;v.boxStrokeColor=t.primary.color;v.hintTextColor=ColorStateList.valueOf(t.primary.color);v.defaultHintTextColor=ColorStateList.valueOf(t.onSurfaceVariant)}
            if(v is EditText){val fill=t.surfaceContainerHigh;v.setTextColor(ensureContrastColor(fill,t.onSurface,0xFF1C1B17.toInt(),0xFFF5F1EC.toInt()));v.setHintTextColor(t.outline);if(v !is TextInputEditText)setEditTextBg(v,fill,t.outline);try{v.highlightColor=withAlpha(t.primary.color,0x33)}catch(_:Throwable){};trySetColorFilterField(v,"mCursorDrawable",t.primary.color);trySetColorFilterField(v,"mTextSelectHandleLeftRes",t.primary.color);trySetColorFilterField(v,"mTextSelectHandleRightRes",t.primary.color);trySetColorFilterField(v,"mTextSelectHandleRes",t.primary.color);if(v !is TextInputEditText)tryEtBackgroundTint(v,t.primary.color,t.outline)}
            if(v is CompoundButton){CompoundButtonCompat.setButtonTintList(v,tintList(t.primary.color,t.outline));if(v is SwitchMaterial){v.trackTintList=tintList(withAlpha(t.primary.color,0x66),t.outlineVariant);v.thumbTintList=tintList(t.primary.color,t.surfaceContainerHighest)};v.setTextColor(t.onSurface)}
            if((v is ImageButton||v is ImageView)&&hasStrTag(v,"icon")){try{v.setBackgroundDrawable(makeRippleBg(t.surfaceContainerHighest,t.surfaceContainerHigh))}catch(_:Throwable){};if(v is ImageView){v.setColorFilter(t.onSurfaceVariant,PorterDuff.Mode.SRC_IN);v.scaleType=ImageView.ScaleType.CENTER_INSIDE;val p=dp(v.context,8);v.setPadding(l+p,top+p,r+p,bottom+p)}}
        }
        private fun applyMaterialButtonStyle(b:MaterialButton,style:String,t:Md3Tokens){val fill:Int;val text:Int;val outlined:Boolean;when(style){"tonal"->{fill=t.secondary.container;text=t.secondary.onContainer;outlined=false};"outlined"->{fill=Color.TRANSPARENT;text=t.primary.color;outlined=true};"text"->{fill=Color.TRANSPARENT;text=t.primary.color;outlined=false};else->{fill=t.primary.color;text=ensureContrastColor(fill,t.primary.onColor,0xFF1C1B17.toInt(),Color.WHITE);outlined=false}};b.backgroundTintList=ColorStateList.valueOf(fill);b.setTextColor(text);b.rippleColor=ColorStateList.valueOf(withAlpha(text,0x1F));b.cornerRadius=dp(b.context,if(style=="launch")28 else 20);b.strokeColor=ColorStateList.valueOf(if(outlined)t.outline else Color.TRANSPARENT);b.strokeWidth=if(outlined)dp(b.context,1) else 0;b.isAllCaps=false}
        private fun ensureContrastColor(bg:Int,preferred:Int,dark:Int,light:Int)=if(abs(luminance(bg)-luminance(preferred))<.40f)if(luminance(bg)>.55f)dark else light else preferred
        private fun setBg(v:View,fill:Int,radius:Int,stroke:Int,strokeDp:Int,px:Int,py:Int){try{val shape=MaterialShapeDrawable(ShapeAppearanceModel.builder().setAllCornerSizes(dpF(v.context,radius)).build());shape.fillColor=ColorStateList.valueOf(fill);if(strokeDp>0)shape.setStroke(dpF(v.context,strokeDp),stroke);if(px>0||py>0)v.setPadding(dp(v.context,px),dp(v.context,py),dp(v.context,px),dp(v.context,py));v.background=shape}catch(_:Throwable){}}
        private fun setEditTextBg(v:EditText,fill:Int,stroke:Int){try{val g=GradientDrawable();g.shape=GradientDrawable.RECTANGLE;g.setColor(fill);val r=dpF(v.context,12);g.cornerRadii=floatArrayOf(r,r,r,r,0f,0f,0f,0f);v.setBackgroundDrawable(withRipple(g,fill,12))}catch(_:Throwable){}}
        private fun applyButtonStyle(b:Button,style:String,t:Md3Tokens){val fill:Int;val text:Int;val radius:Int;val outlined:Boolean
            when(style){"launch"->{fill=t.primary.color;text=ensureContrastColor(fill,t.primary.onColor,0xFF1C1B17.toInt(),Color.WHITE);radius=28;outlined=false};"tonal"->{fill=t.secondary.container;text=t.secondary.onContainer;radius=20;outlined=false};"outlined"->{fill=Color.TRANSPARENT;text=t.primary.color;radius=20;outlined=true};"text"->{fill=Color.TRANSPARENT;text=t.primary.color;radius=20;outlined=false};else->{fill=t.primary.color;text=ensureContrastColor(fill,t.primary.onColor,0xFF1C1B17.toInt(),Color.WHITE);radius=20;outlined=false}}
            val g=GradientDrawable();g.shape=GradientDrawable.RECTANGLE;g.setColor(fill);g.cornerRadius=dpF(if(style=="launch")b.context else null,radius);if(outlined)g.setStroke(max(1,dp(b.context,1)),t.outline);b.setBackgroundDrawable(withRipple(g,text,radius));b.setTextColor(text)
        }
        private fun withRipple(content:GradientDrawable,tone:Int,radius:Int):Drawable{val ripple=withAlpha(tone,0x1f);if(Build.VERSION.SDK_INT>=21)try{val mask=GradientDrawable();mask.shape=GradientDrawable.RECTANGLE;mask.cornerRadii=getCornerRadii(content);mask.setColor(Color.WHITE);return RippleDrawable(ColorStateList(arrayOf(intArrayOf()),intArrayOf(ripple)),content,mask)}catch(_:Throwable){};val pressed=GradientDrawable();pressed.cornerRadii=getCornerRadii(content);pressed.setColor(blend(contentColorOr(content),tone,.22f));return StateListDrawable().apply{addState(intArrayOf(android.R.attr.state_pressed),LayerDrawable(arrayOf(content,pressed)));addState(intArrayOf(),content)}}
        private fun makeRippleBg(normalFill:Int,pressedFill:Int):Drawable{val normal=GradientDrawable();normal.shape=GradientDrawable.OVAL;normal.setColor(normalFill);if(Build.VERSION.SDK_INT>=21)try{val mask=GradientDrawable();mask.shape=GradientDrawable.OVAL;mask.setColor(Color.WHITE);return RippleDrawable(ColorStateList(arrayOf(intArrayOf()),intArrayOf(withAlpha(pressedFill,0x33))),normal,mask)}catch(_:Throwable){};val pressed=GradientDrawable();pressed.shape=GradientDrawable.OVAL;pressed.setColor(pressedFill);return StateListDrawable().apply{addState(intArrayOf(android.R.attr.state_pressed),pressed);addState(intArrayOf(),normal)}}
        private fun contentColorOr(g:GradientDrawable):Int=try{val m=GradientDrawable::class.java.getDeclaredMethod("getColor");m.isAccessible=true;(m.invoke(g) as? ColorStateList)?.defaultColor?:0xFFCCCCCC.toInt()}catch(_:Throwable){0xFFCCCCCC.toInt()}
        private fun blend(a:Int,b:Int,t:Float)=Color.rgb((Color.red(a)+(Color.red(b)-Color.red(a))*t).roundToInt(),(Color.green(a)+(Color.green(b)-Color.green(a))*t).roundToInt(),(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t).roundToInt())
        private fun getCornerRadii(g:GradientDrawable):FloatArray{try{val m=GradientDrawable::class.java.getDeclaredMethod("getCornerRadii");m.isAccessible=true;(m.invoke(g) as? FloatArray)?.let{return it}}catch(_:Throwable){};try{val m=GradientDrawable::class.java.getDeclaredMethod("getCornerRadius");m.isAccessible=true;(m.invoke(g) as? Float)?.let{radius->return FloatArray(8){radius}}}catch(_:Throwable){};return FloatArray(8){20f}}
        private fun withAlpha(color:Int,a:Int)=color and 0xFFFFFF or ((a and 0xFF) shl 24)
        private fun tintList(checked:Int,default:Int)=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked,android.R.attr.state_enabled),intArrayOf(android.R.attr.state_enabled,-android.R.attr.state_checked),intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(checked,default,withAlpha(default,128),default))
        private fun dp(c:Context,value:Int)=dpF(c,value).roundToInt()
        private fun dpF(c:Context?,value:Int)=value*(c?.resources?.displayMetrics?.density?:2f)
        private fun trySetColorFilterField(target:Any,name:String,color:Int){try{val f=TextView::class.java.getDeclaredField(name);f.isAccessible=true;when(val o=f.get(target)){is Drawable->o.mutate().setColorFilter(color,PorterDuff.Mode.SRC_IN);is Array<*>->o.filterIsInstance<Drawable>().forEach{it.mutate().setColorFilter(color,PorterDuff.Mode.SRC_IN)}}}catch(_:Throwable){}}
        private fun tryEtBackgroundTint(v:EditText,primary:Int,outline:Int){try{if(Build.VERSION.SDK_INT>=21){val bg=v.background?.mutate()?:return;bg.setTintList(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused),intArrayOf()),intArrayOf(primary,outline)));v.setBackgroundDrawable(bg)}}catch(_:Throwable){}}
    }
}
