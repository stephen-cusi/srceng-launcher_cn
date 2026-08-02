package me.nillerusr.md3;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.valvesoftware.source.R;

import me.nillerusr.md3.Md3Tokens;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Hand-crafted MD3 theme utilities (zero dependency on Material Components / AndroidX).
 *
 * Tagging convention: supports both android:tag="..." (legacy string) AND R.id.md3_*_tag id-tag.
 */
public final class Md3Theme {
    private Md3Theme() {}

    public static final String SP_KEY_THEME_MODE     = "md3_theme_mode";
    public static final String SP_KEY_DYNAMIC_COLOR  = "md3_dynamic_color";
    public static final String SP_KEY_SEED_COLOR     = "md3_seed_color";
    public static final String SP_KEY_UI_LANG        = "md3_ui_lang";         // "system" | "zh-rCN" | "zh-rTW" | "en"
    public static final String SP_KEY_GAME_LANG      = "md3_game_lang";       // ""(=不追加) | schinese | tchinese | english | russian | german | french | italian | spanish | brazilian | latam | japanese | korean | polish | dutch | czech | danish | finnish | greek | hungarian | norwegian | portuguese | romanian | swedish | thai | turkish | ukrainian | bulgarian

    // ========== Resolution (Screen) ==========
    // resolution mode: "device" (= use device native, don't add -w/-h), "preset" (= use RESOLUTION_PRESETS[idx]), "custom" (= custom_w/custom_h)
    public static final String SP_KEY_RES_MODE       = "md3_res_mode";
    public static final String SP_KEY_RES_PRESET_IDX = "md3_res_preset_idx";
    public static final String SP_KEY_RES_CUSTOM_W   = "md3_res_custom_w";
    public static final String SP_KEY_RES_CUSTOM_H   = "md3_res_custom_h";
    public static final String SP_KEY_RES_FULLSCREEN = "md3_res_fullscreen";  // boolean: true=-full, false=-windowed (default true)

    public static final String RES_MODE_DEVICE = "device";
    public static final String RES_MODE_PRESET = "preset";
    public static final String RES_MODE_CUSTOM = "custom";

    // 常用预设分辨率 (宽x高) — 16:9, 16:10, 4:3 常见游戏分辨率
    public static final int[][] RESOLUTION_PRESETS = new int[][]{
        { 1920, 1080 }, // FHD 1080p 16:9
        { 1280, 720  }, // HD 720p 16:9
        { 2560, 1440 }, // QHD 1440p 16:9
        { 3840, 2160 }, // UHD 4K 16:9
        { 1366, 768  }, // HD+ 16:9 (laptop)
        { 1600, 900  }, // HD+ 16:9
        { 1680, 1050 }, // WSXGA+ 16:10
        { 1920, 1200 }, // WUXGA 16:10
        { 1280, 800  }, // WXGA 16:10
        { 1024, 768  }, // XGA 4:3
        { 1280, 1024 }, // SXGA 4:3
        { 800,  600  }, // SVGA 4:3
    };

    public static final String UI_LANG_SYSTEM = "system";
    public static final String UI_LANG_ZH_CN  = "zh-rCN";
    public static final String UI_LANG_ZH_TW  = "zh-rTW";
    public static final String UI_LANG_EN     = "en";
    public static final String UI_LANG_RU     = "ru";
    public static final String UI_LANG_JA     = "ja";
    public static final String UI_LANG_KO     = "ko";
    public static final String UI_LANG_FR     = "fr";
    public static final String UI_LANG_DE     = "de";
    public static final String UI_LANG_ES     = "es";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT  = 1;
    public static final int THEME_DARK   = 2;

    // 启动器UI语言映射列表：(持久化值 → 显示名称用string资源 → Locale对象)
    // 顺序：跟随系统 → 简中 → 繁中 → 英文 → 俄语 → 日语 → 韩语 → 法语 → 德语 → 西语
    public static final String[] UI_LANG_VALUES = new String[]{
        UI_LANG_SYSTEM,
        UI_LANG_ZH_CN, UI_LANG_ZH_TW, UI_LANG_EN,
        UI_LANG_RU, UI_LANG_JA, UI_LANG_KO,
        UI_LANG_FR, UI_LANG_DE, UI_LANG_ES
    };

    // Source引擎常用游戏语言代码（参考HL2/Portal的gameui_*.txt文件名），按展示频率排序；空串=不追加
    public static final String[] GAME_LANG_VALUES = new String[]{
        "", "schinese", "tchinese", "english", "russian", "german", "french",
        "italian", "spanish", "brazilian", "latam", "japanese", "korean",
        "polish", "dutch", "czech", "danish", "finnish", "greek", "hungarian",
        "norwegian", "portuguese", "romanian", "swedish", "thai", "turkish",
        "ukrainian", "bulgarian"
    };

    public static final int[] SEED_PRESETS = new int[]{
        0xFFF79A10, // HL orange (default)
        0xFF7C4DFF, // purple
        0xFF0088FF, // blue
        0xFF00A66B, // green
    };

    // =========================================================
    // Persistence
    // =========================================================
    public static SharedPreferences getPrefs(Context ctx) {
        return ctx.getSharedPreferences("mod", Context.MODE_PRIVATE);
    }

    public static int getThemeMode(Context ctx) {
        return getPrefs(ctx).getInt(SP_KEY_THEME_MODE, THEME_SYSTEM);
    }
    public static void setThemeMode(Context ctx, int mode) {
        getPrefs(ctx).edit().putInt(SP_KEY_THEME_MODE, mode).apply();
    }

    public static boolean getDynamicColor(Context ctx) {
        if (Build.VERSION.SDK_INT < 27) return false;
        return getPrefs(ctx).getBoolean(SP_KEY_DYNAMIC_COLOR, true);
    }
    public static void setDynamicColor(Context ctx, boolean v) {
        getPrefs(ctx).edit().putBoolean(SP_KEY_DYNAMIC_COLOR, v).apply();
    }

    public static int getSeedColor(Context ctx) {
        return getPrefs(ctx).getInt(SP_KEY_SEED_COLOR, SEED_PRESETS[0]);
    }
    public static void setSeedColor(Context ctx, int color) {
        getPrefs(ctx).edit().putInt(SP_KEY_SEED_COLOR, color).apply();
    }

    public static String getUiLang(Context ctx) {
        return getPrefs(ctx).getString(SP_KEY_UI_LANG, UI_LANG_SYSTEM);
    }
    public static void setUiLang(Context ctx, String v) {
        getPrefs(ctx).edit().putString(SP_KEY_UI_LANG, v).apply();
    }

    public static String getGameLang(Context ctx) {
        return getPrefs(ctx).getString(SP_KEY_GAME_LANG, "");
    }
    public static void setGameLang(Context ctx, String v) {
        getPrefs(ctx).edit().putString(SP_KEY_GAME_LANG, v).apply();
    }

    // ========== Resolution getters/setters ==========
    public static String getResolutionMode(Context ctx) {
        return getPrefs(ctx).getString(SP_KEY_RES_MODE, RES_MODE_DEVICE);
    }
    public static void setResolutionMode(Context ctx, String v) {
        getPrefs(ctx).edit().putString(SP_KEY_RES_MODE, v).apply();
    }

    public static int getResolutionPresetIdx(Context ctx) {
        return getPrefs(ctx).getInt(SP_KEY_RES_PRESET_IDX, 0);
    }
    public static void setResolutionPresetIdx(Context ctx, int v) {
        getPrefs(ctx).edit().putInt(SP_KEY_RES_PRESET_IDX, v).apply();
    }

    public static int getResolutionCustomW(Context ctx) {
        return getPrefs(ctx).getInt(SP_KEY_RES_CUSTOM_W, 1280);
    }
    public static void setResolutionCustomW(Context ctx, int v) {
        getPrefs(ctx).edit().putInt(SP_KEY_RES_CUSTOM_W, v).apply();
    }

    public static int getResolutionCustomH(Context ctx) {
        return getPrefs(ctx).getInt(SP_KEY_RES_CUSTOM_H, 720);
    }
    public static void setResolutionCustomH(Context ctx, int v) {
        getPrefs(ctx).edit().putInt(SP_KEY_RES_CUSTOM_H, v).apply();
    }

    public static boolean getResolutionFullscreen(Context ctx) {
        return getPrefs(ctx).getBoolean(SP_KEY_RES_FULLSCREEN, true);
    }
    public static void setResolutionFullscreen(Context ctx, boolean v) {
        getPrefs(ctx).edit().putBoolean(SP_KEY_RES_FULLSCREEN, v).apply();
    }

    /**
     * 计算最终生效的分辨率宽高。
     *   - DEVICE模式：返回{0,0}（表示不追加-w/-h，让引擎用设备分辨率）
     *   - PRESET模式：返回RESOLUTION_PRESETS[idx]
     *   - CUSTOM模式：返回{customW, customH}
     */
    public static int[] getResolvedResolution(Context ctx) {
        String mode = getResolutionMode(ctx);
        if (RES_MODE_DEVICE.equals(mode)) {
            return new int[]{ 0, 0 };
        }
        if (RES_MODE_PRESET.equals(mode)) {
            int idx = getResolutionPresetIdx(ctx);
            if (idx < 0 || idx >= RESOLUTION_PRESETS.length) idx = 0;
            return new int[]{ RESOLUTION_PRESETS[idx][0], RESOLUTION_PRESETS[idx][1] };
        }
        // CUSTOM
        return new int[]{
            Math.max(320, getResolutionCustomW(ctx)),
            Math.max(240, getResolutionCustomH(ctx))
        };
    }

    /** 获取设备本机分辨率（用于"使用本机分辨率"选项显示和DEVICE模式的参考） */
    public static int[] getDeviceResolution(Context ctx) {
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            if (wm != null) {
                android.view.Display display = wm.getDefaultDisplay();
                if (Build.VERSION.SDK_INT >= 17) {
                    android.graphics.Point size = new android.graphics.Point();
                    display.getRealSize(size);
                    return new int[]{ size.x, size.y };
                } else {
                    display.getMetrics(dm);
                    return new int[]{ dm.widthPixels, dm.heightPixels };
                }
            }
        } catch (Throwable ignore) {}
        try {
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            return new int[]{ dm.widthPixels, dm.heightPixels };
        } catch (Throwable ignore) {}
        return new int[]{ 1920, 1080 };
    }

    public static boolean isDynamicColorAvailable() { return Build.VERSION.SDK_INT >= 27; }

    // =========================================================
    // UI Locale helpers — supports:
    //   system follow / zh-CN / zh-TW / en / ru / ja / ko / fr / de / es
    // =========================================================
    private static Locale localeForUiLang(String uiLang) {
        if (UI_LANG_ZH_CN.equals(uiLang)) return Locale.SIMPLIFIED_CHINESE;
        if (UI_LANG_ZH_TW.equals(uiLang)) return Locale.TRADITIONAL_CHINESE;
        if (UI_LANG_EN.equals(uiLang))    return Locale.ENGLISH;
        if (UI_LANG_RU.equals(uiLang))    return new Locale("ru", "RU");
        if (UI_LANG_JA.equals(uiLang))    return Locale.JAPAN;
        if (UI_LANG_KO.equals(uiLang))    return Locale.KOREA;
        if (UI_LANG_FR.equals(uiLang))    return Locale.FRANCE;
        if (UI_LANG_DE.equals(uiLang))    return Locale.GERMANY;
        if (UI_LANG_ES.equals(uiLang))    return new Locale("es", "ES");
        return null; // follow system → return real system locale below
    }

    // 返回系统**真正**的Locale（Resources.getSystem().getConfiguration()不受我们手动改Locale.setDefault的影响）
    public static Locale getRealSystemLocale() {
        try {
            Configuration sysCfg = Resources.getSystem().getConfiguration();
            if (Build.VERSION.SDK_INT >= 24) {
                if (!sysCfg.getLocales().isEmpty()) return sysCfg.getLocales().get(0);
            } else {
                if (sysCfg.locale != null) return sysCfg.locale;
            }
        } catch (Throwable ignore) {}
        // 保底：虽然Locale.getDefault可能被我们污染，但总比null好；最后fallback = ENGLISH
        Locale fallback = Locale.getDefault();
        if (fallback == null) fallback = Locale.ENGLISH;
        return fallback;
    }

    // 根据uiLang计算目标Locale(暴露给外面,用于attachBaseContext/createConfigurationContext)
    public static Locale resolveUiLangLocale(String uiLang) {
        Locale target = localeForUiLang(uiLang);
        return (target == null) ? getRealSystemLocale() : target;
    }

    // 只更新传入的Configuration的Locale设置(不直接作用于任何Activity)——供attachBaseContext内部使用
    public static void applyUiLocaleConfiguration(Configuration cfg, String uiLang) {
        if (cfg == null) return;
        try {
            Locale desired = resolveUiLangLocale(uiLang);
            if (Build.VERSION.SDK_INT >= 17) {
                cfg.setLocale(desired);
            } else {
                cfg.locale = desired;
            }
            Locale.setDefault(desired);
        } catch (Throwable ignore) {}
    }

    @SuppressWarnings("deprecation")
    public static void applyUiLocale(Activity a) {
        String uiLang = getUiLang(a);
        Locale target = localeForUiLang(uiLang);
        Resources res = a.getResources();
        Configuration cfg = res.getConfiguration();
        Locale cur;
        if (Build.VERSION.SDK_INT >= 24) {
            cur = cfg.getLocales().isEmpty() ? getRealSystemLocale() : cfg.getLocales().get(0);
        } else {
            cur = (cfg.locale != null) ? cfg.locale : getRealSystemLocale();
        }
        // 关键：跟随系统时用 getRealSystemLocale()，不要用 Locale.getDefault()
        //     —因为手动选英文时我们调过 Locale.setDefault(ENGLISH)，
        //      这时 Locale.getDefault 已经是污染值 ENGLISH，不再是系统真实Locale了
        Locale desired = (target == null) ? getRealSystemLocale() : target;
        if (desired.equals(cur)) {
            // 即使Locale没变,也同步一下Locale.setDefault保证DateFormat等一致
            Locale.setDefault(desired);
            return;
        }

        cfg = new Configuration(cfg);
        if (Build.VERSION.SDK_INT >= 17) {
            cfg.setLocale(desired);
        } else {
            cfg.locale = desired;
        }
        DisplayMetrics dm = res.getDisplayMetrics();
        res.updateConfiguration(cfg, dm);

        // Also set default Locale so DateFormat etc. aligns
        Locale.setDefault(desired);
    }

    // =========================================================
    // Tag helpers (supports both android:tag string and R.id.* tags)
    // =========================================================
    private static String getStrTag(View v) {
        try {
            Object o = v.getTag(R.id.md3_btn_style);
            if (o instanceof String) return (String) o;
        } catch (Throwable ignore) {}
        try {
            Object o = v.getTag(R.id.md3_text_role);
            if (o instanceof String) return (String) o;
        } catch (Throwable ignore) {}
        try {
            Object o = v.getTag();
            if (o instanceof String) return (String) o;
        } catch (Throwable ignore) {}
        return null;
    }
    private static boolean hasStrTag(View v, String exact) {
        if (exact == null) return false;
        try {
            Object o = v.getTag();
            if (exact.equals(o)) return true;
        } catch (Throwable ignore) {}
        // also check R.id.* tag slots
        int[] ids = new int[]{ R.id.md3_btn_style, R.id.md3_text_role };
        for (int id : ids) {
            try { Object o = v.getTag(id); if (exact.equals(o)) return true; } catch (Throwable ignore) {}
        }
        return false;
    }

    // =========================================================
    // Dark mode resolution
    // =========================================================
    public static boolean resolveDark(Context ctx) {
        int mode = getThemeMode(ctx);
        switch (mode) {
            case THEME_LIGHT: return false;
            case THEME_DARK:  return true;
            default:
            case THEME_SYSTEM:
                int ui = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return ui == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    // =========================================================
    // Seed color resolution (dynamic wallpaper / static seed)
    // =========================================================
    @TargetApi(27)
    private static Integer tryGetWallpaperSeed(Context ctx) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            android.app.WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null) return null;
            int[] colors = new int[] {
                wc.getPrimaryColor()   == null ? 0 : wc.getPrimaryColor().toArgb(),
                wc.getSecondaryColor() == null ? 0 : wc.getSecondaryColor().toArgb(),
                wc.getTertiaryColor()  == null ? 0 : wc.getTertiaryColor().toArgb()
            };
            int best = 0; float bestSat = -1f;
            for (int c : colors) {
                if (c == 0 || Color.alpha(c) < 128) continue;
                float[] hsv = new float[3];
                Color.colorToHSV(c, hsv);
                // ignore too-bright or too-dark pastels where hue influence is weak
                if (hsv[1] < 0.10f || hsv[2] < 0.15f || hsv[2] > 0.97f) continue;
                if (hsv[1] > bestSat) { bestSat = hsv[1]; best = c; }
            }
            return best == 0 ? null : best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int resolveSeedColor(Context ctx) {
        if (getDynamicColor(ctx)) {
            Integer wp = tryGetWallpaperSeed(ctx);
            if (wp != null) return wp;
        }
        return getSeedColor(ctx);
    }

    // =========================================================
    // Build MD3 color tokens from seed + dark flag
    // =========================================================
    public static Md3Tokens buildTokens(Context ctx) {
        boolean dark = resolveDark(ctx);
        int seed = resolveSeedColor(ctx);

        // derive primary/secondary/tertiary hues from seed (HSL-ish approximations)
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        float pH = hsv[0];
        // clamp to reasonable saturation/value to guarantee readable tones
        float seedSat = clamp(hsv[1], 0.55f, 0.82f);
        float seedVal = clamp(hsv[2], 0.55f, 0.78f);

        int primarySeed   = Color.HSVToColor(new float[]{ pH,                           seedSat,        seedVal });
        int secondarySeed = Color.HSVToColor(new float[]{ wrapHue(pH + 45),             seedSat*0.62f,  seedVal });
        int tertiarySeed  = Color.HSVToColor(new float[]{ wrapHue(pH - 55),             seedSat*0.75f,  seedVal });
        int errorSeed     = 0xFFBA1A1A;

        Md3Tokens t = new Md3Tokens();
        t.dark = dark;

        fillRole(t.primary,   primarySeed,   dark);
        fillRole(t.secondary, secondarySeed, dark);
        fillRole(t.tertiary,  tertiarySeed,  dark);
        fillRole(t.error,     errorSeed,     dark);

        // Neutral palette (surface family) - STABLE anchor so surface never clashes with text
        float neutralHue = pH;
        float neutralSat = Math.max(0.05f, seedSat * 0.12f); // keep it very close to grayscale, with warm/cool hint
        applyStableSurfaces(t, neutralHue, neutralSat, dark);

        return t;
    }

    private static void fillRole(Md3Tokens.Role r, int seed, boolean dark) {
        if (dark) {
            r.color         = tone(seed, 80, 40);
            r.onColor       = tone(seed, 20, 20);
            r.container     = tone(seed, 30, 50);
            r.onContainer   = tone(seed, 90, 10);
        } else {
            r.color         = tone(seed, 40, 60);
            r.onColor       = tone(seed, 100, 0);
            r.container     = tone(seed, 90, 8);
            r.onContainer   = tone(seed, 10, 40);
        }
        // Guarantee contrast for primary/on-primary (minimum ~ AA for 4.5:1)
        if (!dark) ensureContrast(r, true);
        else       ensureContrast(r, false);
    }

    private static void ensureContrast(Md3Tokens.Role r, boolean onLightBg) {
        // color-onColor contrast: if luminance too close, push onColor to white/black
        if (luminance(r.color) > 0.6f) r.onColor = blacken(r.onColor, 200);
        if (luminance(r.color) < 0.18f) r.onColor = whiten(r.onColor, 235);
        if (luminance(r.container) > 0.7f) r.onContainer = blacken(r.onContainer, 200);
        if (luminance(r.container) < 0.22f) r.onContainer = whiten(r.onContainer, 235);
    }

    private static void applyStableSurfaces(Md3Tokens t, float neutralHue, float sat, boolean dark) {
        // Build a proper well-ordered surface stack from lightest→highest (tone)
        if (dark) {
            // dark: ascending tone means ascending lightness
            t.surfaceContainerLowest = n(neutralHue, sat, 0.04f);
            t.surfaceDim             = n(neutralHue, sat, 0.05f);
            t.surface                = n(neutralHue, sat, 0.06f);
            t.surfaceContainerLow    = n(neutralHue, sat, 0.09f);
            t.surfaceContainer       = n(neutralHue, sat, 0.12f);
            t.surfaceContainerHigh   = n(neutralHue, sat, 0.16f);
            t.surfaceContainerHighest= n(neutralHue, sat, 0.22f);
            t.surfaceBright          = n(neutralHue, sat, 0.26f);
            t.onSurface              = ensureAgainst(t.surface,      0xFFF2EFE8, 0xFF0D0C0A, true);
            t.onSurfaceVariant       = ensureAgainst(t.surfaceContainerHigh, 0xFFC8BDB1, 0xFF4E443A, true);
            t.outline                = ensureAgainst(t.surfaceContainerHigh, 0xFF988B7E, 0xFF685D53, true);
            t.outlineVariant         = ensureAgainst(t.surfaceContainerLow,  0xFF4A4036, 0xFFD8C8BB, true);
            t.inverseSurface         = 0xFFEDE6DF;
            t.inverseOnSurface       = 0xFF201C18;
            t.inversePrimary         = t.primary.color;
            t.statusBar = t.surface;
            t.navBar    = t.surfaceContainer;
        } else {
            t.surfaceContainerLowest = 0xFFFFFFFF;
            t.surfaceBright          = n(neutralHue, sat, 0.995f);
            t.surface                = n(neutralHue, sat, 0.985f);
            t.surfaceContainerLow    = n(neutralHue, sat, 0.965f);
            t.surfaceContainer       = n(neutralHue, sat, 0.945f);
            t.surfaceContainerHigh   = n(neutralHue, sat, 0.915f);
            t.surfaceContainerHighest= n(neutralHue, sat, 0.885f);
            t.surfaceDim             = n(neutralHue, sat, 0.855f);
            t.onSurface              = ensureAgainst(t.surface,                 0xFF1C1B17, 0xFFF2EFE8, false);
            t.onSurfaceVariant       = ensureAgainst(t.surfaceContainerHigh,   0xFF4A443B, 0xFFC8BDB1, false);
            t.outline                = ensureAgainst(t.surfaceContainerHigh,   0xFF796C60, 0xFF958B7E, false);
            t.outlineVariant         = ensureAgainst(t.surfaceContainerLow,    0xFFCAC0B4, 0xFF4C4037, false);
            t.inverseSurface         = 0xFF322C27;
            t.inverseOnSurface       = 0xFFF7F1EA;
            t.inversePrimary         = tone(t.primary.color, 80, 0);
            t.statusBar = t.surface;
            t.navBar    = t.surfaceContainerHighest;
        }
    }

    /** Pick `ifLight` / `ifDark` based on luminance of bg */
    private static int ensureAgainst(int bg, int ifLight, int ifDark, boolean bgIsDarkSide) {
        float lum = luminance(bg);
        if (bgIsDarkSide) return ifLight;
        if (lum > 0.75f) return ifLight;
        if (lum < 0.30f) return ifDark;
        return ifLight;
    }

    private static int n(float hue, float sat, float v) {
        return 0xFF000000 | (0x00FFFFFF & Color.HSVToColor(new float[]{ wrapHue(hue), clamp(sat, 0.03f, 0.4f), clamp(v, 0f, 1f) }));
    }

    // Generate a tone (lightness / L*) approximation. `satBoost` adds saturation for higher tones.
    // toneTarget is 0..100 but interpreted as value 0..1 and then adjusted by tone semantics.
    private static int tone(int seed, int toneTarget, int satBoost) {
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        float s = hsv[1];
        float v = hsv[2];
        // Higher tone → higher value, lower sat; lower tone → lower value
        if (toneTarget >= 90) { v = 0.93f + 0.07f * (toneTarget - 90)/10f; s = Math.max(0.03f, s * (0.25f + 0.5f * (toneTarget-90)/10f) + satBoost/255f); }
        else if (toneTarget >= 80) { v = 0.82f + 0.12f * (toneTarget - 80)/10f; s = Math.max(0.05f, s * (0.55f + 0.45f * (toneTarget-80)/10f)); }
        else if (toneTarget >= 50) { v = 0.55f + 0.30f * (toneTarget - 50)/30f;     s = s * (0.85f + 0.15f * (toneTarget-50)/30f) + satBoost/255f; }
        else if (toneTarget >= 30) { v = 0.30f + 0.25f * (toneTarget - 30)/20f;     s = s; }
        else if (toneTarget >= 10) { v = 0.10f + 0.22f * (toneTarget - 10)/20f;     s = s * (0.80f + 0.20f * (toneTarget-10)/20f); }
        else                       { v = 0.04f + 0.07f * toneTarget/10f;            s = Math.min(s, 0.35f); }
        s = clamp(s, 0.02f, 0.96f);
        v = clamp(v, 0.03f, 0.995f);
        return 0xFF000000 | (0x00FFFFFF & Color.HSVToColor(new float[]{ wrapHue(hsv[0]), s, v }));
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (Math.min(v, hi)); }
    private static float wrapHue(float h) { while (h < 0) h += 360f; while (h >= 360f) h -= 360f; return h; }

    private static float luminance(int c) {
        // sRGB luminance (0..1)
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        return (0.2126f*r + 0.7152f*g + 0.0722f*b) / 255f;
    }
    private static int blacken(int c, int minVal) { // push down value >= minVal darkness (ie towards black, but minVal means cap)
        int a = Color.alpha(c);
        int r = Math.max(0, Color.red(c) - 160);
        int g = Math.max(0, Color.green(c) - 160);
        int b = Math.max(0, Color.blue(c) - 160);
        return Color.argb(a, r, g, b);
    }
    private static int whiten(int c, int target) {
        int a = Color.alpha(c);
        int r = Math.min(255, Color.red(c)   + 160);
        int g = Math.min(255, Color.green(c) + 160);
        int b = Math.min(255, Color.blue(c)  + 160);
        return Color.argb(a, r, g, b);
    }

    // =========================================================
    // Apply to Activity (entry points)
    // =========================================================
    public static void applyBeforeOnCreate(Activity a) {
        // 1) FIRST: Apply UI locale override — affects which values-* resources get loaded
        //    (must be before setTheme, otherwise the wrong strings will be cached)
        applyUiLocale(a);

        boolean dark = resolveDark(a);
        // Theme selection: always go through SrcEng.MD3 variants; these are our base.
        if (dark) a.setTheme(R.style.SrcEng_MD3_Dark);
        else       a.setTheme(R.style.SrcEng_MD3);
        // Make sure Resources.Configuration.uiMode aligns with forced mode (affects values-night)
        Configuration cfg = a.getResources().getConfiguration();
        int wanted = dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        if ((cfg.uiMode & Configuration.UI_MODE_NIGHT_MASK) != wanted) {
            cfg = new Configuration(cfg);
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | wanted;
            a.getResources().updateConfiguration(cfg, a.getResources().getDisplayMetrics());
        }
    }

    public static void applyAfterSetContentView(Activity a) {
        Md3Tokens tokens = buildTokens(a);
        applyWindow(a, tokens);
        View root = a.findViewById(android.R.id.content);
        // Force root background to be tokens.surface (in case theme.windowBackground used xml colors instead of tokens)
        try { if (root != null) root.setBackgroundDrawable(new ColorDrawable(tokens.surface)); } catch (Throwable ignore) {}
        if (root != null) applyViewTree(root, tokens);
    }

    @TargetApi(21)
    private static void applyWindow(Activity a, Md3Tokens t) {
        Window w = a.getWindow();
        if (w == null) return;
        try { w.setBackgroundDrawable(new ColorDrawable(t.surface)); } catch (Throwable ignore) {}
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                w.setStatusBarColor(t.statusBar);
                w.setNavigationBarColor(t.navBar);
            } catch (Throwable ignore) {}
        }
        if (Build.VERSION.SDK_INT >= 23) {
            View dec = w.getDecorView();
            if (dec != null) {
                int sys = dec.getSystemUiVisibility();
                boolean lightBars = !t.dark && luminance(t.statusBar) > 0.70f;
                boolean lightNav  = !t.dark && luminance(t.navBar) > 0.70f;
                if (lightBars) sys |= 0x00002000; else sys &= ~0x00002000;
                if (Build.VERSION.SDK_INT >= 26) {
                    if (lightNav) sys |= 0x08000000; else sys &= ~0x08000000;
                }
                dec.setSystemUiVisibility(sys);
            }
        }
    }

    // =========================================================
    // View tree traversal + styling
    // =========================================================
    private static void applyViewTree(View v, Md3Tokens t) {
        applySingleView(v, t);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) applyViewTree(vg.getChildAt(i), t);
        }
    }

    private static void applySingleView(View v, Md3Tokens t) {
        // Never apply twice
        try { if (v.getTag(R.id.md3_tag_applied) != null) return; } catch (Throwable ignore) {}
        try { v.setTag(R.id.md3_tag_applied, Boolean.TRUE); } catch (Throwable ignore) {}
        if (v.getId() == R.id.md3_preserve_bg) return;

        // AppBar (flat, no rounded corners, edge-to-edge)
        if (v.getId() == R.id.md3_app_bar) {
            setBg(v, t.surface, 0, Color.TRANSPARENT, 0, 0, 0);
        }

        // Background by tag (card variants / divider / preview)
        if (hasStrTag(v, "card"))            setBg(v, t.surfaceContainerHigh,     20, Color.TRANSPARENT, 0, 0, 0);
        if (hasStrTag(v, "card_outlined"))   setBg(v, t.surfaceContainerLow,      20, t.outlineVariant,       1, 0, 0);
        if (hasStrTag(v, "card_filled"))     setBg(v, t.primary.container,       20, Color.TRANSPARENT, 0, 0, 0);
        if (hasStrTag(v, "preview_primary")) setBg(v, t.primary.container,       20, Color.TRANSPARENT, 0, 0, 0);
        if (hasStrTag(v, "divider"))         setBg(v, t.outlineVariant,           0, Color.TRANSPARENT, 0, 0, 0);

        // TextViews (but NOT Button/EditText/CompoundButton which get their own treatment)
        if (v instanceof TextView && !(v instanceof Button) && !(v instanceof EditText) && !(v instanceof CompoundButton)) {
            TextView tv = (TextView) v;
            String role = getStrTag(v);
            if      ("on_primary_container".equals(role))   tv.setTextColor(t.primary.onContainer);
            else if ("on_secondary_container".equals(role)) tv.setTextColor(t.secondary.onContainer);
            else if ("on_surface_variant".equals(role))     tv.setTextColor(t.onSurfaceVariant);
            else if ("outline".equals(role))                tv.setTextColor(t.outline);
            else if ("on_surface".equals(role))             tv.setTextColor(t.onSurface);
            else if ("primary".equals(role))                tv.setTextColor(t.primary.color);
            else if ("subtitle".equals(role))               tv.setTextColor(t.onSurfaceVariant);
            else {
                tv.setTextColor(t.onSurface);
            }
            tv.setHintTextColor(t.outline);
        }

        // Buttons — ONLY when NOT a CompoundButton (Radio/Checkbox/Switch keep their look + tinted button)
        if (v instanceof Button && !(v instanceof CompoundButton)) {
            Button b = (Button) v;
            String style = getStrTag(v);
            if (style == null) style = "filled";
            applyButtonStyle(b, style, t);
            b.setMinHeight(dp(b.getContext(), 40));
            int padH = dp(b.getContext(), 24);
            int padV = dp(b.getContext(), 10);
            b.setPadding(padH, padV, padH, padV);
            b.setAllCaps(false);
        }

        // EditText — Filled tonal style with readable contrast
        if (v instanceof EditText) {
            EditText et = (EditText) v;
            int fill   = t.surfaceContainerHigh;
            int stroke = t.outline;
            int text   = ensureContrastColor(fill, t.onSurface, 0xFF1C1B17, 0xFFF5F1EC);
            int hint   = t.outline;
            et.setTextColor(text);
            et.setHintTextColor(hint);
            setEditTextBg(et, fill, stroke);
            try { et.setHighlightColor(withAlpha(t.primary.color, 0x33)); } catch (Throwable ignore) {}
            trySetColorFilterField(et, "mCursorDrawable", t.primary.color);
            trySetColorFilterField(et, "mTextSelectHandleLeftRes", t.primary.color);
            trySetColorFilterField(et, "mTextSelectHandleRightRes", t.primary.color);
            trySetColorFilterField(et, "mTextSelectHandleRes", t.primary.color);
            tryEtBackgroundTint(et, t.primary.color, t.outline);
            // Give it a visible padding too
            int pad = dp(et.getContext(), 14);
            et.setPadding(pad, pad, pad, pad);
        }

        // CompoundButton (Switch/CheckBox): tint track/thumb + text color
        if (v instanceof CompoundButton) {
            CompoundButton cb = (CompoundButton) v;
            if (cb instanceof RadioButton) {
                RadioButton rb = (RadioButton) cb;
                if (Build.VERSION.SDK_INT >= 21) try { rb.setButtonTintList(tintList(t.primary.color, t.outline)); } catch (Throwable ignore) {}
                rb.setTextColor(t.onSurface);
                int pad = dp(rb.getContext(), 4);
                rb.setPadding(rb.getPaddingLeft() + pad, rb.getPaddingTop(), rb.getPaddingRight() + pad, rb.getPaddingBottom());
            } else {
                try {
                    if (Build.VERSION.SDK_INT >= 21) {
                        cb.setButtonTintList(tintList(t.primary.color, t.outline));
                        if (cb instanceof Switch) {
                            Switch sw = (Switch) cb;
                            try { sw.setTrackTintList(tintList(withAlpha(t.primary.color, 0x66), t.outlineVariant)); } catch (Throwable ignore) {}
                            try { sw.setThumbTintList(tintList(t.primary.color, t.surfaceContainerHighest)); } catch (Throwable ignore) {}
                        }
                    }
                } catch (Throwable ignore) {}
                if (cb instanceof TextView) ((TextView) cb).setTextColor(t.onSurface);
            }
        }

        // ImageButton / ImageView — icon style
        if (v instanceof ImageButton || v instanceof ImageView) {
            boolean icon = hasStrTag(v, "icon");
            if (icon) {
                Drawable bg = makeRippleBg(t.surfaceContainerHighest, t.surfaceContainerHigh);
                try { v.setBackgroundDrawable(bg); } catch (Throwable ignore) {}
                if (v instanceof ImageView) {
                    ImageView iv = (ImageView) v;
                    iv.setColorFilter(t.onSurfaceVariant, android.graphics.PorterDuff.Mode.SRC_IN);
                    iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    int pad = dp(iv.getContext(), 8);
                    iv.setPadding(pad, pad, pad, pad);
                }
            }
        }
    }

    private static int ensureContrastColor(int bg, int preferred, int fallbackDark, int fallbackLight) {
        // pick fallbackDark or fallbackLight based on bg luminance, and make sure preferred is not too close
        float lBg = luminance(bg);
        int text = preferred;
        // Too close? swap to explicit fallback
        if (Math.abs(lBg - luminance(text)) < 0.40f) {
            text = (lBg > 0.55f) ? fallbackDark : fallbackLight;
        }
        return text;
    }

    // =========================================================
    // Background + button styling primitives
    // =========================================================
    private static void setBg(View v, int fill, int radiusDp, int strokeColor, int strokeDp, int padDpX, int padDpY) {
        try {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setColor(fill);
            if (radiusDp > 0) {
                float r = dpF(v.getContext(), radiusDp);
                g.setCornerRadius(r);
            }
            if (strokeDp > 0) {
                g.setStroke(Math.max(1, dp(v.getContext(), strokeDp)), strokeColor);
            }
            Drawable d = withRipple(g, strokeColor, radiusDp);
            if (padDpX > 0 || padDpY > 0) {
                v.setPadding(dp(v.getContext(), padDpX), dp(v.getContext(), padDpY), dp(v.getContext(), padDpX), dp(v.getContext(), padDpY));
            }
            v.setBackgroundDrawable(d);
        } catch (Throwable ignore) {}
    }

    private static void setEditTextBg(EditText et, int fill, int stroke) {
        try {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setColor(fill);
            float r = dpF(et.getContext(), 12);
            g.setCornerRadii(new float[]{r,r,r,r,0,0,0,0}); // top rounded like MD3 filled text field
            // stroke will be drawn by backgroundTint (focused=primary) via tryEtBackgroundTint
            vCompatBackground(et, withRipple(g, fill, 12));
        } catch (Throwable ignore) {}
    }

    private static void vCompatBackground(View v, Drawable d) {
        try { v.setBackgroundDrawable(d); } catch (Throwable ignore) {}
    }

    private static void applyButtonStyle(Button b, String style, Md3Tokens t) {
        switch (style) {
            case "tonal": {
                GradientDrawable g = baseRect(t.secondary.container, 20);
                Drawable bg = withRipple(g, t.secondary.onContainer, 20);
                vCompatBackground(b, bg);
                b.setTextColor(t.secondary.onContainer);
                break;
            }
            case "outlined": {
                GradientDrawable g = baseRect(Color.TRANSPARENT, 20);
                g.setStroke(Math.max(1, dp(b.getContext(), 1)), t.outline);
                Drawable bg = withRipple(g, t.primary.color, 20);
                vCompatBackground(b, bg);
                b.setTextColor(t.primary.color);
                break;
            }
            case "text": {
                GradientDrawable g = baseRect(Color.TRANSPARENT, 20);
                Drawable bg = withRipple(g, t.primary.color, 20);
                vCompatBackground(b, bg);
                b.setTextColor(t.primary.color);
                break;
            }
            case "filled":
            default: {
                GradientDrawable g = baseRect(t.primary.color, 20);
                int onPrimary = ensureContrastColor(t.primary.color, t.primary.onColor, 0xFF1C1B17, 0xFFFFFFFF);
                Drawable bg = withRipple(g, onPrimary, 20);
                vCompatBackground(b, bg);
                b.setTextColor(onPrimary);
                break;
            }
        }
    }

    private static GradientDrawable baseRect(int fill, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(dpF(null, radiusDp));
        return g;
    }

    private static Drawable withRipple(GradientDrawable content, int rippleTone, int radiusDp) {
        // rippleTone is used for pressed/ripple color; we derive alpha version
        int rippleColor = withAlpha(rippleTone, 0x1F);
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                GradientDrawable mask = new GradientDrawable();
                mask.setShape(GradientDrawable.RECTANGLE);
                float[] cr = getCornerRadii(content);
                mask.setCornerRadii(cr);
                mask.setColor(0xFFFFFFFF);
                return new RippleDrawable(new android.content.res.ColorStateList(new int[][]{{}}, new int[]{ rippleColor }), content, mask);
            } catch (Throwable ignore) { /* fall through */ }
        }
        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setCornerRadii(getCornerRadii(content));
        pressed.setColor(blend(contentColorOr(content), rippleTone, 0.22f));
        LayerDrawable lp = new LayerDrawable(new Drawable[]{ content, pressed });
        sld.addState(new int[]{ android.R.attr.state_pressed }, lp);
        sld.addState(new int[]{}, content);
        return sld;
    }

    private static Drawable makeRippleBg(int normalFill, int pressedFill) {
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.OVAL);
        normal.setColor(normalFill);
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                GradientDrawable mask = new GradientDrawable();
                mask.setShape(GradientDrawable.OVAL);
                mask.setColor(0xFFFFFFFF);
                return new RippleDrawable(new android.content.res.ColorStateList(new int[][]{{}}, new int[]{ withAlpha(pressedFill, 0x33) }), normal, mask);
            } catch (Throwable ignore) {}
        }
        GradientDrawable pr = new GradientDrawable(); pr.setShape(GradientDrawable.OVAL); pr.setColor(pressedFill);
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{ android.R.attr.state_pressed }, pr);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    private static int contentColorOr(GradientDrawable g) {
        try {
            Method m = GradientDrawable.class.getDeclaredMethod("getColor");
            m.setAccessible(true);
            Object cso = m.invoke(g);
            if (cso instanceof android.content.res.ColorStateList) {
                return ((android.content.res.ColorStateList) cso).getDefaultColor();
            }
        } catch (Throwable ignore) {}
        return 0xFFCCCCCC;
    }

    private static int blend(int a, int b, float tB) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(b);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        // Correct blue extraction (was typo above)
        ab = Color.blue(a);
        bb = Color.blue(b);
        int r = Math.round(ar + (br - ar) * tB);
        int g = Math.round(ag + (bg - ag) * tB);
        int bl = Math.round(ab + (bb - ab) * tB);
        return Color.rgb(r, g, bl);
    }

    private static float[] getCornerRadii(GradientDrawable g) {
        try {
            Method m = GradientDrawable.class.getDeclaredMethod("getCornerRadii");
            m.setAccessible(true);
            float[] r = (float[]) m.invoke(g);
            if (r != null) return r;
        } catch (Throwable ignore) {}
        try {
            Method m = GradientDrawable.class.getDeclaredMethod("getCornerRadius");
            m.setAccessible(true);
            Float r = (Float) m.invoke(g);
            if (r != null) { float v = r; return new float[]{v,v,v,v,v,v,v,v}; }
        } catch (Throwable ignore) {}
        return new float[]{20f,20f,20f,20f,20f,20f,20f,20f};
    }

    private static int withAlpha(int color, int a) { return (0x00FFFFFF & color) | ((a & 0xFF) << 24); }

    private static android.content.res.ColorStateList tintList(int checked, int defaultC) {
        int[][] states = new int[][]{
            new int[]{ android.R.attr.state_checked,  android.R.attr.state_enabled },
            new int[]{ android.R.attr.state_enabled, -android.R.attr.state_checked },
            new int[]{ -android.R.attr.state_enabled },
            new int[]{}
        };
        int[] colors = new int[]{
            checked,
            defaultC,
            withAlpha(defaultC, 128),
            defaultC
        };
        return new android.content.res.ColorStateList(states, colors);
    }

    private static int dp(Context c, int dp) { return Math.round(dpF(c, dp)); }
    private static float dpF(Context c, int dp) {
        DisplayMetrics dm;
        if (c != null) dm = c.getResources().getDisplayMetrics();
        else dm = ResourcesHolder.DM;
        return dp * dm.density;
    }
    // Fallback density store (when context unavailable during drawable building that got passed null)
    static final class ResourcesHolder {
        static final DisplayMetrics DM;
        static {
            DisplayMetrics m = new DisplayMetrics();
            // Best effort default 2.0x
            m.density = 2.0f; m.widthPixels = 1080; m.heightPixels = 2280;
            DM = m;
        }
    }

    // =========================================================
    // EditText helpers
    // =========================================================
    private static void trySetColorFilterField(Object target, String fieldName, int color) {
        try {
            java.lang.reflect.Field f = TextView.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object o = f.get(target);
            if (o instanceof Drawable) {
                ((Drawable)o).mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            } else if (o instanceof Drawable[]) {
                for (Drawable d : (Drawable[]) o) if (d != null) d.mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } catch (Throwable ignore) {}
    }

    private static void tryEtBackgroundTint(EditText et, int primary, int outline) {
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                Drawable bg = et.getBackground();
                if (bg != null) {
                    bg = bg.mutate();
                    int[][] states = new int[][]{
                        new int[]{ android.R.attr.state_focused },
                        new int[]{}
                    };
                    int[] colors = new int[]{ primary, outline };
                    bg.setTintList(new android.content.res.ColorStateList(states, colors));
                    et.setBackgroundDrawable(bg);
                }
            }
        } catch (Throwable ignore) {}
    }
}
