package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.HapticFeedbackConstants
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
import me.nillerusr.md3.Md3Motion
import me.nillerusr.md3.Md3Theme
import me.nillerusr.vpk.VpkWriter

class VpkToolActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val selected = linkedSetOf<String>()
    private val scrollPositions = mutableMapOf<String, Int>()
    private var pendingFiles: List<File> = emptyList()
    private var pendingMove = false
    private var busy = false
    private var directoryPicker = false
    private var selectionMode = false
    private var animateSelectionIndicators = false
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
    private lateinit var selectAll: CheckBox
    private lateinit var pasteActions: View
    private var updatingSelectAll = false
    private lateinit var copyButton: Button
    private lateinit var moveButton: Button
    private lateinit var packButton: Button
    private lateinit var deleteButton: Button

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
        selectAll = findViewById(R.id.vpk_manager_select_all)
        pasteActions = findViewById(R.id.vpk_manager_paste_actions)
        directoryPicker = intent.getBooleanExtra(EXTRA_PICK_DIRECTORY, false)
        findViewById<ImageButton>(R.id.md3_button_back).setOnClickListener { navigateBack() }
        copyButton = findViewById(R.id.vpk_manager_copy)
        moveButton = findViewById(R.id.vpk_manager_move)
        packButton = findViewById(R.id.vpk_manager_pack)
        deleteButton = findViewById(R.id.vpk_manager_delete)
        copyButton.setOnClickListener { beginTransfer(false) }
        moveButton.setOnClickListener { beginTransfer(true) }
        packButton.setOnClickListener { choosePackVersion() }
        deleteButton.setOnClickListener { confirmDelete() }
        findViewById<Button>(R.id.vpk_manager_cancel).setOnClickListener { cancelTransfer() }
        findViewById<Button>(R.id.vpk_manager_paste).setOnClickListener { pasteHere() }
        selectAll.setOnCheckedChangeListener { _, checked ->
            if (updatingSelectAll || directoryPicker || busy) return@setOnCheckedChangeListener
            if (checked) selected.addAll(currentSelectablePaths()) else selected.clear()
            renderDirectory(scroll.scrollY)
        }
        if (directoryPicker) {
            findViewById<TextView>(R.id.vpk_manager_title).setText(R.string.vpk_choose_destination)
            findViewById<Button>(R.id.vpk_manager_cancel).setOnClickListener { finish() }
            findViewById<Button>(R.id.vpk_manager_paste).apply {
                setText(R.string.vpk_select_directory)
                setOnClickListener { returnSelectedDirectory() }
            }
        }

        applyExpressiveMotion()

        val requestedStart = intent.getStringExtra(EXTRA_START_DIRECTORY)?.let(::File)
        val start = requestedStart?.takeIf { it.isDirectory && it.canRead() }
            ?: File(LauncherActivity.getDefaultDir()).takeIf { it.isDirectory && it.canRead() }
            ?: Environment.getExternalStorageDirectory()
        showDirectory(start)
    }

    /** 顶栏与底部操作条接入 Expressive 弹簧手感。 */
    private fun applyExpressiveMotion() {
        try {
            Md3Motion.attachPressBounce(
                findViewById(R.id.md3_button_back),
                copyButton, moveButton, packButton, deleteButton,
                findViewById(R.id.vpk_manager_cancel),
                findViewById(R.id.vpk_manager_paste)
            )
            Md3Motion.enterStaggered(findViewById(R.id.vpk_manager_root), 40L, 20f)
        } catch (_: Throwable) {
        }
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
        if (!directoryPicker && selectionMode) {
            selected.clear()
            selectionMode = false
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
        selectionMode = false
        renderDirectory(scrollPositions[canonical.path] ?: 0, animate)
    }

    private fun renderDirectory(restoreY: Int = scroll.scrollY, animate: Boolean = false, revealPath: String? = null) {
        pathView.text = currentDirectory.path
        body.removeAllViews()
        val children = currentDirectory.listFiles()?.filter { it.canRead() }?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }
        ).orEmpty()
        currentDirectory.parentFile?.takeIf { it.canRead() }?.let(::addParentEntry)
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
        scroll.post {
            scroll.scrollTo(0, restoreY)
            revealPath?.let(::revealEntry)
        }
        if (animate) animateDirectory(body)
    }

    private fun addParentEntry(parent: File) {
        val row = layoutInflater.inflate(R.layout.vpk_file_picker_entry, body, false)
        bindPressAnimation(row)
        row.findViewById<View>(R.id.vpk_picker_icon_container).tag = "folder_container"
        row.findViewById<ImageView>(R.id.vpk_picker_icon).apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.ic_vpk_folder)
            tag = "folder_icon"
        }
        row.findViewById<ImageView>(R.id.vpk_picker_trailing).visibility = View.VISIBLE
        row.findViewById<View>(R.id.vpk_picker_selection).visibility = View.GONE
        row.findViewById<TextView>(R.id.vpk_picker_name).text = ".."
        row.findViewById<TextView>(R.id.vpk_picker_detail).setText(R.string.vpk_manager_folder)
        row.setOnClickListener {
            if (!busy && !selectionMode) showDirectory(parent, true)
        }
        body.addView(row)
    }

    private fun addEntry(file: File) {
        val row = layoutInflater.inflate(R.layout.vpk_file_picker_entry, body, false)
        bindPressAnimation(row)
        val iconContainer = row.findViewById<View>(R.id.vpk_picker_icon_container)
        val icon = row.findViewById<ImageView>(R.id.vpk_picker_icon)
        val trailing = row.findViewById<ImageView>(R.id.vpk_picker_trailing)
        val selectionIndicator = row.findViewById<View>(R.id.vpk_picker_selection)
        val selectionCheck = row.findViewById<ImageView>(R.id.vpk_picker_selection_check)
        val name = row.findViewById<TextView>(R.id.vpk_picker_name)
        val detail = row.findViewById<TextView>(R.id.vpk_picker_detail)
        val path = canonicalFile(file).path
        row.setTag(R.id.vpk_entry_path, path)
        val selecting = !directoryPicker && selectionMode
        selectionIndicator.visibility = if (selecting) View.VISIBLE else View.GONE
        icon.visibility = View.VISIBLE
        icon.setImageResource(when {
            file.isDirectory -> R.drawable.ic_vpk_folder
            file.extension.equals("vpk", true) -> R.drawable.ic_vpk_archive
            file.extension.equals("gma", true) -> R.drawable.ic_gma_archive
            else -> R.drawable.ic_vpk_file
        })
        val iconRole = when {
            file.isDirectory -> "folder"
            file.extension.equals("vpk", true) -> "vpk_archive"
            file.extension.equals("gma", true) -> "gma_archive"
            else -> "file"
        }
        iconContainer.tag = "${iconRole}_container"
        icon.tag = "${iconRole}_icon"
        trailing.visibility = if (!selecting && file.isDirectory) View.VISIBLE else View.GONE
        val checked = path in selected
        selectionIndicator.tag = if (checked) "selection_checked" else "selection_unchecked"
        selectionCheck.visibility = if (checked) View.VISIBLE else View.INVISIBLE
        if (selecting && animateSelectionIndicators) {
            Md3Motion.morphIn(selectionIndicator)
        }
        name.text = file.name
        val sizeText = when {
            file.isDirectory -> getString(R.string.vpk_manager_folder)
            file.extension.equals("vpk", true) -> getString(R.string.vpk_manager_archive, formatSize(file.length()))
            file.extension.equals("gma", true) -> getString(R.string.gma_manager_archive, formatSize(file.length()))
            else -> formatSize(file.length())
        }
        detail.text = getString(R.string.vpk_manager_detail_with_date, sizeText, formatDate(file.lastModified()))
        row.setOnLongClickListener {
            if (!directoryPicker && !busy && pendingFiles.isEmpty()) {
                row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val enteringSelectionMode = !selectionMode
                selectionMode = true
                selected += path
                animateSelectionIndicators = enteringSelectionMode
                renderDirectory(scroll.scrollY, revealPath = path)
                animateSelectionIndicators = false
            }
            true
        }
        row.setOnClickListener {
            when {
                busy -> Unit
                directoryPicker && file.isDirectory -> showDirectory(file, true)
                directoryPicker -> Unit
                selectionMode -> {
                    if (path in selected) selected -= path else selected += path
                    renderDirectory(scroll.scrollY, revealPath = path)
                }
                file.isDirectory -> showDirectory(file, true)
                file.isSupportedArchive() -> {
                    startActivityForResult(
                        Intent(this, VpkArchiveActivity::class.java).putExtra(VpkArchiveActivity.EXTRA_ARCHIVE_PATH, file.path),
                        REQUEST_ARCHIVE
                    )
                    applyOpenTransition()
                }
            }
        }
        body.addView(row)
    }

    @Deprecated("Uses the classic Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ARCHIVE && resultCode == RESULT_OK &&
            data?.getBooleanExtra(VpkArchiveActivity.EXTRA_EXTRACTED, false) == true
        ) {
            renderDirectory()
        }
    }

    private fun updateFooter() {
        if (directoryPicker) {
            footer.visibility = View.VISIBLE
            selectionActions.visibility = View.GONE
            selectAll.visibility = View.GONE
            pasteActions.visibility = View.VISIBLE
            selectedView.setText(R.string.vpk_choose_destination_prompt)
            return
        }
        val transferring = pendingFiles.isNotEmpty()
        footer.visibility = if (selectionMode || transferring) View.VISIBLE else View.GONE
        selectionActions.visibility = if (selectionMode && !transferring) View.VISIBLE else View.GONE
        selectAll.visibility = if (selectionMode && !transferring) View.VISIBLE else View.GONE
        pasteActions.visibility = if (transferring) View.VISIBLE else View.GONE
        val hasSelection = selected.isNotEmpty()
        copyButton.isEnabled = hasSelection
        moveButton.isEnabled = hasSelection
        packButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection
        val selectable = currentSelectablePaths()
        updatingSelectAll = true
        selectAll.isChecked = selectable.isNotEmpty() && selectable.all(selected::contains)
        updatingSelectAll = false
        selectedView.text = if (transferring) {
            getString(if (pendingMove) R.string.vpk_move_destination else R.string.vpk_copy_destination, pendingFiles.size)
        } else {
            getString(R.string.vpk_picker_selected, selected.size)
        }
    }

    private fun currentSelectablePaths(): Set<String> = currentDirectory.listFiles()
        ?.asSequence()
        ?.filter { it.canRead() }
        ?.map { canonicalFile(it).path }
        ?.toSet()
        .orEmpty()

    private fun revealEntry(path: String) {
        val row = (0 until body.childCount)
            .map(body::getChildAt)
            .firstOrNull { it.getTag(R.id.vpk_entry_path) == path }
            ?: return
        scroll.requestChildRectangleOnScreen(row, Rect(0, 0, row.width, row.height), false)
    }

    private fun returnSelectedDirectory() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_DIRECTORY, currentDirectory.path))
        finish()
    }

    private fun beginTransfer(move: Boolean) {
        pendingFiles = selectedFiles()
        pendingMove = move
        selected.clear()
        selectionMode = false
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
        try {
            validateTransfer(sources, currentDirectory)
        } catch (error: Throwable) {
            Toast.makeText(this, error.message ?: error.toString(), Toast.LENGTH_LONG).show()
            return
        }
        runTask(if (move) R.string.vpk_moving else R.string.vpk_copying) {
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
            require(source.parentFile?.let(::canonicalFile)?.path != destinationPath) { getString(R.string.vpk_error_same_directory) }
            require(!File(destination, source.name).exists()) { getString(R.string.vpk_error_already_exists) }
            require(!source.isDirectory || !destinationPath.startsWith(sourcePath + File.separator)) {
                getString(R.string.vpk_error_folder_inside_itself)
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
                        selectionMode = false
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
                        selectionMode = false
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
        statusArea.visibility = if (value) View.VISIBLE else View.GONE
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

    private fun formatDate(time: Long): String =
        try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(time))
        } catch (_: Throwable) {
            ""
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun animateDirectory(view: View) = Md3Motion.enterItem(view)

    private fun bindPressAnimation(view: View) = Md3Motion.attachPressBounce(view, 0.97f)

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
        private const val REQUEST_ARCHIVE = 1002
        const val EXTRA_PICK_DIRECTORY = "vpk_pick_directory"
        const val EXTRA_START_DIRECTORY = "vpk_start_directory"
        const val EXTRA_SELECTED_DIRECTORY = "vpk_selected_directory"
    }
}
