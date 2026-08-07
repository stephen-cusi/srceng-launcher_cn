package me.nillerusr

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.util.Linkify
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.valvesoftware.source.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.Markwon
import java.io.File
import me.nillerusr.md3.Md3Theme
import org.libsdl.app.SDLActivity

open class LauncherActivity : Activity() {
    private lateinit var predictiveBack: PredictiveBackController
    companion object {
        @JvmField
        var PKG_NAME: String? = null

        @JvmField
        var can_write = true

        @JvmField
        var cmdArgs: EditText? = null

        @JvmField
        var GamePath: EditText? = null

        @JvmField
        var res_width: EditText? = null

        @JvmField
        var res_height: EditText? = null

        @JvmField
        var useVolumeButtons: android.widget.CheckBox? = null

        @JvmField
        var check_updates: android.widget.CheckBox? = null

        const val REQUEST_PERMISSIONS = 42

        @JvmStatic
        fun getDefaultDir(): String {
            val dir = Environment.getExternalStorageDirectory() ?: return "/sdcard/"
            if (!dir.exists()) {
                return "/sdcard/"
            }
            return dir.path
        }

        @JvmStatic
        fun getAndroidDataDir(): String {
            val path = getDefaultDir() + "/Android/data/" + PKG_NAME + "/files"
            val directory = File(path)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            return path
        }

    }

    @JvmField
    var mPref: SharedPreferences? = null

    private var cachedThemeMode = Md3Theme.THEME_SYSTEM
    private var cachedDark = false
    private var cachedAmoled = false
    private var cachedDynamic = false
    private var cachedSeedColor = Md3Theme.SEED_PRESETS[0]
    private var cachedUiLang = Md3Theme.UI_LANG_SYSTEM

    open fun applyPermissions(permissions: Array<String>, code: Int) {
        val requestPermissions = ArrayList<String>()
        for (permission in permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions.add(permission)
            }
        }

        if (requestPermissions.isNotEmpty()) {
            requestPermissions(requestPermissions.toTypedArray(), code)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSIONS &&
            grantResults[0] == PackageManager.PERMISSION_DENIED
        ) {
            Toast.makeText(
                this,
                R.string.srceng_launcher_error_no_permission,
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        try {
            val cfg = Configuration(newBase.resources.configuration)
            Md3Theme.applyUiLocaleConfiguration(cfg, Md3Theme.getUiLang(newBase))
            super.attachBaseContext(newBase.createConfigurationContext(cfg))
            return
        } catch (_: Throwable) {
        }
        super.attachBaseContext(newBase)
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        Md3Theme.applyBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        PKG_NAME = application.packageName

        mPref = getSharedPreferences("mod", 0)
        setContentView(R.layout.activity_launcher)
        Md3Theme.applyAfterSetContentView(this)
        predictiveBack = PredictiveBackController(this) { finish() }
        predictiveBack.sync()

        cachedThemeMode = Md3Theme.getThemeMode(this)
        cachedDark = Md3Theme.resolveDark(this)
        cachedAmoled = Md3Theme.getAmoledBlack(this)
        cachedDynamic = Md3Theme.isDynamicColorAvailable() && Md3Theme.getDynamicColor(this)
        cachedSeedColor = Md3Theme.getSeedColor(this)
        cachedUiLang = Md3Theme.getUiLang(this)

        cmdArgs = findViewById(R.id.edit_cmdline)
        GamePath = findViewById(R.id.edit_gamepath)

        findViewById<View>(R.id.md3_button_settings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.button_launch).setOnClickListener(::startSource)

        findViewById<Button>(R.id.button_about).setOnClickListener {
            val scroll = ScrollView(this)
            val padding = (24 * resources.displayMetrics.density).toInt()
            scroll.setPadding(padding, 0, padding, padding)
            val text = TextView(this)
            text.text = getString(
                R.string.srceng_launcher_about_content,
                getString(R.string.srceng_launcher_about_text),
                getString(R.string.srceng_launcher_rewrite_text)
            )
            text.linksClickable = true
            text.setTextIsSelectable(true)
            Linkify.addLinks(text, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
            scroll.addView(text)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.srceng_launcher_about)
                .setView(scroll)
                .setNegativeButton(R.string.srceng_launcher_changelog) { _, _ ->
                    UpdateSystem.loadCurrentChangelog(this) { version, changelog, error ->
                        val content = TextView(this)
                        content.setPadding(padding, 0, padding, padding)
                        content.movementMethod = LinkMovementMethod.getInstance()
                        if (changelog != null) {
                            Markwon.builder(this).build().setMarkdown(content, changelog)
                        } else {
                            content.text = getString(R.string.srceng_launcher_changelog_failed, error)
                        }
                        val scroll = ScrollView(this)
                        scroll.addView(content)
                        MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.srceng_launcher_changelog_version, version))
                            .setView(scroll)
                            .setPositiveButton(android.R.string.ok, null)
                            .show().also { Md3Theme.applyDialog(it) }
                    }
                }
                .setPositiveButton(android.R.string.ok, null)
                .show().also { Md3Theme.applyDialog(it) }
        }

        findViewById<Button>(R.id.button_gamedir).setOnClickListener {
            val intent = Intent(this, DirchActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        resources.getString(R.string.last_commit)
        cmdArgs!!.setText(mPref!!.getString("argv", ""))
        GamePath!!.setText(mPref!!.getString("gamepath", getDefaultDir() + "/srceng"))

        applyPermissions(
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            ),
            REQUEST_PERMISSIONS
        )
    }

    open fun saveSettings(editor: SharedPreferences.Editor) {
        editor.putString("argv", cmdArgs!!.text.toString())
        editor.putString("gamepath", GamePath!!.text.toString())
        editor.commit()
    }

    open fun startSource(view: View) {
        GamePath!!.text.toString()

        val editor = mPref!!.edit()
        saveSettings(editor)
        editor.putBoolean("immersive_mode", true)
        editor.commit()

        val intent = Intent(this, SDLActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    public override fun onPause() {
        Log.v("SRCAPK", "onPause")
        saveSettings(mPref!!.edit())
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::predictiveBack.isInitialized) predictiveBack.sync()
        try {
            val newMode = Md3Theme.getThemeMode(this)
            val newDark = Md3Theme.resolveDark(this)
            val newAmoled = Md3Theme.getAmoledBlack(this)
            val newDyn = Md3Theme.isDynamicColorAvailable() && Md3Theme.getDynamicColor(this)
            val newSeed = Md3Theme.getSeedColor(this)
            if (newMode != cachedThemeMode || newDark != cachedDark || newAmoled != cachedAmoled ||
                newDyn != cachedDynamic || newSeed != cachedSeedColor
            ) {
                try {
                    Md3Theme.applyBeforeOnCreate(this)
                } catch (_: Throwable) {
                }
                try {
                    Md3Theme.applyAfterSetContentView(this)
                } catch (_: Throwable) {
                }
                cachedThemeMode = newMode
                cachedDark = newDark
                cachedAmoled = newAmoled
                cachedDynamic = newDyn
                cachedSeedColor = newSeed
            }
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        if (::predictiveBack.isInitialized) predictiveBack.release()
        super.onDestroy()
    }
}
