package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
import com.valvesoftware.source.R
import java.io.File
import java.io.IOException
import me.nillerusr.md3.Md3Theme

/**
 * HL2SB addon manager: lists <gamepath>/hl2sb/addons (folders + .gma archives)
 * and persists enable/disable state to <gamepath>/hl2sb/addons_disabled.txt,
 * the same file the engine reads (one name per line, '#' and ';' comments).
 */
open class AddonManagerActivity : Activity() {
    private lateinit var predictiveBack: PredictiveBackController
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView
    private var addonsDirectory: File? = null
    private val disabledAddons = linkedSetOf<String>()

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

    public override fun onCreate(savedInstanceState: Bundle?) {
        Md3Theme.applyBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addon_manager)
        Md3Theme.applyAfterSetContentView(this)
        predictiveBack = PredictiveBackController(this) { finish() }
        predictiveBack.sync()

        listContainer = findViewById(R.id.addon_list)
        statusView = findViewById(R.id.addon_manager_status)
        findViewById<View>(R.id.md3_button_back).setOnClickListener { finish() }
        reload()
    }

    override fun onDestroy() {
        if (::predictiveBack.isInitialized) predictiveBack.release()
        super.onDestroy()
    }

    private fun gameDirectory(): File? {
        val preferences = getSharedPreferences("mod", 0)
        val gamePath = preferences.getString("gamepath", LauncherActivity.getDefaultDir() + "/srceng")
            ?: return null
        return File(File(gamePath, "hl2sb"), "addons")
    }

    private fun disabledFile(): File? = addonsDirectory?.parentFile?.let { File(it, "addons_disabled.txt") }

    private fun reload() {
        listContainer.removeAllViews()
        val directory = gameDirectory()
        if (directory == null || !directory.isDirectory) {
            statusView.setText(R.string.addon_manager_load_failed)
            return
        }
        addonsDirectory = directory
        disabledAddons.clear()
        disabledAddons.addAll(readDisabledNames())
        statusView.text = directory.path

        val entries = directory.listFiles()?.filter { entry ->
            !entry.name.startsWith(".") && (entry.isDirectory || entry.name.lowercase().endsWith(".gma"))
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

        if (entries.isEmpty()) {
            statusView.setText(R.string.addon_manager_empty)
            return
        }
        val inflater = layoutInflater
        for (entry in entries) addAddonRow(inflater, entry)
        Md3Theme.applyAfterSetContentView(this)
    }

    private fun addAddonRow(inflater: LayoutInflater, entry: File) {
        val isArchive = !entry.isDirectory
        val name = if (isArchive) entry.name.removeSuffix(".gma") else entry.name
        val row = inflater.inflate(R.layout.addon_row, listContainer, false)
        row.findViewById<TextView>(R.id.addon_row_name).text = name
        row.findViewById<TextView>(R.id.addon_row_detail).setText(
            if (isArchive) R.string.addon_manager_gma else R.string.vpk_manager_folder
        )
        val switch = row.findViewById<MaterialSwitch>(R.id.addon_row_switch)
        switch.isChecked = !disabledAddons.contains(name.lowercase())
        switch.setOnCheckedChangeListener { _, checked ->
            val key = name.lowercase()
            if (checked) disabledAddons.remove(key) else disabledAddons.add(key)
            persist()
        }
        listContainer.addView(row)
    }

    private fun readDisabledNames(): Set<String> {
        val file = disabledFile() ?: return emptySet()
        if (!file.isFile) return emptySet()
        val names = linkedSetOf<String>()
        try {
            file.readLines().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEach
                names.add(line.lowercase())
            }
        } catch (_: IOException) {
        }
        return names
    }

    private fun persist() {
        val file = disabledFile() ?: return
        try {
            val temporary = File(file.parentFile, file.name + ".tmp")
            val builder = StringBuilder("# addons_disabled.txt - one addon name per line\n")
            builder.append("# managed by HL2Box launcher; lines starting with '#' or ';' are comments\n")
            for (name in disabledAddons.toSortedSet()) builder.append(name).append('\n')
            temporary.writeText(builder.toString())
            if (file.exists()) file.delete()
            if (!temporary.renameTo(file)) throw IOException("rename failed")
            try {
                Toast.makeText(this, R.string.addon_manager_saved, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {}
        } catch (_: Throwable) {
            try { Toast.makeText(this, R.string.addon_manager_save_failed, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
        }
    }
}
