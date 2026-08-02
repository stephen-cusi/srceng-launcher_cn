package me.nillerusr;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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

    // Appearance
    private RadioGroup darkGroup;
    private RadioButton darkSystem, darkOff, darkOn;
    private Switch dynamicSwitch;
    private LinearLayout seedContainer;
    private LinearLayout previewCard;
    private Button previewFilled, previewTonal, previewOutlined;

    // Language
    private RadioGroup uiLangGroup;
    private RadioButton uiLangSystem, uiLangZhCn, uiLangZhTw, uiLangEn;
    private Spinner gameLangSpinner;
    private ArrayAdapter<String> gameLangAdapter;

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
        buildGameLangSpinner();
        bindListeners();
        updateSeedVisualState();
    }

    private void findViews() {
        darkGroup = findViewById(R.id.md3_dark_group);
        darkSystem = findViewById(R.id.md3_dark_system);
        darkOff = findViewById(R.id.md3_dark_off);
        darkOn = findViewById(R.id.md3_dark_on);
        dynamicSwitch = findViewById(R.id.md3_dynamic_switch);
        seedContainer = findViewById(R.id.md3_seed_container);
        previewCard = findViewById(R.id.md3_preview_card);
        previewFilled = findViewById(R.id.md3_preview_btn_filled);
        previewTonal = findViewById(R.id.md3_preview_btn_tonal);
        previewOutlined = findViewById(R.id.md3_preview_btn_outlined);

        uiLangGroup  = findViewById(R.id.md3_ui_lang_group);
        uiLangSystem = findViewById(R.id.md3_ui_lang_system);
        uiLangZhCn   = findViewById(R.id.md3_ui_lang_zh_cn);
        uiLangZhTw   = findViewById(R.id.md3_ui_lang_zh_tw);
        uiLangEn     = findViewById(R.id.md3_ui_lang_en);
        gameLangSpinner = findViewById(R.id.md3_game_lang_spinner);

        ImageButton back = findViewById(R.id.md3_button_back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
        }
    }

    private void bindState() {
        int mode = Md3Theme.getThemeMode(this);
        switch (mode) {
            case Md3Theme.THEME_LIGHT: darkOff.setChecked(true); break;
            case Md3Theme.THEME_DARK:  darkOn.setChecked(true); break;
            default:                    darkSystem.setChecked(true); break;
        }
        if (Md3Theme.isDynamicColorAvailable()) {
            dynamicSwitch.setChecked(Md3Theme.getDynamicColor(this));
        } else {
            dynamicSwitch.setChecked(false);
            dynamicSwitch.setEnabled(false);
        }
        lastDarkMode = mode;
        lastDynamic = Md3Theme.getDynamicColor(this);
        lastSeed = Md3Theme.getSeedColor(this);

        // UI language
        String uiLang = Md3Theme.getUiLang(this);
        if (Md3Theme.UI_LANG_ZH_CN.equals(uiLang))      uiLangZhCn.setChecked(true);
        else if (Md3Theme.UI_LANG_ZH_TW.equals(uiLang)) uiLangZhTw.setChecked(true);
        else if (Md3Theme.UI_LANG_EN.equals(uiLang))    uiLangEn.setChecked(true);
        else                                             uiLangSystem.setChecked(true);
        lastUiLang = uiLang;

        lastGameLang = Md3Theme.getGameLang(this);
    }

    private void buildGameLangSpinner() {
        String[] raw = Md3Theme.GAME_LANG_VALUES;
        String[] labels = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String v = raw[i];
            if (v == null || v.isEmpty()) {
                labels[i] = getString(R.string.md3_game_lang_default) + "  (" + getString(R.string.srceng_app_name) + ")";
            } else {
                labels[i] = gameLangDisplayName(v);
            }
        }
        gameLangAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            // Override getView / getDropDownView so text color follows MD3 tokens
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                Md3Tokens t = Md3Theme.buildTokens(getContext());
                try { ((TextView)v).setTextColor(t.onSurface); } catch (Throwable ignore) {}
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                Md3Tokens t = Md3Theme.buildTokens(getContext());
                try {
                    TextView tv = (TextView)v;
                    tv.setTextColor(t.onSurface);
                    tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                } catch (Throwable ignore) {}
                try { v.setBackgroundColor(t.surfaceContainerHigh); } catch (Throwable ignore) {}
                return v;
            }
        };
        gameLangAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gameLangSpinner.setAdapter(gameLangAdapter);
        // select current
        int selIdx = 0;
        for (int i = 0; i < raw.length; i++) {
            if ((raw[i] == null ? "" : raw[i]).equals(lastGameLang == null ? "" : lastGameLang)) { selIdx = i; break; }
        }
        gameLangSpinner.setSelection(selIdx, false);
    }

    /** Friendly display name for a Source engine language code */
    private String gameLangDisplayName(String code) {
        if (code == null) return "";
        // Keep the original code as technical name so users can verify against their game data.
        String friendly;
        if ("schinese".equals(code))      friendly = getString(R.string.md3_ui_lang_zh_cn);
        else if ("tchinese".equals(code)) friendly = getString(R.string.md3_ui_lang_zh_tw);
        else if ("english".equals(code))  friendly = getString(R.string.md3_ui_lang_en);
        else if ("russian".equals(code))  friendly = "Русский (Russian)";
        else if ("german".equals(code))   friendly = "Deutsch (German)";
        else if ("french".equals(code))   friendly = "Français (French)";
        else if ("italian".equals(code))  friendly = "Italiano (Italian)";
        else if ("spanish".equals(code))  friendly = "Español (Spanish)";
        else if ("brazilian".equals(code))friendly = "Português-BR (Brazilian)";
        else if ("latam".equals(code))    friendly = "Español-LATAM (Latin America)";
        else if ("japanese".equals(code)) friendly = "日本語 (Japanese)";
        else if ("korean".equals(code))   friendly = "한국어 (Korean)";
        else if ("polish".equals(code))   friendly = "Polski (Polish)";
        else if ("dutch".equals(code))    friendly = "Nederlands (Dutch)";
        else if ("czech".equals(code))    friendly = "Čeština (Czech)";
        else if ("danish".equals(code))   friendly = "Dansk (Danish)";
        else if ("finnish".equals(code))  friendly = "Suomi (Finnish)";
        else if ("greek".equals(code))    friendly = "Ελληνικά (Greek)";
        else if ("hungarian".equals(code))friendly = "Magyar (Hungarian)";
        else if ("norwegian".equals(code))friendly = "Norsk (Norwegian)";
        else if ("portuguese".equals(code))friendly = "Português (Portuguese)";
        else if ("romanian".equals(code)) friendly = "Română (Romanian)";
        else if ("swedish".equals(code))  friendly = "Svenska (Swedish)";
        else if ("thai".equals(code))     friendly = "ไทย (Thai)";
        else if ("turkish".equals(code))  friendly = "Türkçe (Turkish)";
        else if ("ukrainian".equals(code))friendly = "Українська (Ukrainian)";
        else if ("bulgarian".equals(code))friendly = "Български (Bulgarian)";
        else friendly = code;
        return friendly + "  ·  " + code;
    }

    private void bindListeners() {
        darkGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                int mode = Md3Theme.THEME_SYSTEM;
                if (checkedId == R.id.md3_dark_off) mode = Md3Theme.THEME_LIGHT;
                else if (checkedId == R.id.md3_dark_on) mode = Md3Theme.THEME_DARK;
                Md3Theme.setThemeMode(SettingsActivity.this, mode);
                if (mode != lastDarkMode) { lastDarkMode = mode; refreshTheme(); }
            }
        });
        dynamicSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!Md3Theme.isDynamicColorAvailable()) {
                    Toast.makeText(SettingsActivity.this, R.string.md3_dynamic_color_not_available, Toast.LENGTH_LONG).show();
                    dynamicSwitch.setChecked(false);
                    return;
                }
                Md3Theme.setDynamicColor(SettingsActivity.this, isChecked);
                updateSeedVisualState();
                if (isChecked) {
                    Toast.makeText(SettingsActivity.this, R.string.md3_dynamic_color_on_hint, Toast.LENGTH_LONG).show();
                }
                if (isChecked != lastDynamic) { lastDynamic = isChecked; refreshTheme(); }
            }
        });

        // 预览区按钮 —— 点击有明确反馈
        View.OnClickListener previewClick = new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (v == previewFilled) {
                    Toast.makeText(SettingsActivity.this, R.string.md3_preview_hint_filled, Toast.LENGTH_SHORT).show();
                } else if (v == previewTonal) {
                    Toast.makeText(SettingsActivity.this, R.string.md3_preview_hint_tonal, Toast.LENGTH_SHORT).show();
                } else if (v == previewOutlined) {
                    Toast.makeText(SettingsActivity.this, R.string.md3_preview_hint_outlined, Toast.LENGTH_SHORT).show();
                }
            }
        };
        if (previewFilled != null) previewFilled.setOnClickListener(previewClick);
        if (previewTonal != null) previewTonal.setOnClickListener(previewClick);
        if (previewOutlined != null) previewOutlined.setOnClickListener(previewClick);

        // ===== Language =====
        uiLangGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                String lang = Md3Theme.UI_LANG_SYSTEM;
                if (checkedId == R.id.md3_ui_lang_zh_cn) lang = Md3Theme.UI_LANG_ZH_CN;
                else if (checkedId == R.id.md3_ui_lang_zh_tw) lang = Md3Theme.UI_LANG_ZH_TW;
                else if (checkedId == R.id.md3_ui_lang_en) lang = Md3Theme.UI_LANG_EN;
                Md3Theme.setUiLang(SettingsActivity.this, lang);
                if (!lang.equals(lastUiLang)) { lastUiLang = lang; refreshTheme(); }
            }
        });
        gameLangSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String raw = Md3Theme.GAME_LANG_VALUES[position];
                String value = (raw == null) ? "" : raw;
                Md3Theme.setGameLang(SettingsActivity.this, value);
                lastGameLang = value;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /** 动态取色开启时，种子色区视觉上降级（半透明 + 提示 Toast 互斥） */
    private void updateSeedVisualState() {
        boolean dyn = Md3Theme.isDynamicColorAvailable() && Md3Theme.getDynamicColor(this);
        if (seedContainer != null) {
            seedContainer.setAlpha(dyn ? 0.42f : 1.0f);
        }
    }

    private void buildSeedColors() {
        seedContainer.removeAllViews();
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
            lp.rightMargin = 0; lp.topMargin = 0; lp.bottomMargin = 0;
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
            ring.setStroke(dp(3), Md3Theme.buildTokens(this).primary.color);
            final android.view.View ringView = new android.view.View(this);
            ringView.setBackground(ring);
            ringView.setLayoutParams(fplp);
            ringView.setVisibility(colorsEqual(current, color) ? View.VISIBLE : View.INVISIBLE);
            wrap.addView(ringView);

            if (Build.VERSION.SDK_INT >= 21) {
                wrap.setForeground(new android.graphics.drawable.RippleDrawable(
                        new android.content.res.ColorStateList(new int[][]{{}}, new int[]{0x22000000}),
                        null, null));
            }
            wrap.setClickable(true);
            wrap.setFocusable(true);
            final int finalI = i;
            wrap.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View vv) {
                    // 动态取色开启时提示种子色不会立即生效
                    if (Md3Theme.isDynamicColorAvailable() && Md3Theme.getDynamicColor(SettingsActivity.this)) {
                        Toast.makeText(SettingsActivity.this, R.string.md3_seed_ignored_when_dynamic, Toast.LENGTH_LONG).show();
                    }
                    Md3Theme.setSeedColor(SettingsActivity.this, color);
                    if (lastSeed != color) { lastSeed = color;
                        // update rings
                        for (int j = 0; j < seedContainer.getChildCount(); j++) {
                            ViewGroup c = (ViewGroup) seedContainer.getChildAt(j);
                            if (c.getChildCount() >= 2) c.getChildAt(1).setVisibility(j == finalI ? View.VISIBLE : View.INVISIBLE);
                        }
                        refreshTheme();
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
        float d = getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }

    private void refreshTheme() {
        // Recreate forces applyBeforeOnCreate to run again (applies locale + night mode + theme)
        if (Build.VERSION.SDK_INT >= 11) recreate();
        else { finish(); startActivity(getIntent()); }
    }
}
