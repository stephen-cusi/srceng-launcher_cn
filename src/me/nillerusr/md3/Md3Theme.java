package me.nillerusr.md3;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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

/**
 * 手工 MD3 主题工具（零依赖 AndroidX / Material Components）
 *
 * 1) 持久化设置（mod SharedPreferences）：
 *    - theme_mode : 0 = 跟随系统，1 = 强制浅色，2 = 强制深色
 *    - dynamic_color : boolean = 壁纸动态取色（API 27+）
 *    - seed_color : int     = 动态取色不可用或关闭时的主色
 *
 * 2) 颜色系统：seed (ARGB) → HSL 生成主/辅/第三色 + 各 4 级 tonal palette（容器 + onColor） + surface 5 级
 *
 * 3) apply(Activity) 流程：setTheme(light/dark) → 填充 tokens → window 状态栏/导航栏着色 → 对 View 树递归打补丁
 */
public final class Md3Theme {
    private Md3Theme() {}

    public static final String SP_KEY_THEME_MODE     = "md3_theme_mode";
    public static final String SP_KEY_DYNAMIC_COLOR  = "md3_dynamic_color";
    public static final String SP_KEY_SEED_COLOR     = "md3_seed_color";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT  = 1;
    public static final int THEME_DARK   = 2;

    // 4 个默认种子色（启动器设置中用来做色卡）
    public static final int[] SEED_PRESETS = new int[]{
        0xFFF79A10, // HL 橙（默认）
        0xFF7C4DFF, // 紫
        0xFF0088FF, // 蓝
        0xFF00A66B, // 绿
    };

    // =========================================================
    // 持久化
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

    public static boolean isDynamicColorAvailable() {
        return Build.VERSION.SDK_INT >= 27;
    }

    // =========================================================
    // 深浅模式判定
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
    // 种子色获取（动态壁纸色 / 静态种子）
    // =========================================================
    @TargetApi(27)
    private static Integer tryGetWallpaperSeed(Context ctx) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            android.app.WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null) return null;
            int[] c = new int[3];
            // 提取主色 / 次色 / 第三色，取饱和度最高的那个
            int[] colors = new int[] {
                wc.getPrimaryColor()   == null ? 0 : wc.getPrimaryColor().toArgb(),
                wc.getSecondaryColor() == null ? 0 : wc.getSecondaryColor().toArgb(),
                wc.getTertiaryColor()  == null ? 0 : wc.getTertiaryColor().toArgb()
            };
            int best = 0; float bestSat = -1f;
            for (int color : colors) {
                if (color == 0 || Color.alpha(color) < 128) continue;
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                if (hsv[1] > bestSat) { bestSat = hsv[1]; best = color; }
            }
            if (best == 0) return null;
            return best;
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
    // 构建 MD3 tokens（根据 seed + dark 生成完整调色板）
    // =========================================================
    public static Md3Tokens buildTokens(Context ctx) {
        boolean dark = resolveDark(ctx);
        int seed = resolveSeedColor(ctx);
        // 主/辅/第三色：把 seed 色相旋转 0 / +60 / -60（HSL）
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        float primaryHue = hsv[0];
        int primarySeed = Color.HSVToColor(new float[]{primaryHue,          clamp(hsv[1], 0.45f, 0.85f), clamp(hsv[2], 0.40f, 0.85f)});
        int secondarySeed = Color.HSVToColor(new float[]{wrapHue(primaryHue + 55), clamp(hsv[1]*0.72f, 0.25f, 0.70f), clamp(hsv[2]*0.90f+0.04f, 0.45f, 0.85f)});
        int tertiarySeed  = Color.HSVToColor(new float[]{wrapHue(primaryHue - 55), clamp(hsv[1]*0.78f, 0.28f, 0.75f), clamp(hsv[2]*0.92f+0.03f, 0.45f, 0.85f)});
        int errorSeed     = 0xFFBA1A1A;

        Md3Tokens t = new Md3Tokens();
        t.dark = dark;
        applyTonalRole(t.primary,   primarySeed,   dark);
        applyTonalRole(t.secondary, secondarySeed, dark);
        applyTonalRole(t.tertiary,  tertiarySeed,  dark);
        applyTonalRole(t.error,     errorSeed,     dark);

        // Neutral (surface/text)：取主色最小饱和
        int neutralSeed = Color.HSVToColor(new float[]{primaryHue, Math.max(0.04f, hsv[1]*0.12f), dark ? 0.10f : 0.98f});
        applyNeutrals(t, neutralSeed, dark);
        return t;
    }

    private static void applyTonalRole(Md3Tokens.Role r, int seed, boolean dark) {
        if (dark) {
            r.color         = tonal(seed, 80);
            r.onColor       = tonal(seed, 20);
            r.container     = tonal(seed, 30);
            r.onContainer   = tonal(seed, 90);
        } else {
            r.color         = tonal(seed, 40);
            r.onColor       = tonal(seed, 100);
            r.container     = tonal(seed, 90);
            r.onContainer   = tonal(seed, 10);
        }
    }

    private static void applyNeutrals(Md3Tokens t, int neutralSeed, boolean dark) {
        if (dark) {
            // Dark surfaces
            t.surfaceDim             = tonal(neutralSeed, 6);
            t.surface                = tonal(neutralSeed, 6);
            t.surfaceBright          = tonal(neutralSeed, 24);
            t.surfaceContainerLowest = tonal(neutralSeed, 4);
            t.surfaceContainerLow    = tonal(neutralSeed, 10);
            t.surfaceContainer       = tonal(neutralSeed, 12);
            t.surfaceContainerHigh   = tonal(neutralSeed, 17);
            t.surfaceContainerHighest= tonal(neutralSeed, 22);
            t.onSurface              = tonal(neutralSeed, 90);
            t.onSurfaceVariant       = tonal(neutralSeed, 80);
            t.outline                = tonal(neutralSeed, 60);
            t.outlineVariant         = tonal(neutralSeed, 30);
            t.inverseSurface         = tonal(neutralSeed, 90);
            t.inverseOnSurface       = tonal(neutralSeed, 20);
            t.statusBar              = t.surface;
            t.navBar                 = t.surfaceContainer;
        } else {
            t.surfaceDim             = tonal(neutralSeed, 87);
            t.surface                = tonal(neutralSeed, 98);
            t.surfaceBright          = tonal(neutralSeed, 98);
            t.surfaceContainerLowest = tonal(neutralSeed, 100);
            t.surfaceContainerLow    = tonal(neutralSeed, 96);
            t.surfaceContainer       = tonal(neutralSeed, 94);
            t.surfaceContainerHigh   = tonal(neutralSeed, 92);
            t.surfaceContainerHighest= tonal(neutralSeed, 90);
            t.onSurface              = tonal(neutralSeed, 10);
            t.onSurfaceVariant       = tonal(neutralSeed, 30);
            t.outline                = tonal(neutralSeed, 50);
            t.outlineVariant         = tonal(neutralSeed, 80);
            t.inverseSurface         = tonal(neutralSeed, 20);
            t.inverseOnSurface       = tonal(neutralSeed, 95);
            t.statusBar              = t.surface;
            t.navBar                 = t.surfaceContainerHighest;
        }
        // inversePrimary（和 primary 一致，方便 Snackbar 等）
        t.inversePrimary = dark ? tonal(seedColorFix(neutralSeed), 40) : tonal(seedColorFix(neutralSeed), 80);
    }
    private static int seedColorFix(int n) { return n; }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo; if (v > hi) return hi; return v;
    }
    private static float wrapHue(float h) {
        while (h < 0) h += 360f;
        while (h >= 360f) h -= 360f;
        return h;
    }

    /** 根据色调种子（HSL 近似）和目标 tone (L* 0-100) 生成颜色，alpha=FF */
    private static int tonal(int seed, int tone) {
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        // 保持色相不变；饱和度在 tone≤10 / tone≥90 时下降
        float s = hsv[1];
        if (tone <= 10) s *= (0.25f + 0.75f * tone/10f);
        else if (tone >= 90) s *= (1.0f - 0.92f * (tone - 90)/10f);
        float v;
        if (tone <= 10) v = 0.05f + 0.07f * tone/10f;
        else if (tone <= 50) v = 0.15f + 0.55f * (tone-10)/40f;
        else if (tone <= 90) v = 0.70f + 0.25f * (tone-50)/40f;
        else v = 0.95f + 0.05f * (tone-90)/10f;
        if (v > 1f) v = 1f;
        if (s < 0f) s = 0f; if (s > 1f) s = 1f;
        return 0xFF000000 | (0x00FFFFFF & Color.HSVToColor(new float[]{hsv[0], s, v}));
    }

    // =========================================================
    // 应用到 Activity（入口）
    // =========================================================
    public static void applyBeforeOnCreate(Activity a) {
        // 在 super.onCreate / setContentView 之前：选 light/dark 主题
        boolean dark = resolveDark(a);
        if (dark) a.setTheme(R.style.SrcEng_MD3_Dark);
        else       a.setTheme(R.style.SrcEng_MD3);
        // 强制覆盖窗口模式的 uiMode（theme "跟随系统" 不会根据强制模式自动改）
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
        if (root != null) applyViewTree(root, tokens);
    }

    @TargetApi(21)
    private static void applyWindow(Activity a, Md3Tokens t) {
        Window w = a.getWindow();
        if (w == null) return;
        // 背景（有时候 windowBackground 没覆盖到）
        try { w.setBackgroundDrawable(new ColorDrawable(t.surface)); } catch (Throwable ignore) {}
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                w.setStatusBarColor(t.statusBar);
                w.setNavigationBarColor(t.navBar);
            } catch (Throwable ignore) {}
        }
        // 23+ 浅色状态栏：自动选择深色/浅色图标
        if (Build.VERSION.SDK_INT >= 23) {
            View dec = w.getDecorView();
            if (dec != null) {
                int sys = dec.getSystemUiVisibility();
                boolean lightBars = !t.dark && isLightColor(t.statusBar);
                boolean lightNav  = !t.dark && isLightColor(t.navBar);
                if (lightBars) sys |= 0x00002000; else sys &= ~0x00002000; // SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                if (Build.VERSION.SDK_INT >= 26) {
                    if (lightNav) sys |= 0x08000000; else sys &= ~0x08000000; // LIGHT_NAVIGATION_BAR
                }
                dec.setSystemUiVisibility(sys);
            }
        }
    }

    private static boolean isLightColor(int c) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        // sRGB luminance approximation
        double y = (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
        return y > 140.0;
    }

    // =========================================================
    // View 树遍历 + 着色
    // =========================================================
    private static void applyViewTree(View v, Md3Tokens t) {
        applySingleView(v, t);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)v;
            for (int i = 0; i < vg.getChildCount(); i++) applyViewTree(vg.getChildAt(i), t);
        }
    }

    private static void applySingleView(View v, Md3Tokens t) {
        // 背景
        if (v.getTag(R.id.md3_tag_applied) == null && v.getId() != R.id.md3_preserve_bg) {
            if (v instanceof TextView && !(v instanceof Button || v instanceof EditText || v instanceof CompoundButton)) {
                TextView tv = (TextView) v;
                Object tag = v.getTag(R.id.md3_text_role);
                String role = (tag instanceof String) ? (String) tag : null;
                if ("on_primary_container".equals(role))         tv.setTextColor(t.primary.onContainer);
                else if ("on_secondary_container".equals(role)) tv.setTextColor(t.secondary.onContainer);
                else if ("on_surface_variant".equals(role))     tv.setTextColor(t.onSurfaceVariant);
                else if ("outline".equals(role))                tv.setTextColor(t.outline);
                else if ("on_surface".equals(role))             tv.setTextColor(t.onSurface);
                else if ("primary".equals(role))                tv.setTextColor(t.primary.color);
                else {
                    // 默认按 textAppearance 大致区分：title 大字号 = onSurface，辅助 = onSurfaceVariant
                    float size = tv.getTextSize();
                    if (isSubTitleStyle(v, role)) tv.setTextColor(t.onSurfaceVariant);
                    else tv.setTextColor(t.onSurface);
                }
                // hint
                tv.setHintTextColor(t.outline);
            }

            // 顶部 bar（按 id / tag）
            if (matchesId(v, R.id.md3_app_bar))  setTintedBg(v, t.surfaceContainer, Color.TRANSPARENT, 0);
            if (matchesTag(v, "card"))           setTintedBg(v, t.surfaceContainerHigh, Color.TRANSPARENT, 0);
            if (matchesTag(v, "card_filled"))    setTintedBg(v, t.primary.container, Color.TRANSPARENT, 0);
            if (matchesTag(v, "card_outlined"))  setTintedBg(v, t.surfaceContainerLow, t.outlineVariant, 1);
            if (matchesTag(v, "preview_primary"))setTintedBg(v, t.primary.container, Color.TRANSPARENT, 0);
            if (matchesTag(v, "divider"))        setTintedBg(v, t.outlineVariant, Color.TRANSPARENT, 0);

            if (v instanceof Button) {
                Button b = (Button) v;
                Object tag = v.getTag(R.id.md3_btn_style);
                String style = (tag instanceof String) ? (String) tag : "filled";
                applyButtonStyle(b, style, t);
                b.setMinHeight(dp(b.getContext(), 40));
                int padH = dp(b.getContext(), 24);
                b.setPadding(padH, 0, padH, 0);
                b.setAllCaps(false);
            }

            if (v instanceof EditText) {
                EditText et = (EditText) v;
                et.setTextColor(t.onSurface);
                et.setHintTextColor(t.outline);
                try { setTintedBg(et, t.surfaceContainerHigh, t.outline, 1, true); } catch (Throwable ignore) {}
                // highlight/cursor/handle color = primary
                try { et.setHighlightColor(withAlpha(t.primary.color, 0x44)); } catch (Throwable ignore) {}
                trySetColorFilterField(et, "mCursorDrawable", t.primary.color);
                trySetColorFilterField(et, "mTextSelectHandleLeftRes", t.primary.color);
                trySetColorFilterField(et, "mTextSelectHandleRightRes", t.primary.color);
                trySetColorFilterField(et, "mTextSelectHandleRes", t.primary.color);
                tryEtBackgroundTint(et, t.primary.color, t.outline);
            }

            if (v instanceof CompoundButton && !(v instanceof RadioButton)) {
                CompoundButton cb = (CompoundButton)v;
                try {
                    // Switch / CheckBox track + thumb tint
                    Drawable[] drawables = cb.getCompoundDrawables();
                    if (Build.VERSION.SDK_INT >= 21) {
                        cb.setButtonTintList(tintList(t.primary.color, t.outline));
                        if (cb instanceof Switch) {
                            Switch sw = (Switch) cb;
                            try { sw.setTrackTintList(tintList(withAlpha(t.primary.color, 0x66), t.outlineVariant)); } catch (Throwable ignore) {}
                            try { sw.setThumbTintList(tintList(t.primary.color, t.surfaceContainerHighest)); } catch (Throwable ignore) {}
                        }
                    }
                } catch (Throwable ignore) {}
            }

            if (v instanceof RadioButton) {
                RadioButton rb = (RadioButton) v;
                if (Build.VERSION.SDK_INT >= 21) {
                    try { rb.setButtonTintList(tintList(t.primary.color, t.outline)); } catch (Throwable ignore) {}
                }
                rb.setTextColor(t.onSurface);
            }

            if (v instanceof ImageButton || v instanceof ImageView) {
                Object tag = v.getTag(R.id.md3_btn_style);
                if ("icon".equals(tag)) {
                    if (matchesTag(v, "pressed_bg_on")) v.setBackground(makeRippleBg(t.primary.container, t.surfaceContainerHighest));
                    else v.setBackground(makeRippleBg(t.surfaceContainerHighest, t.surfaceContainerHigh));
                    if (v instanceof ImageView) {
                        ImageView iv = (ImageView)v;
                        if (iv.getTag(R.id.md3_tint) != null || matchesId(v, R.id.md3_icon_button)) {
                            iv.setColorFilter(t.onSurfaceVariant, android.graphics.PorterDuff.Mode.SRC_IN);
                        }
                    }
                }
            }
        }
    }

    private static boolean isSubTitleStyle(View v, String role) {
        if (role != null) return false;
        Object tag = v.getTag(R.id.md3_text_role);
        return "subtitle".equals(tag);
    }

    private static boolean matchesId(View v, int id) {
        try { return v.getId() == id; } catch (Throwable ignore) { return false; }
    }
    private static boolean matchesTag(View v, String tag) {
        try { return tag.equals(v.getTag()); } catch (Throwable ignore) { return false; }
    }

    private static int withAlpha(int color, int a) { return (0x00FFFFFF & color) | ((a & 0xFF) << 24); }

    private static android.content.res.ColorStateList tintList(int checkedColor, int defaultColor) {
        int[][] states = new int[][] {
            new int[] { android.R.attr.state_checked },
            new int[] { android.R.attr.state_enabled, -android.R.attr.state_checked },
            new int[] { -android.R.attr.state_enabled },
            new int[] {}
        };
        int[] colors = new int[] {
            checkedColor,
            defaultColor,
            withAlpha(defaultColor, 128),
            defaultColor
        };
        return new android.content.res.ColorStateList(states, colors);
    }

    private static void applyButtonStyle(Button b, String style, Md3Tokens t) {
        switch (style) {
            case "tonal": {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.RECTANGLE);
                g.setColor(t.secondary.container);
                g.setCornerRadius(dpF(b.getContext(), 20));
                b.setBackground(makeRipple(g, withAlpha(t.secondary.onContainer, 0x33)));
                b.setTextColor(t.secondary.onContainer);
                break;
            }
            case "outlined": {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.RECTANGLE);
                g.setColor(Color.TRANSPARENT);
                g.setStroke(Math.max(1, dp(b.getContext(), 1)), t.outline);
                g.setCornerRadius(dpF(b.getContext(), 20));
                b.setBackground(makeRipple(g, withAlpha(t.primary.color, 0x33)));
                b.setTextColor(t.primary.color);
                break;
            }
            case "text": {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.RECTANGLE);
                g.setColor(Color.TRANSPARENT);
                g.setCornerRadius(dpF(b.getContext(), 20));
                b.setBackground(makeRipple(g, withAlpha(t.primary.color, 0x33)));
                b.setTextColor(t.primary.color);
                break;
            }
            case "filled":
            default: {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.RECTANGLE);
                g.setColor(t.primary.color);
                g.setCornerRadius(dpF(b.getContext(), 20));
                b.setBackground(makeRipple(g, withAlpha(t.primary.onColor, 0x33)));
                b.setTextColor(t.primary.onColor);
                break;
            }
        }
    }

    private static Drawable makeRipple(Drawable content, int maskColor) {
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable mask;
            if (content instanceof GradientDrawable) {
                GradientDrawable src = (GradientDrawable) content;
                GradientDrawable m = new GradientDrawable();
                m.setCornerRadii(getCornerRadii(src));
                m.setColor(0xFFFFFFFF);
                mask = m;
            } else mask = content.getConstantState().newDrawable();
            return new RippleDrawable(new android.content.res.ColorStateList(new int[][]{{}}, new int[]{maskColor & 0x00FFFFFF | 0x33000000}), content, mask);
        } else {
            StateListDrawable sld = new StateListDrawable();
            GradientDrawable pressed = new GradientDrawable();
            pressed.setColor(maskColor);
            if (content instanceof GradientDrawable) {
                GradientDrawable g = (GradientDrawable) content;
                pressed.setCornerRadii(getCornerRadii(g));
            }
            LayerDrawable lp = new LayerDrawable(new Drawable[]{content, pressed});
            sld.addState(new int[]{android.R.attr.state_pressed}, lp);
            sld.addState(new int[]{}, content);
            return sld;
        }
    }

    private static Drawable makeRippleBg(int pressedFill, int normalFill) {
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(normalFill);
        normal.setCornerRadius(99999f);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF); mask.setCornerRadius(99999f);
        if (Build.VERSION.SDK_INT >= 21) {
            return new RippleDrawable(new android.content.res.ColorStateList(new int[][]{{}}, new int[]{0x22000000 | (pressedFill & 0x00FFFFFF)}), normal, mask);
        } else {
            GradientDrawable pr = new GradientDrawable(); pr.setColor(pressedFill); pr.setCornerRadius(99999f);
            StateListDrawable sld = new StateListDrawable();
            sld.addState(new int[]{android.R.attr.state_pressed}, pr);
            sld.addState(new int[]{}, normal);
            return sld;
        }
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

    private static void setTintedBg(View v, int color, int stroke, int strokeDp) {
        setTintedBg(v, color, stroke, strokeDp, false);
    }
    private static void setTintedBg(View v, int color, int stroke, int strokeDp, boolean topRadiusOnly) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        if (stroke > 0) g.setStroke(Math.max(1, dp(v.getContext(), strokeDp)), stroke);
        float r = dpF(v.getContext(), topRadiusOnly ? 12 : 20);
        if (topRadiusOnly) g.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});
        else g.setCornerRadius(r);
        Drawable mask = g;
        if (Build.VERSION.SDK_INT >= 21) {
            int rippleColor = withAlpha(mix(color, 0xFF000000, 0.35f), 0x1F);
            v.setBackground(new RippleDrawable(new android.content.res.ColorStateList(new int[][]{{}}, new int[]{rippleColor}), g, mask));
        } else v.setBackground(g);
    }

    private static int mix(int a, int b, float t) {
        float ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        float br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        return Color.rgb(Math.round(ar + (br-ar)*t), Math.round(ag + (bg-ag)*t), Math.round(ab + (bb-ab)*t));
    }

    private static int dp(Context c, int dp) { return Math.round(dpF(c, dp)); }
    private static float dpF(Context c, int dp) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return dp * dm.density;
    }

    // =========================================================
    // EditText 着色辅助
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
            } else if (o instanceof Integer) {
                // res id
                int id = (Integer) o;
                if (id != 0 && target instanceof TextView) {
                    android.content.res.Resources res = ((TextView)target).getResources();
                    try {
                        Drawable d = res.getDrawable(id).mutate();
                        d.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                    } catch (Throwable ignore) {}
                }
            }
        } catch (Throwable ignore) {}
    }

    private static void tryEtBackgroundTint(EditText et, int primary, int outline) {
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                Drawable bg = et.getBackground();
                if (bg != null) {
                    bg = bg.mutate();
                    int[][] states = new int[][] {
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
