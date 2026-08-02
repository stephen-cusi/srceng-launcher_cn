package me.nillerusr;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import com.valvesoftware.source.R;

import me.nillerusr.md3.Md3Theme;
import me.nillerusr.md3.Md3Tokens;

public class SettingsActivity extends Activity {

    private RadioGroup darkGroup;
    private RadioButton darkSystem, darkOff, darkOn;
    private Switch dynamicSwitch;
    private LinearLayout seedContainer;

    private int lastDarkMode = Md3Theme.THEME_SYSTEM;
    private boolean lastDynamic = false;
    private int lastSeed = Md3Theme.SEED_PRESETS[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Md3Theme.applyBeforeOnCreate(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        Md3Theme.applyAfterSetContentView(this);

        findViews();
        bindState();
        bindListeners();
        buildSeedColors();
    }

    private void findViews() {
        darkGroup = findViewById(R.id.md3_dark_group);
        darkSystem = findViewById(R.id.md3_dark_system);
        darkOff = findViewById(R.id.md3_dark_off);
        darkOn = findViewById(R.id.md3_dark_on);
        dynamicSwitch = findViewById(R.id.md3_dynamic_switch);
        seedContainer = findViewById(R.id.md3_seed_container);

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
                if (isChecked != lastDynamic) { lastDynamic = isChecked; refreshTheme(); }
            }
        });
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
        // Super cheap: recreate activity so colors apply through applyBeforeOnCreate
        if (Build.VERSION.SDK_INT >= 11) recreate();
        else { finish(); startActivity(getIntent()); }
    }
}
