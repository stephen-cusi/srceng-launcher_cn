package me.nillerusr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.valvesoftware.source.R;

import me.nillerusr.md3.Md3Theme;
import me.nillerusr.md3.Md3Tokens;

public class SettingsActivity extends Activity {
    private static final String TAG = "SettingsActivity";

    // 三档刷新策略
    //   REFRESH_TOKEN_REDRAW: 仅重建Md3Tokens+重绘View(用于seed_color变化),无需重启
    //   REFRESH_RECREATE:     recreate() SettingsActivity自己(深色/动态取色),主界面Launcher在onResume自动感知刷新
    //   REFRESH_FULL_RESTART: CLEAR_TASK全栈重启LauncherActivity(仅用于语言变化,必须刷新所有inflation缓存)
    private static final int REFRESH_TOKEN_REDRAW   = 0;
    private static final int REFRESH_RECREATE       = 1;
    private static final int REFRESH_FULL_RESTART   = 2;

    // Appearance
    private RadioGroup darkGroup;
    private RadioButton darkSystem, darkOff, darkOn;
    private Switch dynamicSwitch;
    private LinearLayout seedContainer;
    private Button previewFilled, previewTonal, previewOutlined;

    // Language
    private Spinner uiLangSpinner, gameLangSpinner;
    private ArrayAdapter<String> uiLangAdapter, gameLangAdapter;

    private int lastDarkMode = Md3Theme.THEME_SYSTEM;
    private boolean lastDynamic = false;
    private int lastSeed = Md3Theme.SEED_PRESETS[0];
    private String lastUiLang = Md3Theme.UI_LANG_SYSTEM;
    private String lastGameLang = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Md3Theme.applyBeforeOnCreate(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Md3Theme.applyAfterSetContentView(this);

        findViews();
        bindState();
        buildSeedColors();
        buildUiLangSpinner();
        buildGameLangSpinner();
        bindListeners();
        updateSeedVisualState();
    }

    // attachBaseContext：在系统创建Context时立刻注入正确Locale，保证所有LayoutInflater/Resources都是最新语言
    @Override
    protected void attachBaseContext(Context newBase) {
        try {
            // 先走一次applyUiLocale的Configuration，再wrap
            Configuration cfg = new Configuration(newBase.getResources().getConfiguration());
            Md3Theme.applyUiLocaleConfiguration(cfg, Md3Theme.getUiLang(newBase));
            Context ctx = newBase.createConfigurationContext(cfg);
            super.attachBaseContext(ctx);
            return;
        } catch (Throwable ignore) {}
        super.attachBaseContext(newBase);
    }

    private void findViews() {
        darkGroup = optFind(R.id.md3_dark_group);
        darkSystem = optFind(R.id.md3_dark_system);
        darkOff = optFind(R.id.md3_dark_off);
        darkOn = optFind(R.id.md3_dark_on);
        dynamicSwitch = optFind(R.id.md3_dynamic_switch);
        seedContainer = optFind(R.id.md3_seed_container);
        previewFilled = optFind(R.id.md3_preview_btn_filled);
        previewTonal = optFind(R.id.md3_preview_btn_tonal);
        previewOutlined = optFind(R.id.md3_preview_btn_outlined);

        uiLangSpinner   = optFind(R.id.md3_ui_lang_spinner);
        gameLangSpinner = optFind(R.id.md3_game_lang_spinner);

        ImageButton back = optFind(R.id.md3_button_back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
        }
    }

    private <T extends View> T optFind(int id) {
        try {
            View v = findViewById(id);
            if (v == null) {
                Log.w(TAG, "findViewById returned null for id=0x" + Integer.toHexString(id));
            }
            return (T) v;
        } catch (Throwable t) {
            Log.w(TAG, "findViewById failed for id=0x" + Integer.toHexString(id), t);
            return null;
        }
    }

    private static void setCheckedSafe(CompoundButton v, boolean checked) {
        if (v != null) {
            try { v.setChecked(checked); } catch (Throwable t) { Log.w(TAG, "setChecked failed", t); }
        }
    }

    private static void setEnabledSafe(View v, boolean enabled) {
        if (v != null) { try { v.setEnabled(enabled); } catch (Throwable ignore) {} }
    }

    private void bindState() {
        int mode = Md3Theme.getThemeMode(this);
        if (mode == Md3Theme.THEME_LIGHT)       setCheckedSafe(darkOff, true);
        else if (mode == Md3Theme.THEME_DARK)    setCheckedSafe(darkOn, true);
        else                                      setCheckedSafe(darkSystem, true);

        boolean dynAvail = Md3Theme.isDynamicColorAvailable();
        boolean dyn = dynAvail && Md3Theme.getDynamicColor(this);
        setCheckedSafe(dynamicSwitch, dyn);
        if (!dynAvail) {
            setCheckedSafe(dynamicSwitch, false);
            setEnabledSafe(dynamicSwitch, false);
        }

        lastDarkMode = mode;
        lastDynamic = dyn;
        lastSeed = Md3Theme.getSeedColor(this);

        // UI language — Spinner selection; done in buildUiLangSpinner()
        lastUiLang = Md3Theme.getUiLang(this);

        lastGameLang = Md3Theme.getGameLang(this);
    }

    // ========= UI language Spinner (10 languages: system/zh_CN/zh_TW/en/ru/ja/ko/fr/de/es) =========
    private String uiLangDisplayName(String value) {
        if (value == null) return "";
        String friendly;
        try {
            if (Md3Theme.UI_LANG_SYSTEM.equals(value))      friendly = getString(R.string.md3_ui_lang_follow_system);
            else if (Md3Theme.UI_LANG_ZH_CN.equals(value))  friendly = getString(R.string.md3_ui_lang_zh_cn);
            else if (Md3Theme.UI_LANG_ZH_TW.equals(value))  friendly = getString(R.string.md3_ui_lang_zh_tw);
            else if (Md3Theme.UI_LANG_EN.equals(value))     friendly = getString(R.string.md3_ui_lang_en);
            else if (Md3Theme.UI_LANG_RU.equals(value))     friendly = getString(R.string.md3_ui_lang_ru);
            else if (Md3Theme.UI_LANG_JA.equals(value))     friendly = getString(R.string.md3_ui_lang_ja);
            else if (Md3Theme.UI_LANG_KO.equals(value))     friendly = getString(R.string.md3_ui_lang_ko);
            else if (Md3Theme.UI_LANG_FR.equals(value))     friendly = getString(R.string.md3_ui_lang_fr);
            else if (Md3Theme.UI_LANG_DE.equals(value))     friendly = getString(R.string.md3_ui_lang_de);
            else if (Md3Theme.UI_LANG_ES.equals(value))     friendly = getString(R.string.md3_ui_lang_es);
            else friendly = value;
        } catch (Throwable t) {
            // Resource可能还没加载或不存在,fallback母语名
            if (Md3Theme.UI_LANG_SYSTEM.equals(value)) friendly = "Follow System";
            else if (Md3Theme.UI_LANG_ZH_CN.equals(value)) friendly = "简体中文";
            else if (Md3Theme.UI_LANG_ZH_TW.equals(value)) friendly = "繁體中文";
            else if (Md3Theme.UI_LANG_EN.equals(value))    friendly = "English";
            else if (Md3Theme.UI_LANG_RU.equals(value))    friendly = "Русский";
            else if (Md3Theme.UI_LANG_JA.equals(value))    friendly = "日本語";
            else if (Md3Theme.UI_LANG_KO.equals(value))    friendly = "한국어";
            else if (Md3Theme.UI_LANG_FR.equals(value))    friendly = "Français";
            else if (Md3Theme.UI_LANG_DE.equals(value))    friendly = "Deutsch";
            else if (Md3Theme.UI_LANG_ES.equals(value))    friendly = "Español";
            else friendly = value;
        }
        return friendly + "  ·  " + value;
    }

    private void buildUiLangSpinner() {
        if (uiLangSpinner == null) return;
        String[] raw = Md3Theme.UI_LANG_VALUES;
        String[] labels = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            labels[i] = uiLangDisplayName(raw[i]);
        }
        uiLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                try {
                    Md3Tokens t = Md3Theme.buildTokens(getContext());
                    ((TextView)v).setTextColor(t.onSurface);
                } catch (Throwable ignore) {}
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                try {
                    Md3Tokens t = Md3Theme.buildTokens(getContext());
                    TextView tv = (TextView)v;
                    tv.setTextColor(t.onSurface);
                    tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                    v.setBackgroundColor(t.surfaceContainerHigh);
                } catch (Throwable ignore) {}
                return v;
            }
        };
        uiLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        uiLangSpinner.setAdapter(uiLangAdapter);
        int selIdx = 0;
        for (int i = 0; i < raw.length; i++) {
            String a = (raw[i] == null) ? "" : raw[i];
            String b = (lastUiLang == null) ? "" : lastUiLang;
            if (a.equals(b)) { selIdx = i; break; }
        }
        try { uiLangSpinner.setSelection(selIdx, false); } catch (Throwable ignore) {}
    }

    private void buildGameLangSpinner() {
        if (gameLangSpinner == null) return;
        String[] raw = Md3Theme.GAME_LANG_VALUES;
        String[] labels = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String v = raw[i];
            if (v == null || v.isEmpty()) {
                try {
                    labels[i] = getString(R.string.md3_game_lang_default);
                } catch (Throwable t) { labels[i] = "Auto"; }
            } else {
                labels[i] = gameLangDisplayName(v);
            }
        }
        gameLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                try {
                    Md3Tokens t = Md3Theme.buildTokens(getContext());
                    ((TextView)v).setTextColor(t.onSurface);
                } catch (Throwable ignore) {}
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                try {
                    Md3Tokens t = Md3Theme.buildTokens(getContext());
                    TextView tv = (TextView)v;
                    tv.setTextColor(t.onSurface);
                    tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                    v.setBackgroundColor(t.surfaceContainerHigh);
                } catch (Throwable ignore) {}
                return v;
            }
        };
        gameLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gameLangSpinner.setAdapter(gameLangAdapter);
        int selIdx = 0;
        for (int i = 0; i < raw.length; i++) {
            String a = (raw[i] == null) ? "" : raw[i];
            String b = (lastGameLang == null) ? "" : lastGameLang;
            if (a.equals(b)) { selIdx = i; break; }
        }
        try { gameLangSpinner.setSelection(selIdx, false); } catch (Throwable ignore) {}
    }

    private String gameLangDisplayName(String code) {
        if (code == null) return "";
        String friendly;
        try {
            if ("schinese".equals(code))      friendly = getString(R.string.md3_ui_lang_zh_cn);
            else if ("tchinese".equals(code)) friendly = getString(R.string.md3_ui_lang_zh_tw);
            else if ("english".equals(code))  friendly = getString(R.string.md3_ui_lang_en);
            else if ("russian".equals(code))  friendly = "Русский";
            else if ("german".equals(code))   friendly = "Deutsch";
            else if ("french".equals(code))   friendly = "Français";
            else if ("italian".equals(code))  friendly = "Italiano";
            else if ("spanish".equals(code))  friendly = "Español";
            else if ("brazilian".equals(code))friendly = "Português-BR";
            else if ("latam".equals(code))    friendly = "Español-LATAM";
            else if ("japanese".equals(code)) friendly = "日本語";
            else if ("korean".equals(code))   friendly = "한국어";
            else if ("polish".equals(code))   friendly = "Polski";
            else if ("dutch".equals(code))    friendly = "Nederlands";
            else if ("czech".equals(code))    friendly = "Čeština";
            else if ("danish".equals(code))   friendly = "Dansk";
            else if ("finnish".equals(code))  friendly = "Suomi";
            else if ("greek".equals(code))    friendly = "Ελληνικά";
            else if ("hungarian".equals(code))friendly = "Magyar";
            else if ("norwegian".equals(code))friendly = "Norsk";
            else if ("portuguese".equals(code))friendly = "Português";
            else if ("romanian".equals(code)) friendly = "Română";
            else if ("swedish".equals(code))  friendly = "Svenska";
            else if ("thai".equals(code))     friendly = "ไทย";
            else if ("turkish".equals(code))  friendly = "Türkçe";
            else if ("ukrainian".equals(code))friendly = "Українська";
            else if ("bulgarian".equals(code))friendly = "Български";
            else friendly = code;
        } catch (Throwable t) { friendly = code; }
        return friendly + "  ·  " + code;
    }

    private void bindListeners() {
        if (darkGroup != null) {
            darkGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                    int mode = Md3Theme.THEME_SYSTEM;
                    if (checkedId == R.id.md3_dark_off) mode = Md3Theme.THEME_LIGHT;
                    else if (checkedId == R.id.md3_dark_on) mode = Md3Theme.THEME_DARK;
                    Md3Theme.setThemeMode(SettingsActivity.this, mode);
                    if (mode != lastDarkMode) { lastDarkMode = mode; refreshTheme(REFRESH_RECREATE); }
                }
            });
        }
        if (dynamicSwitch != null) {
            dynamicSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!Md3Theme.isDynamicColorAvailable()) {
                        try { Toast.makeText(SettingsActivity.this, R.string.md3_dynamic_color_not_available, Toast.LENGTH_LONG).show(); } catch (Throwable ignore) {}
                        setCheckedSafe(dynamicSwitch, false);
                        return;
                    }
                    Md3Theme.setDynamicColor(SettingsActivity.this, isChecked);
                    updateSeedVisualState();
                    if (isChecked) {
                        try { Toast.makeText(SettingsActivity.this, R.string.md3_dynamic_color_on_hint, Toast.LENGTH_LONG).show(); } catch (Throwable ignore) {}
                    }
                    if (isChecked != lastDynamic) { lastDynamic = isChecked; refreshTheme(REFRESH_RECREATE); }
                }
            });
        }

        View.OnClickListener previewClick = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int msg = 0;
                if (v == previewFilled) msg = R.string.md3_preview_hint_filled;
                else if (v == previewTonal) msg = R.string.md3_preview_hint_tonal;
                else if (v == previewOutlined) msg = R.string.md3_preview_hint_outlined;
                if (msg != 0) { try { Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {} }
            }
        };
        if (previewFilled != null) previewFilled.setOnClickListener(previewClick);
        if (previewTonal != null) previewTonal.setOnClickListener(previewClick);
        if (previewOutlined != null) previewOutlined.setOnClickListener(previewClick);

        // ===== Language =====
        if (uiLangSpinner != null) {
            uiLangSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    try {
                        String raw = Md3Theme.UI_LANG_VALUES[position];
                        String value = (raw == null) ? Md3Theme.UI_LANG_SYSTEM : raw;
                        Md3Theme.setUiLang(SettingsActivity.this, value);
                        if (!value.equals(lastUiLang)) { lastUiLang = value; refreshTheme(REFRESH_FULL_RESTART); }
                    } catch (Throwable ignore) {}
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
        if (gameLangSpinner != null) {
            gameLangSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    try {
                        String raw = Md3Theme.GAME_LANG_VALUES[position];
                        String value = (raw == null) ? "" : raw;
                        Md3Theme.setGameLang(SettingsActivity.this, value);
                        lastGameLang = value;
                    } catch (Throwable ignore) {}
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void updateSeedVisualState() {
        if (seedContainer == null) return;
        boolean dyn = Md3Theme.isDynamicColorAvailable() && Md3Theme.getDynamicColor(this);
        try { seedContainer.setAlpha(dyn ? 0.42f : 1.0f); } catch (Throwable ignore) {}
    }

    private void buildSeedColors() {
        if (seedContainer == null) return;
        try { seedContainer.removeAllViews(); } catch (Throwable ignore) {}
        int[] presets = Md3Theme.SEED_PRESETS;
        int current = Md3Theme.getSeedColor(this);
        int size = dp(44);
        int margin = dp(8);
        for (int i = 0; i < presets.length; i++) {
            final int color = presets[i];
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.RECTANGLE);
            g.setColor(color);
            g.setCornerRadius(size * 0.5f);
            final android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i == 0) lp.leftMargin = 0; else lp.leftMargin = margin;
            wrap.setLayoutParams(lp);
            wrap.setPadding(dp(3), dp(3), dp(3), dp(3));

            android.widget.FrameLayout.LayoutParams fplp = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
            android.view.View v = new android.view.View(this);
            v.setBackground(g);
            v.setLayoutParams(fplp);
            wrap.addView(v);

            final GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.RECTANGLE);
            ring.setColor(Color.TRANSPARENT);
            ring.setCornerRadius(size * 0.5f);
            try { ring.setStroke(dp(3), Md3Theme.buildTokens(this).primary.color); } catch (Throwable ignore) {}
            final android.view.View ringView = new android.view.View(this);
            ringView.setBackground(ring);
            ringView.setLayoutParams(fplp);
            ringView.setVisibility(colorsEqual(current, color) ? View.VISIBLE : View.INVISIBLE);
            wrap.addView(ringView);

            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    wrap.setForeground(new android.graphics.drawable.RippleDrawable(
                            new android.content.res.ColorStateList(new int[][]{{}}, new int[]{0x22000000}),
                            null, null));
                } catch (Throwable ignore) {}
            }
            wrap.setClickable(true);
            wrap.setFocusable(true);
            final int finalI = i;
            wrap.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View vv) {
                    if (Md3Theme.isDynamicColorAvailable() && Md3Theme.getDynamicColor(SettingsActivity.this)) {
                        try { Toast.makeText(SettingsActivity.this, R.string.md3_seed_ignored_when_dynamic, Toast.LENGTH_LONG).show(); } catch (Throwable ignore) {}
                    }
                    Md3Theme.setSeedColor(SettingsActivity.this, color);
                    if (lastSeed != color) {
                        lastSeed = color;
                        for (int j = 0; j < seedContainer.getChildCount(); j++) {
                            try {
                                ViewGroup c = (ViewGroup) seedContainer.getChildAt(j);
                                if (c.getChildCount() >= 2) c.getChildAt(1).setVisibility(j == finalI ? View.VISIBLE : View.INVISIBLE);
                            } catch (Throwable ignore) {}
                        }
                        // Seed color change不需要recreate SettingsActivity——当前View都是我们手动画的(token重建即可)
                        // Launcher那边也会在onResume自动感知变化刷新
                        refreshTheme(REFRESH_TOKEN_REDRAW);
                    }
                }
            });
            seedContainer.addView(wrap);
        }
    }

    private static boolean colorsEqual(int a, int b) {
        return (0x00FFFFFF & a) == (0x00FFFFFF & b);
    }

    private int dp(int dp) {
        try {
            float d = getResources().getDisplayMetrics().density;
            return Math.round(dp * d);
        } catch (Throwable t) { return dp * 2; }
    }

    private void refreshTheme() {
        // 历史兼容入口(默认走REFRESH_RECREATE,不做全栈重启)
        refreshTheme(REFRESH_RECREATE);
    }

    private void refreshTheme(int level) {
        try {
            if (level == REFRESH_TOKEN_REDRAW) {
                // ========= 档1：仅SeedColor变化 → 立刻重绘当前Activity & 预览卡片 =========
                //   SettingsActivity里所有颜色都是从Md3Tokens.buildTokens动态生成
                //   → 只需applyAfterSetContentView重新token化,不需要recreate
                try { Md3Theme.applyAfterSetContentView(this); } catch (Throwable ignore) {}
                // 手动刷新seed ring ring颜色（因为ring是buildSeedColors时创建的token.primary.color）
                try { buildSeedColors(); } catch (Throwable ignore) {}
                return;
            }
            if (level == REFRESH_FULL_RESTART) {
                // ========= 档3：语言变化 → 必须全栈重启LauncherActivity =========
                //   因为LayoutInflater缓存的strings资源/Context包装不会因为单Activity重建刷新
                Intent i = new Intent(this, LauncherActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                try { startActivity(i); } catch (Throwable ignore) {}
                try {
                    if (Build.VERSION.SDK_INT >= 16) finishAffinity();
                    else finish();
                } catch (Throwable ignore) {}
                return;
            }
            // ========= 档2：深色/动态取色变化 → recreate SettingsActivity自己,Launcher onResume感知 =========
            try {
                if (Build.VERSION.SDK_INT >= 11) recreate();
                else { finish(); startActivity(getIntent()); }
                return;
            } catch (Throwable t) {
                Log.w(TAG, "refreshTheme(RECREATE) failed", t);
            }
        } catch (Throwable t) {
            Log.w(TAG, "refreshTheme level=" + level + " failed", t);
        }
        // 兜底：极端情况也不要崩，简单finish
        try { finish(); } catch (Throwable ignore) {}
    }
}
