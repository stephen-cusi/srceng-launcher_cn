package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.valvesoftware.source.R
import java.io.File
import java.io.RandomAccessFile
import me.nillerusr.md3.Md3Theme
import me.nillerusr.md3.Md3Tokens

class EngineLogActivity : Activity() {
    private enum class Level { ERROR, WARNING, INFO }

    private data class Entry(val lineNumber: Int, val text: String, val level: Level)

    private lateinit var predictiveBack: PredictiveBackController
    private lateinit var metaView: TextView
    private lateinit var statusView: TextView
    private lateinit var textView: EngineLogTextView
    private lateinit var scroll: ScrollView
    private lateinit var stage: android.widget.FrameLayout
    private lateinit var horizontalScroll: HorizontalScrollView

    private var logFile: File? = null
    private val entries = ArrayList<Entry>()
    private val pending = StringBuilder()
    private var lastReadOffset = 0L
    private var nextLineNumber = 1
    private var selectedLevel: Level? = null
    private var refreshing = false
    private var followTail = false
    private var fileMissing = false
    private var highlightEnabled = true
    private var wordWrapEnabled = true
    private var visibleLineNumbers = IntArray(0)
    private var visibleLineOffsets = IntArray(0)
    private var jumpHighlightLine: Int? = null
    private var pendingScrollLine: Int? = null
    private var scrollPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_INTERVAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Md3Theme.applyBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_engine_log)
        Md3Theme.applyAfterSetContentView(this)
        predictiveBack = PredictiveBackController(this) { finish() }
        predictiveBack.sync()

        metaView = findViewById(R.id.engine_log_meta)
        statusView = findViewById(R.id.engine_log_status)
        textView = findViewById(R.id.engine_log_text)
        scroll = findViewById(R.id.engine_log_scroll)
        stage = findViewById(R.id.engine_log_stage)
        horizontalScroll = findViewById(R.id.engine_log_horizontal_scroll)

        val prefs = getSharedPreferences(PREFS_NAME, 0)
        highlightEnabled = prefs.getBoolean(PREF_HIGHLIGHT, true)
        wordWrapEnabled = prefs.getBoolean(PREF_WORD_WRAP, true)

        findViewById<ImageButton>(R.id.engine_log_menu).setOnClickListener(::showMainMenu)
        findViewById<ImageButton>(R.id.engine_log_filter).setOnClickListener(::showFilterMenu)
        findViewById<ImageButton>(R.id.md3_button_back).setOnClickListener { finish() }
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val content = scroll.getChildAt(0)
            val max = if (content == null) 0 else content.height - scroll.height
            followTail = scrollY >= max - SCROLL_FOLLOW_MARGIN
        }

        applyEditorPalette()
        applyWordWrap()
        locateLogFile()
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

    override fun onResume() {
        super.onResume()
        if (::predictiveBack.isInitialized) predictiveBack.sync()
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        cancelPendingScroll()
        if (::predictiveBack.isInitialized) predictiveBack.release()
        super.onDestroy()
    }

    private fun showMainMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_HIGHLIGHT, 0, R.string.engine_log_highlight).apply {
                isCheckable = true
                isChecked = highlightEnabled
            }
            menu.add(0, MENU_CLEAR, 1, R.string.engine_log_clear)
            menu.add(0, MENU_GO_TO_LINE, 2, R.string.engine_log_go_to_line)
            menu.add(0, MENU_WORD_WRAP, 3, R.string.engine_log_word_wrap).apply {
                isCheckable = true
                isChecked = wordWrapEnabled
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_HIGHLIGHT -> {
                        highlightEnabled = !highlightEnabled
                        saveDisplayPrefs()
                        rebuild()
                        true
                    }
                    MENU_CLEAR -> {
                        confirmClearLog()
                        true
                    }
                    MENU_GO_TO_LINE -> {
                        showGoToLineDialog()
                        true
                    }
                    MENU_WORD_WRAP -> {
                        val anchorLine = currentTopLine()
                        wordWrapEnabled = !wordWrapEnabled
                        saveDisplayPrefs()
                        rebuild(anchorLine)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showFilterMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(FILTER_GROUP, FILTER_ALL, 0, R.string.engine_log_filter_all).isChecked = selectedLevel == null
            menu.add(FILTER_GROUP, FILTER_WARNING, 1, R.string.engine_log_filter_warning).isChecked = selectedLevel == Level.WARNING
            menu.add(FILTER_GROUP, FILTER_INFO, 2, R.string.engine_log_filter_info).isChecked = selectedLevel == Level.INFO
            menu.add(FILTER_GROUP, FILTER_ERROR, 3, R.string.engine_log_filter_error).isChecked = selectedLevel == Level.ERROR
            menu.setGroupCheckable(FILTER_GROUP, true, true)
            setOnMenuItemClickListener { item ->
                selectedLevel = when (item.itemId) {
                    FILTER_WARNING -> Level.WARNING
                    FILTER_INFO -> Level.INFO
                    FILTER_ERROR -> Level.ERROR
                    else -> null
                }
                jumpHighlightLine = null
                rebuild()
                scroll.post { scroll.scrollTo(0, 0) }
                true
            }
            show()
        }
    }

    private fun confirmClearLog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.engine_log_clear)
            .setMessage(R.string.engine_log_clear_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.engine_log_clear) { _, _ -> clearLog() }
            .show().also(Md3Theme::applyDialog)
    }

    private fun clearLog() {
        val file = logFile ?: return
        try {
            RandomAccessFile(file, "rw").use { it.setLength(0) }
            forceReload()
            Toast.makeText(this, R.string.engine_log_cleared, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.engine_log_clear_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showGoToLineDialog() {
        val container = layoutInflater.inflate(R.layout.dialog_engine_log_go_to_line, null)
        val inputLayout = container.findViewById<TextInputLayout>(R.id.engine_log_line_input_layout)
        val input = container.findViewById<TextInputEditText>(R.id.engine_log_line_input)
        inputLayout.hint = getString(
            R.string.engine_log_line_range,
            entries.firstOrNull()?.lineNumber ?: 1,
            entries.lastOrNull()?.lineNumber ?: 1
        )
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.engine_log_go_to_line)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val line = input.text.toString().toIntOrNull()
                val first = entries.firstOrNull()?.lineNumber
                val last = entries.lastOrNull()?.lineNumber
                if (line == null || first == null || last == null || line !in first..last) {
                    inputLayout.error = getString(R.string.engine_log_invalid_line)
                    return@setOnClickListener
                }
                selectedLevel = null
                jumpHighlightLine = line
                dialog.dismiss()
                rebuild(line)
            }
        }
        dialog.show()
        Md3Theme.applyDialog(dialog)
    }

    private fun saveDisplayPrefs() {
        getSharedPreferences(PREFS_NAME, 0).edit()
            .putBoolean(PREF_HIGHLIGHT, highlightEnabled)
            .putBoolean(PREF_WORD_WRAP, wordWrapEnabled)
            .apply()
    }

    private fun applyWordWrap() {
        val parent = textView.parent
        if (wordWrapEnabled) {
            if (parent != stage) {
                horizontalScroll.removeView(textView)
                stage.addView(
                    textView,
                    android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            horizontalScroll.visibility = View.GONE
        } else {
            if (parent != horizontalScroll) {
                stage.removeView(textView)
                horizontalScroll.addView(
                    textView,
                    android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            horizontalScroll.visibility = View.VISIBLE
        }
        textView.setWordWrap(wordWrapEnabled)
    }

    private fun applyEditorPalette() {
        val tokens = Md3Theme.buildTokens(this)
        findViewById<View>(R.id.engine_log_file_bar).setBackgroundColor(tokens.surfaceContainerHigh)
        textView.setBackgroundColor(tokens.surfaceContainerLow)
        textView.setEditorPalette(tokens.surfaceContainerHigh, tokens.onSurfaceVariant, tokens.outlineVariant)
        val tint = ColorStateList.valueOf(tokens.onSurfaceVariant)
        findViewById<ImageButton>(R.id.md3_button_back).imageTintList = tint
        findViewById<ImageButton>(R.id.engine_log_filter).imageTintList = tint
        findViewById<ImageButton>(R.id.engine_log_menu).imageTintList = tint
    }

    private fun locateLogFile() {
        val prefs = getSharedPreferences("mod", 0)
        var gamePath = prefs.getString("gamepath", "").orEmpty()
        if (gamePath.isEmpty()) gamePath = LauncherActivity.getDefaultDir() + "/srceng"
        var candidate = File(gamePath, "engine.log")
        if (!candidate.isFile) {
            val fallback = File(LauncherActivity.getDefaultDir() + "/srceng", "engine.log")
            if (fallback.isFile) candidate = fallback
        }
        logFile = candidate
        findViewById<TextView>(R.id.engine_log_title).apply {
            text = candidate.name
            contentDescription = candidate.path
        }
        forceReload()
    }

    private fun forceReload() {
        lastReadOffset = 0L
        nextLineNumber = 1
        entries.clear()
        pending.setLength(0)
        refresh()
        rebuild()
    }

    private fun refresh() {
        val file = logFile ?: return
        if (refreshing) return
        refreshing = true
        var changed = false
        try {
            if (!file.isFile) {
                fileMissing = true
                updateStatus(0)
                return
            }
            fileMissing = false
            val length = file.length()
            if (length < lastReadOffset) {
                lastReadOffset = 0L
                nextLineNumber = 1
                entries.clear()
                pending.setLength(0)
                changed = true
            }
            if (length > lastReadOffset) {
                readRange(file, lastReadOffset, length)
                lastReadOffset = length
                changed = true
            }
        } catch (_: Throwable) {
        } finally {
            refreshing = false
        }
        if (changed) rebuild()
    }

    private fun readRange(file: File, start: Long, end: Long) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val count = (end - start).toInt()
            if (count <= 0) return
            val buffer = ByteArray(count)
            raf.readFully(buffer)
            parseText(String(buffer, Charsets.UTF_8))
        }
    }

    private fun parseText(text: String) {
        pending.append(text)
        while (true) {
            val index = pending.indexOf("\n")
            if (index < 0) break
            val line = pending.substring(0, index).removeSuffix("\r")
            pending.delete(0, index + 1)
            val level = if (!line.startsWith("[") && entries.isNotEmpty()) entries.last().level else classify(line)
            entries.add(Entry(nextLineNumber++, line, level))
        }
        if (entries.size > MAX_ENTRIES) entries.subList(0, entries.size - MAX_ENTRIES).clear()
    }

    private fun classify(text: String): Level {
        val lower = text.lowercase()
        if (lower.contains("error") || lower.contains("fatal") || lower.contains("exception") ||
            lower.contains("unable to load") || lower.contains("failed to load")
        ) return Level.ERROR
        if (lower.contains("can't find") || lower.contains("can't use") || lower.contains("missing") ||
            lower.contains("conflicting") || lower.contains("not allowed") || lower.contains("warning") ||
            lower.contains("doesn't exist") || lower.contains("not found") || lower.contains("multiple help strings")
        ) return Level.WARNING
        return Level.INFO
    }

    private fun timestampEnd(text: String): Int {
        if (!text.startsWith("[")) return 0
        val close = text.indexOf(']')
        if (close < 0) return 0
        var end = close + 1
        if (end < text.length && text[end] == ' ') end++
        return end
    }

    private fun rebuild(jumpToLine: Int? = null) {
        val tokens: Md3Tokens = Md3Theme.buildTokens(this)
        val errorColor = if (tokens.dark) 0xFFFF5252.toInt() else 0xFFD32F2F.toInt()
        val warningColor = if (tokens.dark) 0xFFFFB300.toInt() else 0xFFF57F17.toInt()
        val builder = SpannableStringBuilder()
        val lineNumbers = ArrayList<Int>()
        val lineOffsets = ArrayList<Int>()
        val jumpHighlight = jumpHighlightLine
        val jumpBackground = if (tokens.dark) 0x55C9A227 else 0x66FFF3B0
        val preserveLine = if (jumpToLine != null) null else if (!followTail) currentTopLine() else null

        for (entry in entries) {
            if (selectedLevel != null && entry.level != selectedLevel) continue
            val start = builder.length
            lineNumbers.add(entry.lineNumber)
            lineOffsets.add(start)
            builder.append(entry.text).append('\n')
            if (jumpHighlight != null && entry.lineNumber == jumpHighlight) {
                builder.setSpan(BackgroundColorSpan(jumpBackground), start, builder.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (highlightEnabled) {
                val timestampEnd = timestampEnd(entry.text)
                if (timestampEnd > 0) {
                    builder.setSpan(ForegroundColorSpan(tokens.onSurfaceVariant), start, start + timestampEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val color = when (entry.level) {
                    Level.ERROR -> errorColor
                    Level.WARNING -> warningColor
                    Level.INFO -> tokens.onSurface
                }
                builder.setSpan(ForegroundColorSpan(color), start + timestampEnd, builder.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        textView.text = builder
        textView.setTextColor(tokens.onSurface)
        visibleLineNumbers = lineNumbers.toIntArray()
        visibleLineOffsets = lineOffsets.toIntArray()
        textView.setLineMetadata(visibleLineNumbers, visibleLineOffsets)
        applyWordWrap()
        updateStatus(lineNumbers.size)
        metaView.text = getString(R.string.engine_log_meta, filterLabel(), lineNumbers.size)

        val scrollLine = jumpToLine ?: preserveLine
        if (scrollLine != null) {
            followTail = false
            scrollToLine(scrollLine)
        } else if (followTail && entries.isNotEmpty()) {
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun scrollToLine(lineNumber: Int) {
        cancelPendingScroll()
        pendingScrollLine = lineNumber
        val listener = ViewTreeObserver.OnPreDrawListener {
            val layout = textView.layout ?: return@OnPreDrawListener true
            val line = pendingScrollLine ?: return@OnPreDrawListener true
            cancelPendingScroll()
            var offset = 0
            for (index in visibleLineOffsets.indices) {
                if (visibleLineNumbers[index] == line) {
                    offset = visibleLineOffsets[index]
                    break
                }
            }
            val visualLine = layout.getLineForOffset(offset.coerceAtMost(textView.text.length))
            val targetY = (textView.totalPaddingTop + layout.getLineTop(visualLine) - dp(16)).coerceAtLeast(0)
            scroll.postOnAnimation {
                textView.clearFocus()
                scroll.scrollTo(0, targetY)
            }
            true
        }
        scrollPreDrawListener = listener
        textView.viewTreeObserver.addOnPreDrawListener(listener)
        textView.requestLayout()
    }

    private fun cancelPendingScroll() {
        scrollPreDrawListener?.let { listener ->
            val observer = textView.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
        scrollPreDrawListener = null
        pendingScrollLine = null
    }

    private fun currentTopLine(): Int? {
        val layout = textView.layout ?: return visibleLineNumbers.firstOrNull()
        if (visibleLineNumbers.isEmpty()) return null
        val localY = (scroll.scrollY - textView.totalPaddingTop).coerceAtLeast(0)
        val visualLine = layout.getLineForVertical(localY)
        val offset = layout.getLineStart(visualLine)
        var result = visibleLineNumbers.first()
        for (index in visibleLineOffsets.indices) {
            if (visibleLineOffsets[index] > offset) break
            result = visibleLineNumbers[index]
        }
        return result
    }

    private fun updateStatus(matching: Int) {
        val message = when {
            fileMissing -> R.string.engine_log_not_found
            entries.isEmpty() -> R.string.engine_log_empty
            matching == 0 -> R.string.engine_log_no_matches
            else -> 0
        }
        statusView.visibility = if (message == 0) View.GONE else View.VISIBLE
        if (message != 0) statusView.setText(message)
    }

    private fun filterLabel(): String = getString(when (selectedLevel) {
        Level.ERROR -> R.string.engine_log_filter_error
        Level.WARNING -> R.string.engine_log_filter_warning
        Level.INFO -> R.string.engine_log_filter_info
        null -> R.string.engine_log_filter_all
    })

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS_NAME = "engine_log_viewer"
        private const val PREF_HIGHLIGHT = "highlight"
        private const val PREF_WORD_WRAP = "word_wrap"
        private const val REFRESH_INTERVAL = 2000L
        private const val MAX_ENTRIES = 20000
        private const val SCROLL_FOLLOW_MARGIN = 64
        private const val MENU_HIGHLIGHT = 1
        private const val MENU_CLEAR = 2
        private const val MENU_GO_TO_LINE = 3
        private const val MENU_WORD_WRAP = 4
        private const val FILTER_GROUP = 20
        private const val FILTER_ALL = 21
        private const val FILTER_WARNING = 22
        private const val FILTER_INFO = 23
        private const val FILTER_ERROR = 24
    }
}
