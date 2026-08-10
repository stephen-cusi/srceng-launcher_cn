package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.valvesoftware.source.R
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import me.nillerusr.md3.Md3Motion
import me.nillerusr.md3.Md3Theme
import me.nillerusr.gma.GmaArchive
import me.nillerusr.vpk.VpkArchive

class VpkArchiveActivity : Activity() {
    private data class Item(val name: String, val path: String, val directory: Boolean, val size: Long)

    private val executor = Executors.newSingleThreadExecutor()
    private val selected = linkedSetOf<String>()
    private var selectionMode = false
    private var animateSelectionIndicators = false
    private val scrollPositions = mutableMapOf<String, Int>()
    private data class ArchiveEntry(val path: String, val size: Long)

    private var vpkArchive: VpkArchive? = null
    private var gmaArchive: GmaArchive? = null
    private var currentPath = ""
    private var busy = false
    private var pendingExtractionPaths: Set<String>? = null
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

        applyExpressiveMotion()

        val path = intent.getStringExtra(EXTRA_ARCHIVE_PATH) ?: return finish()
        archiveFile = File(path)
        title.text = archiveFile.name
        openArchive()
    }

    /** 顶栏与提取按钮接入 Expressive 弹簧手感。 */
    private fun applyExpressiveMotion() {
        try {
            Md3Motion.attachPressBounce(findViewById(R.id.md3_button_back), extractButton)
            Md3Motion.enterStaggered(findViewById(R.id.vpk_archive_root), 40L, 20f)
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

    private fun openArchive() = runTask(R.string.vpk_reading) {
        if (archiveFile.extension.equals("gma", true)) gmaArchive = GmaArchive.open(archiveFile)
        else vpkArchive = VpkArchive.open(archiveFile)
        runOnUiThread { renderDirectory(0, true) }
    }

    private fun navigateBack() {
        if (busy) return
        if (selectionMode) {
            selected.clear()
            selectionMode = false
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
        if (currentPath.isNotEmpty()) {
            val parentPath = currentPath.substringBeforeLast('/', "")
            val parentRow = layoutInflater.inflate(R.layout.vpk_file_picker_entry, body, false)
            bindPressAnimation(parentRow)
            parentRow.findViewById<View>(R.id.vpk_picker_icon_container).tag = "folder_container"
            parentRow.findViewById<ImageView>(R.id.vpk_picker_icon).apply {
                visibility = View.VISIBLE
                setImageResource(R.drawable.ic_vpk_folder)
                tag = "folder_icon"
            }
            parentRow.findViewById<ImageView>(R.id.vpk_picker_trailing).visibility = View.VISIBLE
            parentRow.findViewById<View>(R.id.vpk_picker_selection).visibility = View.GONE
            parentRow.findViewById<TextView>(R.id.vpk_picker_name).text = ".."
            parentRow.findViewById<TextView>(R.id.vpk_picker_detail).setText(R.string.vpk_archive_folder)
            parentRow.setOnClickListener {
                if (!busy && !selectionMode) {
                    scrollPositions[currentPath] = scroll.scrollY
                    currentPath = parentPath
                    renderDirectory(scrollPositions[currentPath] ?: 0, true)
                }
            }
            body.addView(parentRow)
        }
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
        val icon = row.findViewById<ImageView>(R.id.vpk_picker_icon)
        val trailing = row.findViewById<ImageView>(R.id.vpk_picker_trailing)
        val selectionIndicator = row.findViewById<View>(R.id.vpk_picker_selection)
        val selectionCheck = row.findViewById<ImageView>(R.id.vpk_picker_selection_check)
        val name = row.findViewById<TextView>(R.id.vpk_picker_name)
        val detail = row.findViewById<TextView>(R.id.vpk_picker_detail)
        val selecting = selectionMode
        selectionIndicator.visibility = if (selecting) View.VISIBLE else View.GONE
        icon.visibility = View.VISIBLE
        trailing.visibility = if (!selecting && item.directory) View.VISIBLE else View.GONE
        icon.setImageResource(if (item.directory) R.drawable.ic_vpk_folder else R.drawable.ic_vpk_file)
        val checked = item.path in selected
        selectionIndicator.tag = if (checked) "selection_checked" else "selection_unchecked"
        selectionCheck.visibility = if (checked) View.VISIBLE else View.INVISIBLE
        if (selecting && animateSelectionIndicators) {
            Md3Motion.morphIn(selectionIndicator)
        }
        name.text = item.name
        detail.text = if (item.directory) getString(R.string.vpk_archive_folder) else formatSize(item.size)
        row.setOnLongClickListener {
            if (!busy) {
                row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val enteringSelectionMode = !selectionMode
                selectionMode = true
                selected += item.path
                animateSelectionIndicators = enteringSelectionMode
                renderDirectory(scroll.scrollY)
                animateSelectionIndicators = false
            }
            true
        }
        row.setOnClickListener {
            when {
                busy -> Unit
                selectionMode -> {
                    if (item.path in selected) selected -= item.path else selected += item.path
                    renderDirectory(scroll.scrollY)
                }
                item.directory -> {
                    scrollPositions[currentPath] = scroll.scrollY
                    currentPath = item.path
                    renderDirectory(scrollPositions[currentPath] ?: 0, true)
                }
                else -> previewFile(item)
            }
        }
        body.addView(row)
    }

    private fun prepareExtraction() {
        val paths = selected.takeIf { it.isNotEmpty() }?.toSet()
        val parent = archiveFile.parentFile ?: return
        val choices = arrayOf(
            getString(R.string.vpk_extract_current_directory),
            getString(R.string.vpk_extract_named_directory, archiveBaseName()),
            getString(R.string.vpk_extract_choose_directory)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vpk_extract_destination_title)
            .setItems(choices) { _, choice ->
                when (choice) {
                    0 -> resolveConflicts(paths, parent)
                    1 -> resolveConflicts(paths, File(parent, archiveBaseName()))
                    else -> {
                        pendingExtractionPaths = paths
                        startActivityForResult(
                            Intent(this, VpkToolActivity::class.java)
                                .putExtra(VpkToolActivity.EXTRA_PICK_DIRECTORY, true)
                                .putExtra(VpkToolActivity.EXTRA_START_DIRECTORY, parent.path),
                            REQUEST_EXTRACTION_DIRECTORY
                        )
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Deprecated("Uses the classic activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXTRACTION_DIRECTORY && resultCode == RESULT_OK) {
            val destination = data?.getStringExtra(VpkToolActivity.EXTRA_SELECTED_DIRECTORY)?.let(::File) ?: return
            val paths = pendingExtractionPaths
            pendingExtractionPaths = null
            resolveConflicts(paths, destination)
        }
    }

    private fun resolveConflicts(paths: Set<String>?, destination: File) {
        val conflicts = archiveEntries().filter { entry ->
            (paths == null || paths.any { entry.path == it || entry.path.startsWith("$it/") }) &&
                File(destination, entry.path).exists()
        }
        if (conflicts.isEmpty()) {
            extract(paths, destination, emptySet(), emptySet())
            return
        }
        val overwrite = linkedSetOf<String>()
        val skipped = linkedSetOf<String>()
        fun ask(index: Int) {
            if (index >= conflicts.size) {
                extract(paths, destination, overwrite, skipped)
                return
            }
            val applyToAll = CheckBox(this).apply {
                setText(R.string.vpk_conflict_apply_all)
                setPadding(dp(20), dp(4), dp(20), dp(4))
            }
            val container = FrameLayout(this).apply { addView(applyToAll) }
            val conflict = conflicts[index]
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.vpk_conflict_title)
                .setMessage(getString(R.string.vpk_conflict_message, conflict.path))
                .setView(container)
                .setNegativeButton(R.string.vpk_skip, null)
                .setPositiveButton(R.string.vpk_overwrite, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    if (applyToAll.isChecked) {
                        conflicts.drop(index).forEach { skipped += it.path }
                        dialog.dismiss()
                        extract(paths, destination, overwrite, skipped)
                    } else {
                        skipped += conflict.path
                        dialog.dismiss()
                        ask(index + 1)
                    }
                }
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (applyToAll.isChecked) {
                        conflicts.drop(index).forEach { overwrite += it.path }
                        dialog.dismiss()
                        extract(paths, destination, overwrite, skipped)
                    } else {
                        overwrite += conflict.path
                        dialog.dismiss()
                        ask(index + 1)
                    }
                }
            }
            dialog.show()
        }
        ask(0)
    }

    private fun extract(paths: Set<String>?, destination: File, overwrite: Set<String>, skipped: Set<String>) = runTask(R.string.vpk_extracting) {
        when {
            vpkArchive != null -> vpkArchive!!.extract(destination, paths, overwrite, skipped, ::updateProgress)
            gmaArchive != null -> gmaArchive!!.extract(destination, paths, overwrite, skipped, ::updateProgress)
            else -> error(getString(R.string.vpk_no_archive))
        }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_EXTRACTED, true))
        runOnUiThread {
            selected.clear()
            selectionMode = false
            Toast.makeText(this, getString(R.string.vpk_extract_done_path, destination.path), Toast.LENGTH_LONG).show()
            renderDirectory()
        }
    }

    private fun previewFile(item: Item) {
        val lowerName = item.name.lowercase(Locale.ROOT)
        val isText = TEXT_PREVIEW_EXTENSIONS.any { lowerName.endsWith(it) }
        val isAudio = AUDIO_PREVIEW_EXTENSIONS.any { lowerName.endsWith(it) }
        if (!isText && !isAudio) return
        if (isText) {
            runTask(R.string.vpk_preview_reading) {
                val bytes = readEntryBytes(item.path)
                runOnUiThread {
                    if (bytes == null) {
                        Toast.makeText(this, R.string.vpk_preview_unreadable, Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    showTextPreview(item.name, bytes)
                }
            }
        } else {
            try {
                val playlist = audioPlaylistInCurrentDirectory()
                if (playlist.isEmpty()) return
                val startIndex = playlist.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
                showAudioPreview(playlist, startIndex)
            } catch (error: Throwable) {
                Toast.makeText(this, getString(R.string.vpk_preview_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readEntryBytes(path: String): ByteArray? = when {
        vpkArchive != null -> vpkArchive!!.read(path)
        gmaArchive != null -> gmaArchive!!.read(path)
        else -> null
    }

    private fun audioPlaylistInCurrentDirectory(): List<Item> {
        val prefix = if (currentPath.isEmpty()) "" else "$currentPath/"
        val items = linkedMapOf<String, Item>()
        archiveEntries().forEach { entry ->
            if (!entry.path.startsWith(prefix)) return@forEach
            val remaining = entry.path.removePrefix(prefix)
            val name = remaining.substringBefore('/')
            if (name.isEmpty() || '/' in remaining) return@forEach
            if (!AUDIO_PREVIEW_EXTENSIONS.any { name.lowercase(Locale.ROOT).endsWith(it) }) return@forEach
            items[name] = Item(name, prefix + name, false, entry.size)
        }
        return items.values.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun showTextPreview(name: String, bytes: ByteArray) {
        var text = String(bytes, Charsets.UTF_8)
        if (!containsReplacementChars(text)) {
            text = String(bytes, Charsets.ISO_8859_1).replace(Regex("[^\\x20-\\x7E\\x0A\\x0D\\x09]"), "\uFFFD")
        }
        if (text.length > MAX_TEXT_PREVIEW_CHARS) text = text.substring(0, MAX_TEXT_PREVIEW_CHARS) + "\n…"
        val content = TextView(this).apply {
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(20), dp(4), dp(20), dp(12))
            setText(text)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show().also { Md3Theme.applyDialog(it) }
    }

    private fun containsReplacementChars(text: String): Boolean =
        text.indexOf('\uFFFD') >= 0 || text.indexOf('\u0000') >= 0

    private fun showAudioPreview(playlist: List<Item>, startIndex: Int) {
        AudioPreviewController(playlist, startIndex).show()
    }

    private inner class AudioPreviewController(
        private val playlist: List<Item>,
        private var currentIndex: Int
    ) {
        private val player = MediaPlayer()
        private val handler = Handler(Looper.getMainLooper())
        private var prepared = false
        private var loading = false
        private var errorCheckRunnable: Runnable? = null
        private lateinit var autoplaySwitch: com.google.android.material.materialswitch.MaterialSwitch
        private lateinit var dialog: androidx.appcompat.app.AlertDialog
        private lateinit var seekBar: SeekBar
        private lateinit var positionText: TextView
        private lateinit var durationText: TextView
        private lateinit var playButton: ImageButton
        private lateinit var statusText: TextView

        private val progressRunnable = object : Runnable {
            override fun run() {
                if (prepared && player.isPlaying && player.duration > 0) {
                    seekBar.progress = (player.currentPosition * 1000L / player.duration).toInt()
                    positionText.text = formatDuration(player.currentPosition.toLong())
                }
                handler.postDelayed(this, 500L)
            }
        }

        fun show() {
            dialog = MaterialAlertDialogBuilder(this@VpkArchiveActivity)
                .setTitle(playlist[currentIndex].name)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener {
                    handler.removeCallbacksAndMessages(null)
                    player.release()
                    cacheDir.listFiles { it.name.startsWith("preview_") }?.forEach { it.delete() }
                }
                .create()
            val content = layoutInflater.inflate(R.layout.dialog_audio_preview, null)
            seekBar = content.findViewById(R.id.audio_preview_seek)
            positionText = content.findViewById(R.id.audio_preview_position)
            durationText = content.findViewById(R.id.audio_preview_duration)
            playButton = content.findViewById(R.id.audio_preview_play)
            statusText = content.findViewById(R.id.audio_preview_status)
            autoplaySwitch = content.findViewById(R.id.audio_preview_autoplay)
            autoplaySwitch.isChecked = getSharedPreferences("mod", 0).getBoolean(PREF_AUTOPLAY, true)
            autoplaySwitch.setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("mod", 0).edit().putBoolean(PREF_AUTOPLAY, checked).apply()
            }
            val previousButton = content.findViewById<ImageButton>(R.id.audio_preview_previous)
            val nextButton = content.findViewById<ImageButton>(R.id.audio_preview_next)
            dialog.setView(content)
            dialog.window?.setWindowAnimations(R.style.SrcEng_DialogPreview)
            dialog.setOnShowListener {
                Md3Theme.applyDialog(dialog)
                styleSeekBar()
                playButton.setOnClickListener { togglePlay() }
                previousButton.setOnClickListener { playSibling(currentIndex - 1) }
                nextButton.setOnClickListener { playSibling(currentIndex + 1) }
                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser && prepared && player.duration > 0) {
                            val target = player.duration * progress.toLong() / 1000L
                            player.seekTo(target.toInt())
                            positionText.text = formatDuration(target)
                        }
                    }

                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                })
                player.setOnCompletionListener { onTrackEnded() }
                player.setOnErrorListener { _, _, _ ->
                    // Some codecs (notably certain WAV variants) report a transient
                    // error during prepare yet still play back normally afterwards.
                    // Only surface a failure when the player is no longer running.
                    errorCheckRunnable?.let { handler.removeCallbacks(it) }
                    val check = Runnable {
                        errorCheckRunnable = null
                        if (!player.isPlaying && !prepared) {
                            onPlaybackFailed("media")
                        }
                    }
                    errorCheckRunnable = check
                    handler.postDelayed(check, 500)
                    true
                }
                updateControls()
                durationText.text = try {
                    if (prepared) formatDuration(player.duration.toLong()) else formatDuration(0)
                } catch (_: Throwable) {
                    formatDuration(0)
                }
                handler.post(progressRunnable)
            }
            dialog.show()
        }

        private fun togglePlay() {
            if (loading) return
            try {
                when {
                    player.isPlaying -> {
                        player.pause()
                        statusText.setText(R.string.vpk_preview_paused)
                    }
                    prepared -> {
                        player.start()
                        statusText.setText(R.string.vpk_preview_playing)
                    }
                    else -> loadCurrent(true)
                }
                updateControls()
            } catch (error: Throwable) {
                statusText.text = getString(R.string.vpk_preview_failed, error.message ?: "")
            }
        }

        private fun loadCurrent(autoplay: Boolean) {
            errorCheckRunnable?.let { handler.removeCallbacks(it) }
            errorCheckRunnable = null
            val item = playlist[currentIndex]
            val extension = item.name.substringAfterLast('.', "wav")
            val file = File(cacheDir, "preview_$currentIndex.$extension")
            loading = true
            statusText.setText(R.string.vpk_preview_reading)
            executor.execute {
                try {
                    cacheDir.listFiles { it.name.startsWith("preview_") && it != file }?.forEach { it.delete() }
                    val bytes = readEntryBytes(item.path)
                        ?: error(getString(R.string.vpk_preview_unreadable))
                    file.writeBytes(bytes)
                    runOnUiThread {
                        try {
                            prepared = false
                            player.reset()
                            player.setDataSource(file.path)
                            player.setOnPreparedListener {
                                try {
                                    errorCheckRunnable?.let { handler.removeCallbacks(it) }
                                    errorCheckRunnable = null
                                    loading = false
                                    prepared = true
                                    if (dialog.isShowing) dialog.setTitle(item.name)
                                    durationText.text = formatDuration(player.duration.toLong())
                                    if (autoplay) {
                                        statusText.setText(R.string.vpk_preview_playing)
                                        player.start()
                                    } else {
                                        statusText.setText(R.string.vpk_preview_paused)
                                        seekBar.progress = 0
                                        positionText.text = formatDuration(0)
                                    }
                                    updateControls()
                                } catch (error: Throwable) {
                                    onPlaybackFailed(error.message ?: "")
                                }
                            }
                            player.setOnErrorListener { _, _, _ ->
                                handler.postDelayed({
                                    if (!player.isPlaying && !prepared) {
                                        onPlaybackFailed("media")
                                    }
                                }, 300)
                                true
                            }
                            player.prepareAsync()
                        } catch (error: Throwable) {
                            onPlaybackFailed(error.message ?: "")
                        }
                    }
                } catch (error: Throwable) {
                    runOnUiThread { onPlaybackFailed(error.message ?: "") }
                }
            }
        }

        private fun onPlaybackFailed(message: String) {
            loading = false
            prepared = false
            statusText.text = getString(R.string.vpk_preview_failed, message)
            playButton.setImageResource(R.drawable.ic_play)
            seekBar.progress = 0
            updateControls()
        }

        private fun playSibling(rawIndex: Int) {
            if (loading || playlist.size <= 1) return
            var index = rawIndex
            if (index < 0) index = playlist.size - 1
            if (index >= playlist.size) index = 0
            if (index == currentIndex) return
            currentIndex = index
            try {
                if (prepared || player.isPlaying) player.pause()
            } catch (_: Throwable) {
            }
            prepared = false
            loadCurrent(autoplaySwitch.isChecked)
        }

        private fun onTrackEnded() {
            prepared = false
            seekBar.progress = 0
            positionText.text = formatDuration(0)
            playButton.setImageResource(R.drawable.ic_play)
            statusText.setText(R.string.vpk_preview_tap_to_play)
        }

        private fun updateControls() {
            playButton.setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }

        private fun styleSeekBar() {
            try {
                val color = resources.getColor(R.color.md3_primary, theme)
                seekBar.progressTintList = android.content.res.ColorStateList.valueOf(color)
                seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(color)
                seekBar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                    (color and 0x00ffffff) or (0x33000000.toInt())
                )
            } catch (_: Throwable) {
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(Locale.ROOT, minutes, seconds)
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

    private fun animateDirectory(view: View) = Md3Motion.enterItem(view)

    private fun bindPressAnimation(view: View) = Md3Motion.attachPressBounce(view, 0.97f)

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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        vpkArchive?.close()
        gmaArchive?.close()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ARCHIVE_PATH = "vpk_archive_path"
        const val EXTRA_EXTRACTED = "vpk_extracted"
        private const val REQUEST_EXTRACTION_DIRECTORY = 1001
        private const val PREF_AUTOPLAY = "vpk_audio_autoplay"
        private const val MAX_TEXT_PREVIEW_CHARS = 200_000
        private val TEXT_PREVIEW_EXTENSIONS = arrayOf(
            ".txt", ".cfg", ".vdf", ".rc", ".nut", ".lst", ".vmt", ".res", ".kv",
            ".vhv", ".game", ".snd", ".kv3", ".json", ".xml", ".log"
        )
        private val AUDIO_PREVIEW_EXTENSIONS = arrayOf(".wav", ".mp3", ".ogg")
    }
}
