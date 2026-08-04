package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.valvesoftware.source.R
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.util.Arrays
import java.util.Comparator
import me.nillerusr.md3.Md3Theme

open class DirchActivity : Activity() {
    private lateinit var predictiveBack: PredictiveBackController
    private lateinit var body: LinearLayout
    private lateinit var header: TextView
    private lateinit var selectionHint: TextView
    private lateinit var choiceButton: Button
    private var currentDirectory: String? = null

    @JvmField
    var mPref: SharedPreferences? = null

    open fun ListDirectory(path: String) {
        val directory = File(path)
        val directories = directory.listFiles(FileFilter { it.isDirectory })
        if (directories == null) {
            Toast.makeText(this, R.string.srceng_dir_unreadable, Toast.LENGTH_SHORT).show()
            return
        }

        if (directories.size > 1) {
            Arrays.sort(directories, Comparator { first, second ->
                first.name.compareTo(second.name, ignoreCase = true)
            })
        }

        val canonicalPath = try {
            directory.canonicalPath
        } catch (_: IOException) {
            directory.absolutePath
        }
        currentDirectory = canonicalPath
        header.text = canonicalPath
        selectionHint.text = getString(R.string.srceng_dir_current_path, canonicalPath)
        choiceButton.isEnabled = true

        body.removeAllViews()
        val inflater = layoutInflater
        directory.parentFile?.let { addDirectoryView(inflater, "..", it.absolutePath) }
        for (child in directories) addDirectoryView(inflater, child.name, child.absolutePath)
        Md3Theme.applyAfterSetContentView(this)
    }

    private fun addDirectoryView(inflater: LayoutInflater, name: String, path: String) {
        val view = inflater.inflate(R.layout.directory, body, false)
        view.findViewById<TextView>(R.id.dirname).text = name
        bindPressAnimation(view)
        view.setOnClickListener { tapped ->
            tapped.animate().cancel()
            tapped.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .alpha(0.82f)
                .setDuration(70)
                .withEndAction {
                    tapped.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(120)
                        .start()
                    ListDirectory(path)
                }
                .start()
        }
        body.addView(view)
        animateDirectory(view, body.childCount - 1)
    }

    private fun animateDirectory(view: View, position: Int) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = dp(8).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay((position.coerceAtMost(8) * 18L))
            .setDuration(170)
            .start()
    }

    @Suppress("ClickableViewAccessibility")
    private fun bindPressAnimation(view: View) {
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touched.animate().cancel()
                    touched.animate()
                        .scaleX(0.975f)
                        .scaleY(0.975f)
                        .alpha(0.9f)
                        .setDuration(70)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touched.animate().cancel()
                    touched.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setInterpolator(OvershootInterpolator(1.25f))
                        .setDuration(150)
                        .start()
                }
            }
            false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    open fun getExtStoragePaths(): List<String> {
        val paths = ArrayList<String>()
        val files = File("/storage/").listFiles() ?: return paths
        val defaultStorage = Environment.getExternalStorageDirectory().absolutePath
        for (file in files) {
            if (!file.absolutePath.equals(defaultStorage, ignoreCase = true) &&
                file.isDirectory && file.canRead()
            ) {
                paths.add(file.absolutePath)
            }
        }
        return paths
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        Md3Theme.applyBeforeOnCreate(this)
        super.onCreate(savedInstanceState)

        mPref = getSharedPreferences("mod", 0)
        setContentView(R.layout.activity_directory_choice)
        Md3Theme.applyAfterSetContentView(this)
        predictiveBack = PredictiveBackController(this) { finish() }
        predictiveBack.sync()

        body = findViewById(R.id.bodych)
        header = findViewById(R.id.header_txt)
        selectionHint = findViewById(R.id.directory_choice_hint)
        choiceButton = findViewById(R.id.button_choice)
        findViewById<ImageButton>(R.id.md3_button_back).setOnClickListener { finish() }
        choiceButton.setOnClickListener {
            currentDirectory?.let { path ->
                val storedPath = "$path/"
                LauncherActivity.GamePath?.setText(storedPath)
                mPref!!.edit().putString("gamepath", storedPath).apply()
                finish()
            }
        }

        val defaultPath = LauncherActivity.getDefaultDir()
        val paths = getExtStoragePaths()
        if (paths.isEmpty()) {
            ListDirectory(defaultPath)
            return
        }

        addDirectoryView(layoutInflater, File(defaultPath).name.ifEmpty { defaultPath }, defaultPath)
        for (path in paths) addDirectoryView(layoutInflater, File(path).name.ifEmpty { path }, path)
        Md3Theme.applyAfterSetContentView(this)
    }

    override fun onDestroy() {
        if (::predictiveBack.isInitialized) predictiveBack.release()
        super.onDestroy()
    }
}
