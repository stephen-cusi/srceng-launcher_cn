package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.valvesoftware.source.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import me.nillerusr.md3.Md3Theme
import me.nillerusr.md3.Md3Tokens

open class SettingsActivity : Activity() {
    private var darkGroup: RadioGroup? = null
    private var darkSystem: RadioButton? = null
    private var darkOff: RadioButton? = null
    private var darkOn: RadioButton? = null
    private var dynamicSwitch: SwitchMaterial? = null
    private var seedContainer: LinearLayout? = null
    private var customColorContainer: LinearLayout? = null
    private var customColorPreview: View? = null
    private var customRed: SeekBar? = null
    private var customGreen: SeekBar? = null
    private var customBlue: SeekBar? = null
    private var customRedValue: TextView? = null
    private var customGreenValue: TextView? = null
    private var customBlueValue: TextView? = null
    private var customHex: EditText? = null
    private var previewFilled: Button? = null
    private var previewTonal: Button? = null
    private var previewOutlined: Button? = null
    private var updatingColorControls = false

    private var uiLangButton: Button? = null
    private var gameLangButton: Button? = null

    private var resModeGroup: RadioGroup? = null
    private var resModeDevice: RadioButton? = null
    private var resModePreset: RadioButton? = null
    private var resModeCustom: RadioButton? = null
    private var resPresetRow: LinearLayout? = null
    private var resCustomRow: LinearLayout? = null
    private var resPresetButton: Button? = null
    private var resCustomW: EditText? = null
    private var resCustomH: EditText? = null

    private var updateChannelButton: Button? = null
    private var updateMirrorButton: Button? = null
    private var checkUpdateButton: Button? = null
    private var testMirrorsButton: Button? = null
    private var updateStatus: TextView? = null
    private var mirrorTestStatus: TextView? = null

    private var lastDarkMode = Md3Theme.THEME_SYSTEM
    private var lastDynamic = false
    private var lastSeed = Md3Theme.SEED_PRESETS[0]
    private var lastUiLang: String? = Md3Theme.UI_LANG_SYSTEM
    private var lastGameLang: String? = ""
    private var lastResMode: String? = Md3Theme.RES_MODE_DEVICE
    private var lastResPresetIdx = 0
    private var lastResCustomW = 1280
    private var lastResCustomH = 720
    private var selectedUpdateChannel = UpdateSystem.CHANNEL_STABLE
    private var selectedUpdateMirror = UpdateSystem.MIRROR_AUTO

    override fun onCreate(savedInstanceState: Bundle?) {
        Md3Theme.applyBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Md3Theme.applyAfterSetContentView(this)
        findViews()
        bindState()
        buildSeedColors()
        buildUiLangSpinner()
        buildGameLangSpinner()
        buildResolutionPresetSpinner()
        buildUpdateChannelSpinner()
        bindListeners()
        updateSeedVisualState()
        updateResolutionVisibility()
    }

    override fun attachBaseContext(newBase: Context) {
        try {
            val configuration = Configuration(newBase.resources.configuration)
            Md3Theme.applyUiLocaleConfiguration(configuration, Md3Theme.getUiLang(newBase))
            super.attachBaseContext(newBase.createConfigurationContext(configuration))
            return
        } catch (_: Throwable) {
        }
        super.attachBaseContext(newBase)
    }

    private fun findViews() {
        darkGroup = optFind(R.id.md3_dark_group)
        darkSystem = optFind(R.id.md3_dark_system)
        darkOff = optFind(R.id.md3_dark_off)
        darkOn = optFind(R.id.md3_dark_on)
        dynamicSwitch = optFind(R.id.md3_dynamic_switch)
        seedContainer = optFind(R.id.md3_seed_container)
        customColorContainer = optFind(R.id.md3_seed_custom_container)
        customColorPreview = optFind(R.id.md3_seed_custom_preview)
        customRed = optFind(R.id.md3_seed_red)
        customGreen = optFind(R.id.md3_seed_green)
        customBlue = optFind(R.id.md3_seed_blue)
        customRedValue = optFind(R.id.md3_seed_red_value)
        customGreenValue = optFind(R.id.md3_seed_green_value)
        customBlueValue = optFind(R.id.md3_seed_blue_value)
        customHex = optFind(R.id.md3_seed_hex)
        previewFilled = optFind(R.id.md3_preview_btn_filled)
        previewTonal = optFind(R.id.md3_preview_btn_tonal)
        previewOutlined = optFind(R.id.md3_preview_btn_outlined)
        uiLangButton = optFind(R.id.md3_ui_lang_spinner)
        gameLangButton = optFind(R.id.md3_game_lang_spinner)
        resModeGroup = optFind(R.id.md3_res_mode_group)
        resModeDevice = optFind(R.id.md3_res_mode_device)
        resModePreset = optFind(R.id.md3_res_mode_preset)
        resModeCustom = optFind(R.id.md3_res_mode_custom)
        resPresetRow = optFind(R.id.md3_res_preset_row)
        resCustomRow = optFind(R.id.md3_res_custom_row)
        resPresetButton = optFind(R.id.md3_res_preset_spinner)
        resCustomW = optFind(R.id.md3_res_custom_w)
        resCustomH = optFind(R.id.md3_res_custom_h)
        updateChannelButton = optFind(R.id.md3_update_channel)
        updateMirrorButton = optFind(R.id.md3_update_mirror)
        checkUpdateButton = optFind(R.id.md3_check_update)
        testMirrorsButton = optFind(R.id.md3_test_mirrors)
        updateStatus = optFind(R.id.md3_update_status)
        mirrorTestStatus = optFind(R.id.md3_mirror_test_status)
        optFind<ImageButton>(R.id.md3_button_back)?.setOnClickListener { finish() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> optFind(id: Int): T? = try {
        val view = findViewById<View>(id)
        if (view == null) Log.w(TAG, "findViewById returned null for id=0x${Integer.toHexString(id)}")
        view as T?
    } catch (error: Throwable) {
        Log.w(TAG, "findViewById failed for id=0x${Integer.toHexString(id)}", error)
        null
    }

    private fun bindState() {
        val mode = Md3Theme.getThemeMode(this)
        setCheckedSafe(if (mode == Md3Theme.THEME_LIGHT) darkOff else if (mode == Md3Theme.THEME_DARK) darkOn else darkSystem, true)
        val dynamicAvailable = Md3Theme.isDynamicColorAvailable()
        val dynamic = dynamicAvailable && Md3Theme.getDynamicColor(this)
        setCheckedSafe(dynamicSwitch, dynamic)
        if (!dynamicAvailable) {
            setCheckedSafe(dynamicSwitch, false)
            setEnabledSafe(dynamicSwitch, false)
        }
        lastDarkMode = mode
        lastDynamic = dynamic
        lastSeed = Md3Theme.getSeedColor(this)
        updateCustomColorControls(lastSeed)
        lastUiLang = Md3Theme.getUiLang(this)
        lastGameLang = Md3Theme.getGameLang(this)
        lastResMode = Md3Theme.getResolutionMode(this)
        lastResPresetIdx = Md3Theme.getResolutionPresetIdx(this)
        lastResCustomW = Md3Theme.getResolutionCustomW(this)
        lastResCustomH = Md3Theme.getResolutionCustomH(this)
        val width = lastResCustomW.coerceIn(320, 8192)
        val height = lastResCustomH.coerceIn(240, 8192)
        if (width != lastResCustomW || height != lastResCustomH) {
            Md3Theme.setResolutionCustomW(this, width)
            Md3Theme.setResolutionCustomH(this, height)
            lastResCustomW = width
            lastResCustomH = height
        }
        setCheckedSafe(
            if (Md3Theme.RES_MODE_PRESET == lastResMode) resModePreset
            else if (Md3Theme.RES_MODE_CUSTOM == lastResMode) resModeCustom else resModeDevice,
            true
        )
        try { resCustomW?.setText(lastResCustomW.toString()) } catch (_: Throwable) {}
        try { resCustomH?.setText(lastResCustomH.toString()) } catch (_: Throwable) {}
    }

    private fun uiLangDisplayName(value: String?): String {
        if (value == null) return ""
        val friendly = try {
            when (value) {
                Md3Theme.UI_LANG_SYSTEM -> getString(R.string.md3_ui_lang_follow_system)
                Md3Theme.UI_LANG_ZH_CN -> getString(R.string.md3_ui_lang_zh_cn)
                Md3Theme.UI_LANG_ZH_TW -> getString(R.string.md3_ui_lang_zh_tw)
                Md3Theme.UI_LANG_EN -> getString(R.string.md3_ui_lang_en)
                Md3Theme.UI_LANG_RU -> getString(R.string.md3_ui_lang_ru)
                Md3Theme.UI_LANG_JA -> getString(R.string.md3_ui_lang_ja)
                Md3Theme.UI_LANG_KO -> getString(R.string.md3_ui_lang_ko)
                Md3Theme.UI_LANG_FR -> getString(R.string.md3_ui_lang_fr)
                Md3Theme.UI_LANG_DE -> getString(R.string.md3_ui_lang_de)
                Md3Theme.UI_LANG_ES -> getString(R.string.md3_ui_lang_es)
                else -> value
            }
        } catch (_: Throwable) {
            when (value) {
                Md3Theme.UI_LANG_SYSTEM -> "Follow System"
                Md3Theme.UI_LANG_ZH_CN -> "简体中文"
                Md3Theme.UI_LANG_ZH_TW -> "繁體中文"
                Md3Theme.UI_LANG_EN -> "English"
                Md3Theme.UI_LANG_RU -> "Русский"
                Md3Theme.UI_LANG_JA -> "日本語"
                Md3Theme.UI_LANG_KO -> "한국어"
                Md3Theme.UI_LANG_FR -> "Français"
                Md3Theme.UI_LANG_DE -> "Deutsch"
                Md3Theme.UI_LANG_ES -> "Español"
                else -> value
            }
        }
        return "$friendly  ·  $value"
    }

    private fun gameLangDisplayName(code: String?): String {
        if (code == null) return ""
        val friendly = try {
            when (code) {
                "schinese" -> getString(R.string.md3_ui_lang_zh_cn)
                "tchinese" -> getString(R.string.md3_ui_lang_zh_tw)
                "english" -> getString(R.string.md3_ui_lang_en)
                "russian" -> "Русский"
                "german" -> "Deutsch"
                "french" -> "Français"
                "italian" -> "Italiano"
                "spanish" -> "Español"
                "brazilian" -> "Português-BR"
                "latam" -> "Español-LATAM"
                "japanese" -> "日本語"
                "korean" -> "한국어"
                "polish" -> "Polski"
                "dutch" -> "Nederlands"
                "czech" -> "Čeština"
                "danish" -> "Dansk"
                "finnish" -> "Suomi"
                "greek" -> "Ελληνικά"
                "hungarian" -> "Magyar"
                "norwegian" -> "Norsk"
                "portuguese" -> "Português"
                "romanian" -> "Română"
                "swedish" -> "Svenska"
                "thai" -> "ไทย"
                "turkish" -> "Türkçe"
                "ukrainian" -> "Українська"
                "bulgarian" -> "Български"
                else -> code
            }
        } catch (_: Throwable) { code }
        return "$friendly  ·  $code"
    }

    private fun buildUiLangSpinner() {
        val button = uiLangButton ?: return
        val values = Md3Theme.UI_LANG_VALUES
        val initialSelection = values.indexOfFirst { (it ?: "") == (lastUiLang ?: "") }.let { if (it < 0) 0 else it }
        button.text = uiLangDisplayName(values[initialSelection])
        button.setOnClickListener {
            val labels = Array(values.size) { uiLangDisplayName(values[it]) }
            val selected = values.indexOfFirst { (it ?: "") == (lastUiLang ?: "") }.let { if (it < 0) 0 else it }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.md3_ui_language)
                .setSingleChoiceItems(labels, selected) { dialog, position ->
                    val value = values[position] ?: Md3Theme.UI_LANG_SYSTEM
                    Md3Theme.setUiLang(this, value)
                    dialog.dismiss()
                    if (value != lastUiLang) {
                        lastUiLang = value
                        refreshTheme(REFRESH_FULL_RESTART)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun buildGameLangSpinner() {
        val button = gameLangButton ?: return
        val values = Md3Theme.GAME_LANG_VALUES
        val labels = Array(values.size) { index ->
            val value = values[index]
            if (value.isNullOrEmpty()) try { getString(R.string.md3_game_lang_default) } catch (_: Throwable) { "Auto" }
            else gameLangDisplayName(value)
        }
        val initialSelection = values.indexOfFirst { (it ?: "") == (lastGameLang ?: "") }.let { if (it < 0) 0 else it }
        button.text = labels[initialSelection]
        button.setOnClickListener {
            val selected = values.indexOfFirst { (it ?: "") == (lastGameLang ?: "") }.let { if (it < 0) 0 else it }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.md3_game_language)
                .setSingleChoiceItems(labels, selected) { dialog, position ->
                    val value = values[position] ?: ""
                    Md3Theme.setGameLang(this, value)
                    lastGameLang = value
                    button.text = labels[position]
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun buildResolutionPresetSpinner() {
        val button = resPresetButton ?: return
        val presets = Md3Theme.RESOLUTION_PRESETS
        val labels = Array(presets.size) { ratioLabel(presets[it][0], presets[it][1]) }
        val initialSelection = if (lastResPresetIdx in presets.indices) lastResPresetIdx else 0
        lastResPresetIdx = initialSelection
        button.text = labels[initialSelection]
        button.setOnClickListener {
            val selected = if (lastResPresetIdx in presets.indices) lastResPresetIdx else 0
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.md3_res_preset_label)
                .setSingleChoiceItems(labels, selected) { dialog, position ->
                    Md3Theme.setResolutionPresetIdx(this, position)
                    lastResPresetIdx = position
                    button.text = labels[position]
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun updateResolutionVisibility() {
        try { resPresetRow?.visibility = if (Md3Theme.RES_MODE_PRESET == lastResMode) View.VISIBLE else View.GONE } catch (_: Throwable) {}
        try { resCustomRow?.visibility = if (Md3Theme.RES_MODE_CUSTOM == lastResMode) View.VISIBLE else View.GONE } catch (_: Throwable) {}
    }

    private fun saveCustomResolutionInputs() {
        if (Md3Theme.RES_MODE_CUSTOM != lastResMode) return
        val width = readInt(resCustomW, lastResCustomW).coerceIn(320, 8192)
        val height = readInt(resCustomH, lastResCustomH).coerceIn(240, 8192)
        Md3Theme.setResolutionCustomW(this, width)
        Md3Theme.setResolutionCustomH(this, height)
        lastResCustomW = width
        lastResCustomH = height
        try { resCustomW?.setText(width.toString()) } catch (_: Throwable) {}
        try { resCustomH?.setText(height.toString()) } catch (_: Throwable) {}
    }

    private fun buildUpdateChannelSpinner() {
        val channelButton = updateChannelButton ?: return
        val channelLabels = arrayOf(
            getString(R.string.md3_update_stable), getString(R.string.md3_update_dev)
        )
        val preferences = getSharedPreferences("mod", 0)
        selectedUpdateChannel = if (preferences.getString(UpdateSystem.PREF_CHANNEL, UpdateSystem.CHANNEL_STABLE) == UpdateSystem.CHANNEL_DEV) {
            UpdateSystem.CHANNEL_DEV
        } else {
            UpdateSystem.CHANNEL_STABLE
        }
        channelButton.text = channelLabels[if (selectedUpdateChannel == UpdateSystem.CHANNEL_DEV) 1 else 0]
        channelButton.setOnClickListener {
            val selected = if (selectedUpdateChannel == UpdateSystem.CHANNEL_DEV) 1 else 0
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.md3_update_channel)
                .setSingleChoiceItems(channelLabels, selected) { dialog, position ->
                    selectedUpdateChannel = if (position == 1) UpdateSystem.CHANNEL_DEV else UpdateSystem.CHANNEL_STABLE
                    preferences.edit().putString(UpdateSystem.PREF_CHANNEL, selectedUpdateChannel).apply()
                    channelButton.text = channelLabels[position]
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        updateMirrorButton?.let { mirrorButton ->
            val labels = arrayOf(getString(R.string.md3_update_mirror_auto), *UpdateSystem.MIRROR_NAMES)
            selectedUpdateMirror = mirrorId(mirrorPosition(
                preferences.getString(UpdateSystem.PREF_MIRROR, UpdateSystem.MIRROR_AUTO)
            ))
            mirrorButton.text = labels[mirrorPosition(selectedUpdateMirror)]
            mirrorButton.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.md3_update_mirror)
                    .setSingleChoiceItems(labels, mirrorPosition(selectedUpdateMirror)) { dialog, position ->
                        selectedUpdateMirror = mirrorId(position)
                        preferences.edit().putString(UpdateSystem.PREF_MIRROR, selectedUpdateMirror).apply()
                        mirrorButton.text = labels[position]
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        try {
            val current = packageManager.getPackageInfo(packageName, 0).versionName
            updateStatus?.text = getString(R.string.md3_update_current, current)
        } catch (_: Throwable) {}
    }

    private fun checkForUpdates() {
        val channel = selectedUpdateChannel
        val mirror = selectedUpdateMirror
        checkUpdateButton?.isEnabled = false
        updateStatus?.setText(R.string.md3_update_checking)
        UpdateSystem(this, channel, mirror, object : UpdateSystem.Callback {
            override fun onUpdateResult(result: UpdateSystem.Result) {
                checkUpdateButton?.isEnabled = true
                if (!result.success) {
                    updateStatus?.text = getString(R.string.md3_update_failed, result.error)
                    return
                }
                if (!result.published) {
                    updateStatus?.setText(R.string.md3_update_unpublished)
                    return
                }
                if (!result.available) {
                    updateStatus?.setText(R.string.md3_update_latest)
                    return
                }
                updateStatus?.text = getString(R.string.md3_update_available, result.versionName)
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(getString(R.string.md3_update_available, result.versionName))
                    .setMessage(result.changelog)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.md3_update_download) { _, _ ->
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.apkUrl))) }
                        catch (_: Throwable) { Toast.makeText(this@SettingsActivity, R.string.md3_update_open_failed, Toast.LENGTH_LONG).show() }
                    }.show()
            }
        }).execute()
    }

    private fun testUpdateMirrors() {
        testMirrorsButton?.isEnabled = false
        mirrorTestStatus?.setText(R.string.md3_update_mirror_testing)
        UpdateSystem.testMirrors(object : UpdateSystem.MirrorTestCallback {
            override fun onMirrorTestResult(available: BooleanArray) {
                testMirrorsButton?.isEnabled = true
                val status = mirrorTestStatus ?: return
                val text = SpannableStringBuilder()
                for (i in UpdateSystem.MIRROR_NAMES.indices) {
                    if (i > 0) text.append('\n')
                    val start = text.length
                    val okay = i < available.size && available[i]
                    text.append(UpdateSystem.MIRROR_NAMES[i]).append(": ").append(getString(
                        if (okay) R.string.md3_update_mirror_available else R.string.md3_update_mirror_unavailable
                    ))
                    text.setSpan(ForegroundColorSpan(if (okay) 0xFF2E7D32.toInt() else 0xFFC62828.toInt()), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                status.text = text
            }
        })
    }

    private fun bindListeners() {
        darkGroup?.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.md3_dark_off) Md3Theme.THEME_LIGHT else if (checkedId == R.id.md3_dark_on) Md3Theme.THEME_DARK else Md3Theme.THEME_SYSTEM
            Md3Theme.setThemeMode(this, mode)
            if (mode != lastDarkMode) { lastDarkMode = mode; refreshTheme(REFRESH_TOKEN_REDRAW) }
        }
        dynamicSwitch?.setOnCheckedChangeListener { _, checked ->
            if (!Md3Theme.isDynamicColorAvailable()) {
                try { Toast.makeText(this, R.string.md3_dynamic_color_not_available, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
                setCheckedSafe(dynamicSwitch, false)
            } else {
                Md3Theme.setDynamicColor(this, checked)
                updateSeedVisualState()
                if (checked) try { Toast.makeText(this, R.string.md3_dynamic_color_on_hint, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
                if (checked != lastDynamic) { lastDynamic = checked; refreshTheme(REFRESH_TOKEN_REDRAW) }
            }
        }
        bindCustomColorControls()
        checkUpdateButton?.setOnClickListener { checkForUpdates() }
        testMirrorsButton?.setOnClickListener { testUpdateMirrors() }
        val previewClick = View.OnClickListener { view ->
            val message = if (view === previewFilled) R.string.md3_preview_hint_filled else if (view === previewTonal) R.string.md3_preview_hint_tonal else if (view === previewOutlined) R.string.md3_preview_hint_outlined else 0
            if (message != 0) try { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
        }
        previewFilled?.setOnClickListener(previewClick)
        previewTonal?.setOnClickListener(previewClick)
        previewOutlined?.setOnClickListener(previewClick)
        resModeGroup?.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.md3_res_mode_preset) Md3Theme.RES_MODE_PRESET else if (checkedId == R.id.md3_res_mode_custom) Md3Theme.RES_MODE_CUSTOM else Md3Theme.RES_MODE_DEVICE
            if (Md3Theme.RES_MODE_CUSTOM != mode) saveCustomResolutionInputs()
            Md3Theme.setResolutionMode(this, mode)
            lastResMode = mode
            updateResolutionVisibility()
        }
        resCustomW?.addTextChangedListener(resolutionWatcher(320, { lastResCustomW }) { Md3Theme.setResolutionCustomW(this, it); lastResCustomW = it })
        resCustomH?.addTextChangedListener(resolutionWatcher(240, { lastResCustomH }) { Md3Theme.setResolutionCustomH(this, it); lastResCustomH = it })
    }

    private fun resolutionWatcher(minimum: Int, current: () -> Int, save: (Int) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (Md3Theme.RES_MODE_CUSTOM != lastResMode) return
            val value = try { s.toString().trim().toInt() } catch (_: Throwable) { return }
            if (value in minimum..8192 && value != current()) save(value)
        }
    }

    override fun onPause() {
        saveCustomResolutionInputs()
        super.onPause()
    }

    private fun updateSeedVisualState() {
        val dynamic = Md3Theme.isDynamicColorAvailable() && Md3Theme.getDynamicColor(this)
        try { seedContainer?.alpha = if (dynamic) 0.42f else 1f } catch (_: Throwable) {}
        try { customColorContainer?.alpha = if (dynamic) 0.42f else 1f } catch (_: Throwable) {}
    }

    private fun bindCustomColorControls() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !updatingColorControls) applyCustomColorFromSliders()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        customRed?.setOnSeekBarChangeListener(listener)
        customGreen?.setOnSeekBarChangeListener(listener)
        customBlue?.setOnSeekBarChangeListener(listener)
        customHex?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingColorControls) return
                val value = (s?.toString() ?: "").trim().removePrefix("#")
                if (!value.matches(Regex("(?i)[0-9a-f]{6}"))) return
                try { applyCustomSeed(0xFF000000.toInt() or value.toInt(16)) } catch (_: Throwable) {}
            }
        })
    }

    private fun applyCustomColorFromSliders() {
        applyCustomSeed(Color.rgb(customRed?.progress ?: 0, customGreen?.progress ?: 0, customBlue?.progress ?: 0))
    }

    private fun applyCustomSeed(input: Int) {
        val color = input or 0xFF000000.toInt()
        Md3Theme.setSeedColor(this, color)
        lastSeed = color
        updateCustomColorControls(color)
        refreshTheme(REFRESH_TOKEN_REDRAW)
    }

    private fun updateCustomColorControls(color: Int) {
        updatingColorControls = true
        try {
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            customRed?.progress = red
            customGreen?.progress = green
            customBlue?.progress = blue
            customRedValue?.text = red.toString()
            customGreenValue?.text = green.toString()
            customBlueValue?.text = blue.toString()
            customHex?.setText(String.format("#%06X", color and 0x00FFFFFF))
            customColorPreview?.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(color)
                setStroke(dp(1), 0x55000000)
            }
        } finally { updatingColorControls = false }
    }

    private fun buildSeedColors() {
        val container = seedContainer ?: return
        val presets = Md3Theme.SEED_PRESETS
        val current = Md3Theme.getSeedColor(this)
        val size = dp(44)
        val margin = dp(8)
        val reuse = container.childCount == presets.size
        if (!reuse) try { container.removeAllViews() } catch (_: Throwable) {}
        val tokens: Md3Tokens = Md3Theme.buildTokens(this)
        var ringColor = 0
        try { ringColor = tokens.primary.color } catch (_: Throwable) {}
        for (i in presets.indices) {
            val color = presets[i]
            val wrapper: FrameLayout
            val swatch: View
            val ringView: View
            val swatchBackground: GradientDrawable
            val ringBackground: GradientDrawable
            if (reuse) {
                wrapper = container.getChildAt(i) as FrameLayout
                swatch = wrapper.getChildAt(0)
                ringView = wrapper.getChildAt(1) ?: View(this)
                swatchBackground = swatch.background as GradientDrawable
                ringBackground = ringView.background as GradientDrawable
            } else {
                wrapper = FrameLayout(this)
                wrapper.layoutParams = LinearLayout.LayoutParams(size, size).apply { leftMargin = if (i == 0) 0 else margin }
                wrapper.setPadding(dp(3), dp(3), dp(3), dp(3))
                val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                swatchBackground = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE }
                swatch = View(this).apply { layoutParams = params }
                wrapper.addView(swatch)
                ringBackground = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE }
                ringView = View(this).apply { layoutParams = params }
                wrapper.addView(ringView)
                try { wrapper.foreground = RippleDrawable(ColorStateList(arrayOf(intArrayOf()), intArrayOf(0x22000000)), null, null) } catch (_: Throwable) {}
                wrapper.isClickable = true
                wrapper.isFocusable = true
            }
            swatchBackground.setColor(color)
            swatchBackground.cornerRadius = size * 0.5f
            swatch.background = swatchBackground
            ringBackground.setColor(Color.TRANSPARENT)
            ringBackground.cornerRadius = size * 0.5f
            try { ringBackground.setStroke(dp(3), ringColor) } catch (_: Throwable) {}
            ringView.background = ringBackground
            ringView.visibility = if (colorsEqual(current, color)) View.VISIBLE else View.INVISIBLE
            wrapper.setOnClickListener {
                if (Md3Theme.isDynamicColorAvailable() && Md3Theme.getDynamicColor(this)) try {
                    Toast.makeText(this, R.string.md3_seed_ignored_when_dynamic, Toast.LENGTH_LONG).show()
                } catch (_: Throwable) {}
                Md3Theme.setSeedColor(this, color)
                if (lastSeed != color) {
                    lastSeed = color
                    updateCustomColorControls(color)
                    for (index in 0 until container.childCount) try {
                        val child = container.getChildAt(index) as ViewGroup
                        if (child.childCount >= 2) child.getChildAt(1).visibility = if (index == i) View.VISIBLE else View.INVISIBLE
                    } catch (_: Throwable) {}
                    refreshTheme(REFRESH_TOKEN_REDRAW)
                }
            }
            if (!reuse) container.addView(wrapper)
        }
    }

    private fun dp(value: Int): Int = try { Math.round(value * resources.displayMetrics.density) } catch (_: Throwable) { value * 2 }

    private fun refreshTheme(level: Int) {
        try {
            if (level == REFRESH_TOKEN_REDRAW) {
                try { Md3Theme.applyBeforeOnCreate(this) } catch (_: Throwable) {}
                try { Md3Theme.applyAfterSetContentView(this) } catch (_: Throwable) {}
                try { buildSeedColors() } catch (_: Throwable) {}
                return
            }
            if (level == REFRESH_FULL_RESTART) {
                val intent = Intent(this, LauncherActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                try { startActivity(intent) } catch (_: Throwable) {}
                try { finishAffinity() } catch (_: Throwable) {}
                return
            }
        } catch (error: Throwable) {
            Log.w(TAG, "refreshTheme level=$level failed", error)
        }
        try { finish() } catch (_: Throwable) {}
    }

    private fun itemSelected(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    companion object {
        private const val TAG = "SettingsActivity"
        private const val REFRESH_TOKEN_REDRAW = 0
        private const val REFRESH_FULL_RESTART = 2

        private fun setCheckedSafe(view: CompoundButton?, checked: Boolean) {
            if (view != null) try { view.isChecked = checked } catch (error: Throwable) { Log.w(TAG, "setChecked failed", error) }
        }

        private fun setEnabledSafe(view: View?, enabled: Boolean) {
            if (view != null) try { view.isEnabled = enabled } catch (_: Throwable) {}
        }

        private fun readInt(view: EditText?, fallback: Int): Int = try {
            val value = view?.text?.toString()?.trim().orEmpty().toInt()
            if (value > 0) value else fallback
        } catch (_: Throwable) { fallback }

        private fun ratioLabel(width: Int, height: Int): String {
            var a = width
            var b = height
            try { while (b != 0) { val next = b; b = a % b; a = next } } catch (_: Throwable) { a = 1 }
            val divisor = Math.max(1, a)
            val ratioWidth = width / divisor
            val ratioHeight = height / divisor
            val ratio = when {
                ratioWidth == 16 && ratioHeight == 9 -> "16:9"
                ratioWidth == 16 && ratioHeight == 10 -> "16:10"
                ratioWidth == 4 && ratioHeight == 3 -> "4:3"
                ratioWidth == 3 && ratioHeight == 2 -> "3:2"
                ratioWidth == 5 && ratioHeight == 4 -> "5:4"
                ratioWidth == 21 && ratioHeight == 9 -> "21:9"
                else -> "$ratioWidth:$ratioHeight"
            }
            return "$width × $height  ($ratio)"
        }

        private fun mirrorPosition(id: String?): Int {
            val index = UpdateSystem.MIRROR_IDS.indexOf(id)
            return if (index < 0) 0 else index + 1
        }

        private fun mirrorId(position: Int): String =
            if (position > 0 && position <= UpdateSystem.MIRROR_IDS.size) UpdateSystem.MIRROR_IDS[position - 1] else UpdateSystem.MIRROR_AUTO

        private fun colorsEqual(first: Int, second: Int) =
            (first and 0x00FFFFFF) == (second and 0x00FFFFFF)
    }
}
