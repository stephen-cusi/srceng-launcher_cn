package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.valvesoftware.source.R
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.util.Arrays
import java.util.Comparator
import me.nillerusr.md3.Md3Theme

open class DirchActivity : Activity(), View.OnTouchListener {
    companion object {
        @JvmField
        var cur_dir: String? = null

        @JvmField
        var body: LinearLayout? = null
    }

    @JvmField
    var mPref: SharedPreferences? = null

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val button = v.findViewById<TextView>(R.id.dirname)
            val currentDirectory = cur_dir
            if (currentDirectory == null) {
                ListDirectory("" + button.text)
            } else {
                ListDirectory(currentDirectory + "/" + button.text)
            }
        }
        return false
    }

    open fun ListDirectory(path: String) {
        val header = findViewById<TextView>(R.id.header_txt)
        val myDirectory = File(path)
        val directories = myDirectory.listFiles(FileFilter { it.isDirectory }) ?: return

        if (directories.size > 1) {
            Arrays.sort(directories, Comparator { first, second ->
                first.name.uppercase().compareTo(second.name.uppercase())
            })
        }

        val inflater = layoutInflater
        try {
            cur_dir = myDirectory.canonicalPath
            header.text = cur_dir
        } catch (_: IOException) {
        }

        body!!.removeAllViews()
        addDirectoryView(inflater, "..")
        for (directory in directories) {
            addDirectoryView(inflater, directory.name)
        }
        Md3Theme.applyAfterSetContentView(this)
    }

    private fun addDirectoryView(inflater: LayoutInflater, name: String) {
        val view = inflater.inflate(R.layout.directory, body, false)
        view.findViewById<TextView>(R.id.dirname).text = name
        body!!.addView(view)
        view.setOnTouchListener(this)
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
        cur_dir = null
        body = findViewById(R.id.bodych)
        findViewById<TextView>(R.id.header_txt).text = ""

        findViewById<Button>(R.id.button_choice).setOnClickListener {
            val currentDirectory = cur_dir
            if (currentDirectory != null) {
                LauncherActivity.GamePath?.setText("$currentDirectory/")
                val editor = mPref!!.edit()
                editor.putString("gamepath", "$currentDirectory/")
                editor.commit()
                finish()
            }
        }

        val paths = getExtStoragePaths()
        if (paths.isEmpty()) {
            ListDirectory(LauncherActivity.getDefaultDir())
            return
        }

        val inflater = layoutInflater
        addDirectoryView(inflater, LauncherActivity.getDefaultDir())
        for (directory in paths) {
            addDirectoryView(inflater, directory)
        }
        Md3Theme.applyAfterSetContentView(this)
    }
}
