package me.nillerusr

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.valvesoftware.source.R
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import me.nillerusr.md3.Md3Theme
import me.nillerusr.gma.GmaArchive
import me.nillerusr.vpk.VpkArchive

class VpkArchiveActivity : Activity() {
    private data class Item(val name: String, val path: String, val directory: Boolean, val size: Long)

    private val executor = Executors.newSingleThreadExecutor()
    private val selected = linkedSetOf<String>()
    private val scrollPositions = mutableMapOf<String, Int>()
    private data class ArchiveEntry(val path: String, val size: Long)

    private var vpkArchive: VpkArchive? = null
    private var gmaArchive: GmaArchive? = null
    private var currentPath = ""
    private var busy = false
    private lateinit var archiveFile: File
    private lateinit var title: TextView
    private lateinit var pathView: TextView
    private lateinit var statusArea: View
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var selectedView: TextView
    private lateinit var extractButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        Md3Theme.applyBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpk_archive)
        Md3Theme.applyAfterSetContentView(this)

        title = findViewById(R.id.vpk_archive_title)
        pathView = findViewById(R.id.vpk_archive_path)
        statusArea = findViewById(R.id.vpk_archive_status_area)
        status = findViewById(R.id.vpk_archive_status)
        progress = findViewById(R.id.vpk_archive_progress)
        scroll = findViewById(R.id.vpk_archive_scroll)
        body = findViewById(R.id.vpk_archive_body)
        selectedView = findViewById(R.id.vpk_archive_selected)
        extractButton = findViewById(R.id.vpk_archive_extract)
        findViewById<ImageButton>(R.id.md3_button_back).setOnClickListener { navigateBack() }
        extractButton.setOnClickListener { prepareExtraction() }

        val path = intent.getStringExtra(EXTRA_ARCHIVE_PATH) ?: return finish()
        archiveFile = File(path)
        title.text = archiveFile.name
        openArchive()
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

    @Deprecated("Uses the classic Activity back callback")
    override fun onBackPressed() = navigateBack()

    private fun openArchive() = runTask(R.string.vpk_reading) {
        if (archiveFile.extension.equals("gma", true)) gmaArchive = GmaArchive.open(archiveFile)
        else vpkArchive = VpkArchive.open(archiveFile)
        runOnUiThread { renderDirectory(0, true) }
    }

    private fun navigateBack() {
        if (busy) return
        if (selected.isNotEmpty()) {
            selected.clear()
            renderDirectory(scroll.scrollY)
        } else if (currentPath.isNotEmpty()) {
            scrollPositions[currentPath] = scroll.scrollY
            currentPath = currentPath.substringBeforeLast('/', "")
            renderDirectory(scrollPositions[currentPath] ?: 0, true)
        } else {
            finish()
            applyCloseTransition()
        }
    }

    private fun renderDirectory(restoreY: Int = scroll.scrollY, animate: Boolean = false) {
        val entries = archiveEntries()
        if (entries.isEmpty() && vpkArchive == null && gmaArchive == null) return
        pathView.text = if (currentPath.isEmpty()) {
            gmaArchive?.let { getString(R.string.gma_archive_summary, it.version, it.entries.size, it.title) }
                ?: getString(R.string.vpk_archive_summary, vpkArchive?.version ?: 0, entries.size)
        } else {
            "/$currentPath"
        }
        body.removeAllViews()
        val prefix = if (currentPath.isEmpty()) "" else "$currentPath/"
        val items = linkedMapOf<String, Item>()
        entries.forEach { entry ->
            if (!entry.path.startsWith(prefix)) return@forEach
            val remaining = entry.path.removePrefix(prefix)
            val name = remaining.substringBefore('/')
            if (name.isEmpty()) return@forEach
            val itemPath = prefix + name
            val isDirectory = '/' in remaining
            val existing = items[name]
            items[name] = Item(name, itemPath, isDirectory || existing?.directory == true, if (isDirectory) 0 else entry.size)
        }
        items.values.sortedWith(compareBy<Item> { !it.directory }.thenBy { it.name.lowercase(Locale.ROOT) }).forEach(::addEntry)
        selectedView.text = getString(R.string.vpk_picker_selected, selected.size)
        extractButton.text = getString(if (selected.isEmpty()) R.string.vpk_extract_all else R.string.vpk_extract_selected)
        Md3Theme.applyAfterSetContentView(this)
        scroll.post { scroll.scrollTo(0, restoreY) }
        if (animate) animateDirectory(body)
    }

    private fun addEntry(item: Item) {
        val row = layoutInflater.inflate(R.layout.vpk_file_picker_entry, body, false)
        bindPressAnimation(row)
        val checkBox = row.findViewById<CheckBox>(R.id.vpk_picker_check)
        val icon = row.findViewById<ImageView>(R.id.vpk_picker_icon)
        val name = row.findViewById<TextView>(R.id.vpk_picker_name)
        val detail = row.findViewById<TextView>(R.id.vpk_picker_detail)
        val selecting = selected.isNotEmpty()
        checkBox.visibility = if (selecting) View.VISIBLE else View.INVISIBLE
        icon.visibility = if (selecting) View.INVISIBLE else View.VISIBLE
        icon.setImageResource(if (item.directory) R.drawable.ic_vpk_folder else R.drawable.ic_vpk_file)
        checkBox.isChecked = item.path in selected
        if (selecting) {
            checkBox.alpha = 0f
            checkBox.scaleX = 0.72f
            checkBox.scaleY = 0.72f
            checkBox.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(OvershootInterpolator(1.6f))
                .setDuration(180)
                .start()
        }
        name.text = item.name
        detail.text = if (item.directory) getString(R.string.vpk_archive_folder) else formatSize(item.size)
        checkBox.setOnCheckedChangeListener { _, checked ->
            if (checked) selected += item.path else selected -= item.path
            renderDirectory(scroll.scrollY)
        }
        row.setOnLongClickListener {
            if (!busy) {
                row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                selected += item.path
                renderDirectory(scroll.scrollY)
            }
            true
        }
        row.setOnClickListener {
            when {
                busy -> Unit
                selected.isNotEmpty() -> {
                    if (item.path in selected) selected -= item.path else selected += item.path
                    renderDirectory(scroll.scrollY)
                }
                item.directory -> {
                    scrollPositions[currentPath] = scroll.scrollY
                    currentPath = item.path
                    renderDirectory(scrollPositions[currentPath] ?: 0, true)
                }
            }
        }
        body.addView(row)
    }

    private fun prepareExtraction() {
        val paths = selected.takeIf { it.isNotEmpty() }?.toSet()
        val destination = File(archiveFile.parentFile, archiveBaseName())
        val conflicts = archiveEntries().asSequence()
            .filter { entry -> paths == null || paths.any { entry.path == it || entry.path.startsWith("$it/") } }
            .any { File(destination, it.path).exists() }
        if (conflicts) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.vpk_overwrite_title)
                .setMessage(R.string.vpk_overwrite_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.vpk_overwrite) { _, _ -> extract(paths, destination, true) }
                .show()
        } else {
            extract(paths, destination, false)
        }
    }

    private fun extract(paths: Set<String>?, destination: File, overwrite: Boolean) = runTask(R.string.vpk_extracting) {
        when {
            vpkArchive != null -> vpkArchive!!.extract(destination, paths, overwrite, ::updateProgress)
            gmaArchive != null -> gmaArchive!!.extract(destination, paths, overwrite, ::updateProgress)
            else -> error(getString(R.string.vpk_no_archive))
        }
        runOnUiThread {
            selected.clear()
            Toast.makeText(this, getString(R.string.vpk_extract_done_path, destination.path), Toast.LENGTH_LONG).show()
            renderDirectory()
        }
    }

    private fun archiveBaseName(): String {
        val withoutExtension = archiveFile.name.dropLast(4)
        return if (archiveFile.extension.equals("vpk", true)) {
            Regex("_(?:dir|\\d{3})$", RegexOption.IGNORE_CASE).replace(withoutExtension, "")
        } else withoutExtension
    }

    private fun runTask(message: Int, block: () -> Unit) {
        if (busy) return
        setBusy(true)
        status.setText(message)
        executor.execute {
            try {
                block()
            } catch (error: Throwable) {
                runOnUiThread {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.srceng_launcher_error)
                        .setMessage(error.message ?: error.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun updateProgress(current: Int, total: Int, path: String) = runOnUiThread {
        progress.max = total.coerceAtLeast(1)
        progress.progress = current
        status.text = getString(R.string.vpk_progress, current, total, path)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        statusArea.visibility = if (value) View.VISIBLE else View.INVISIBLE
        progress.progress = 0
        extractButton.isEnabled = !value && (vpkArchive != null || gmaArchive != null)
    }

    private fun archiveEntries(): List<ArchiveEntry> = gmaArchive?.entries?.map { ArchiveEntry(it.path, it.size) }
        ?: vpkArchive?.entries?.map { ArchiveEntry(it.path, it.size) }.orEmpty()

    private fun animateDirectory(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationX = (12 * resources.displayMetrics.density)
        view.animate().alpha(1f).translationX(0f).setDuration(180).start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindPressAnimation(view: View) {
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touched.animate().cancel()
                    touched.animate()
                        .scaleX(0.975f)
                        .scaleY(0.975f)
                        .alpha(0.88f)
                        .setDuration(80)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touched.animate().cancel()
                    touched.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setInterpolator(OvershootInterpolator(1.4f))
                        .setDuration(180)
                        .start()
                }
            }
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun applyCloseTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun formatSize(size: Long): String = when {
        size >= 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f GiB", size / (1024.0 * 1024.0 * 1024.0))
        size >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f MiB", size / (1024.0 * 1024.0))
        size >= 1024L -> String.format(Locale.getDefault(), "%.2f KiB", size / 1024.0)
        else -> "$size B"
    }

    override fun onDestroy() {
        vpkArchive?.close()
        gmaArchive?.close()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ARCHIVE_PATH = "vpk_archive_path"
    }
}
