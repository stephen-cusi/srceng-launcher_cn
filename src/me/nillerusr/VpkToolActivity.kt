package me.nillerusr

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.valvesoftware.source.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import me.nillerusr.md3.Md3Theme
import me.nillerusr.vpk.VpkWriter

class VpkToolActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val selected = linkedSetOf<String>()
    private val scrollPositions = mutableMapOf<String, Int>()
    private var pendingFiles: List<File> = emptyList()
    private var pendingMove = false
    private var busy = false
    private lateinit var currentDirectory: File
    private lateinit var pathView: TextView
    private lateinit var statusArea: View
    private lateinit var statusView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var footer: View
    private lateinit var selectedView: TextView
    private lateinit var selectionActions: View
    private lateinit var pasteActions: View

    override fun onCreate(savedInstanceState: Bundle?) {
        Md3Theme.applyBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpk_tool)
        Md3Theme.applyAfterSetContentView(this)

        pathView = findViewById(R.id.vpk_manager_path)
        statusArea = findViewById(R.id.vpk_manager_status_area)
        statusView = findViewById(R.id.vpk_manager_status)
        progress = findViewById(R.id.vpk_manager_progress)
        body = findViewById(R.id.vpk_manager_body)
        scroll = findViewById(R.id.vpk_manager_scroll)
        footer = findViewById(R.id.vpk_manager_footer)
        selectedView = findViewById(R.id.vpk_manager_selected)
        selectionActions = findViewById(R.id.vpk_manager_actions)
        pasteActions = findViewById(R.id.vpk_manager_paste_actions)
        findViewById<ImageButton>(R.id.md3_button_back).setOnClickListener { navigateBack() }
        findViewById<Button>(R.id.vpk_manager_copy).setOnClickListener { beginTransfer(false) }
        findViewById<Button>(R.id.vpk_manager_move).setOnClickListener { beginTransfer(true) }
        findViewById<Button>(R.id.vpk_manager_pack).setOnClickListener { choosePackVersion() }
        findViewById<Button>(R.id.vpk_manager_delete).setOnClickListener { confirmDelete() }
        findViewById<Button>(R.id.vpk_manager_cancel).setOnClickListener { cancelTransfer() }
        findViewById<Button>(R.id.vpk_manager_paste).setOnClickListener { pasteHere() }

        val start = File(LauncherActivity.getDefaultDir()).takeIf { it.isDirectory && it.canRead() }
            ?: Environment.getExternalStorageDirectory()
        showDirectory(start)
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

    private fun navigateBack() {
        if (busy) return
        if (selected.isNotEmpty()) {
            selected.clear()
            renderDirectory(scroll.scrollY)
            return
        }
        val parent = currentDirectory.parentFile
        if (parent != null && parent.canRead()) {
            showDirectory(parent, true)
        } else if (pendingFiles.isNotEmpty()) {
            cancelTransfer()
        } else {
            finish()
        }
    }

    private fun showDirectory(directory: File, animate: Boolean = false) {
        if (busy) return
        val canonical = canonicalFile(directory)
        if (!canonical.isDirectory || !canonical.canRead() || canonical.listFiles() == null) {
            Toast.makeText(this, R.string.vpk_picker_unreadable, Toast.LENGTH_LONG).show()
            return
        }
        if (::currentDirectory.isInitialized) scrollPositions[currentDirectory.path] = scroll.scrollY
        currentDirectory = canonical
        selected.clear()
        renderDirectory(scrollPositions[canonical.path] ?: 0, animate)
    }

    private fun renderDirectory(restoreY: Int = scroll.scrollY, animate: Boolean = false) {
        pathView.text = currentDirectory.path
        body.removeAllViews()
        val children = currentDirectory.listFiles()?.filter { it.canRead() }?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }
        ).orEmpty()
        if (children.isEmpty()) {
            TextView(this).apply {
                setText(R.string.vpk_folder_empty)
                setPadding(dp(16), dp(32), dp(16), dp(32))
                gravity = android.view.Gravity.CENTER
                tag = "subtitle"
                body.addView(this)
            }
        } else {
            children.forEach(::addEntry)
        }
        updateFooter()
        Md3Theme.applyAfterSetContentView(this)
        scroll.post { scroll.scrollTo(0, restoreY) }
        if (animate) animateDirectory(body)
    }

    private fun addEntry(file: File) {
        val row = layoutInflater.inflate(R.layout.vpk_file_picker_entry, body, false)
        bindPressAnimation(row)
        val checkBox = row.findViewById<CheckBox>(R.id.vpk_picker_check)
        val icon = row.findViewById<ImageView>(R.id.vpk_picker_icon)
        val name = row.findViewById<TextView>(R.id.vpk_picker_name)
        val detail = row.findViewById<TextView>(R.id.vpk_picker_detail)
        val path = canonicalFile(file).path
        val selecting = selected.isNotEmpty()
        checkBox.visibility = if (selecting) View.VISIBLE else View.INVISIBLE
        icon.visibility = if (selecting) View.INVISIBLE else View.VISIBLE
        icon.setImageResource(when {
            file.isDirectory -> R.drawable.ic_vpk_folder
            file.isSupportedArchive() -> R.drawable.ic_vpk_archive
            else -> R.drawable.ic_vpk_file
        })
        checkBox.isChecked = path in selected
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
        name.text = file.name
        detail.text = when {
            file.isDirectory -> getString(R.string.vpk_manager_folder)
            file.extension.equals("vpk", true) -> getString(R.string.vpk_manager_archive, formatSize(file.length()))
            file.extension.equals("gma", true) -> getString(R.string.gma_manager_archive, formatSize(file.length()))
            else -> formatSize(file.length())
        }
        checkBox.setOnCheckedChangeListener { _, checked ->
            if (checked) selected += path else selected -= path
            renderDirectory(scroll.scrollY)
        }
        row.setOnLongClickListener {
            if (!busy && pendingFiles.isEmpty()) {
                row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                selected += path
                renderDirectory(scroll.scrollY)
            }
            true
        }
        row.setOnClickListener {
            when {
                busy -> Unit
                selected.isNotEmpty() -> {
                    if (path in selected) selected -= path else selected += path
                    renderDirectory(scroll.scrollY)
                }
                file.isDirectory -> showDirectory(file, true)
                file.isSupportedArchive() -> {
                    startActivity(Intent(this, VpkArchiveActivity::class.java).putExtra(VpkArchiveActivity.EXTRA_ARCHIVE_PATH, file.path))
                    applyOpenTransition()
                }
            }
        }
        body.addView(row)
    }

    private fun updateFooter() {
        val transferring = pendingFiles.isNotEmpty()
        footer.visibility = if (selected.isNotEmpty() || transferring) View.VISIBLE else View.GONE
        selectionActions.visibility = if (selected.isNotEmpty() && !transferring) View.VISIBLE else View.GONE
        pasteActions.visibility = if (transferring) View.VISIBLE else View.GONE
        selectedView.text = if (transferring) {
            getString(if (pendingMove) R.string.vpk_move_destination else R.string.vpk_copy_destination, pendingFiles.size)
        } else {
            getString(R.string.vpk_picker_selected, selected.size)
        }
    }

    private fun beginTransfer(move: Boolean) {
        pendingFiles = selectedFiles()
        pendingMove = move
        selected.clear()
        renderDirectory()
    }

    private fun cancelTransfer() {
        pendingFiles = emptyList()
        pendingMove = false
        renderDirectory()
    }

    private fun pasteHere() {
        val sources = pendingFiles
        val move = pendingMove
        runTask(if (move) R.string.vpk_moving else R.string.vpk_copying) {
            validateTransfer(sources, currentDirectory)
            sources.forEachIndexed { index, source ->
                val target = File(currentDirectory, source.name)
                if (move && source.renameTo(target)) {
                    updateProgress(index + 1, sources.size, source.name)
                } else {
                    try {
                        copyItem(source, target) { name -> updateProgress(index + 1, sources.size, name) }
                    } catch (error: Throwable) {
                        deleteItem(target)
                        throw error
                    }
                    if (move && !deleteItem(source)) {
                        deleteItem(target)
                        error("Cannot remove ${source.path}")
                    }
                }
            }
            pendingFiles = emptyList()
            pendingMove = false
            runOnUiThread {
                Toast.makeText(this, if (move) R.string.vpk_move_done else R.string.vpk_copy_done, Toast.LENGTH_LONG).show()
                renderDirectory()
            }
        }
    }

    private fun validateTransfer(sources: List<File>, destination: File) {
        val destinationPath = canonicalFile(destination).path
        sources.forEach { source ->
            val sourcePath = canonicalFile(source).path
            require(source.parentFile?.let(::canonicalFile)?.path != destinationPath) { "Source and destination are the same" }
            require(!File(destination, source.name).exists()) { "File already exists: ${source.name}" }
            require(!source.isDirectory || !destinationPath.startsWith(sourcePath + File.separator)) {
                "Cannot place a folder inside itself: ${source.name}"
            }
        }
    }

    private fun copyItem(source: File, target: File, progressCallback: (String) -> Unit) {
        if (source.isDirectory) {
            require(target.mkdir()) { "Cannot create ${target.path}" }
            source.listFiles()?.forEach { child -> copyItem(child, File(target, child.name), progressCallback) }
                ?: error("Cannot list ${source.path}")
        } else {
            BufferedInputStream(FileInputStream(source)).use { input ->
                BufferedOutputStream(FileOutputStream(target)).use { output -> input.copyTo(output, BUFFER_SIZE) }
            }
            progressCallback(source.name)
        }
    }

    private fun confirmDelete() {
        val files = selectedFiles()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vpk_delete_confirm_title)
            .setMessage(getString(R.string.vpk_delete_confirm, files.size))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vpk_delete) { _, _ ->
                runTask(R.string.vpk_deleting) {
                    files.forEachIndexed { index, file ->
                        require(deleteItem(file)) { "Cannot delete ${file.path}" }
                        updateProgress(index + 1, files.size, file.name)
                    }
                    runOnUiThread {
                        selected.clear()
                        Toast.makeText(this, R.string.vpk_delete_done, Toast.LENGTH_LONG).show()
                        renderDirectory()
                    }
                }
            }
            .show()
    }

    private fun deleteItem(file: File): Boolean {
        if (file.isDirectory) file.listFiles()?.forEach { if (!deleteItem(it)) return false }
        return file.delete()
    }

    private fun choosePackVersion() {
        val inputs = selectedFiles()
        val labels = arrayOf(getString(R.string.vpk_version_1), getString(R.string.vpk_version_2))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vpk_create_version)
            .setItems(labels) { _, index -> chooseOutputName(inputs, index + 1) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseOutputName(inputs: List<File>, version: Int) {
        val name = EditText(this).apply {
            setText(R.string.vpk_default_filename)
            selectAll()
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(dp(24), 0, dp(24), 0)
            addView(name)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vpk_output_name)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                var fileName = name.text.toString().trim()
                if (!fileName.lowercase(Locale.ROOT).endsWith(".vpk")) fileName += ".vpk"
                val output = File(currentDirectory, fileName)
                if (fileName == ".vpk" || fileName.contains('/') || fileName.contains('\\') || output.exists()) {
                    name.error = getString(if (output.exists()) R.string.vpk_output_exists else R.string.vpk_output_name_invalid)
                    return@setOnClickListener
                }
                dialog.dismiss()
                runTask(R.string.vpk_creating) {
                    VpkWriter.create(inputs, output, version, ::updateProgress)
                    runOnUiThread {
                        selected.clear()
                        Toast.makeText(this, R.string.vpk_create_done_short, Toast.LENGTH_LONG).show()
                        renderDirectory()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun selectedFiles(): List<File> = selected.map(::File).sortedBy { it.path.length }.filter { candidate ->
        val candidatePath = canonicalFile(candidate).path
        selected.none { other ->
            val parent = canonicalFile(File(other))
            parent.isDirectory && parent.path != candidatePath && candidatePath.startsWith(parent.path + File.separator)
        }
    }

    private fun runTask(message: Int, block: () -> Unit) {
        if (busy) return
        setBusy(true)
        statusView.setText(message)
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
        statusView.text = getString(R.string.vpk_progress, current, total, path)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        statusArea.visibility = if (value) View.VISIBLE else View.INVISIBLE
        progress.progress = 0
        updateFooter()
    }

    private fun canonicalFile(file: File): File = try { file.canonicalFile } catch (_: Throwable) { file.absoluteFile }

    private fun File.isSupportedArchive(): Boolean = extension.equals("vpk", true) || extension.equals("gma", true)

    private fun formatSize(size: Long): String = when {
        size >= 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f GiB", size / (1024.0 * 1024.0 * 1024.0))
        size >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f MiB", size / (1024.0 * 1024.0))
        size >= 1024L -> String.format(Locale.getDefault(), "%.2f KiB", size / 1024.0)
        else -> "$size B"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun animateDirectory(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationX = dp(12).toFloat()
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
    private fun applyOpenTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }
}
