package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.valvesoftware.source.R
import java.io.File
import java.io.RandomAccessFile
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import me.nillerusr.md3.Md3Motion
import me.nillerusr.md3.Md3Theme
import me.nillerusr.md3.Md3Tokens

class EngineLogActivity : Activity() {
    private enum class Level { ERROR, WARNING, INFO }

    private data class Entry(val lineNumber: Int, val text: String, val level: Level)

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
    /** 菜单/对话框打开期间暂停自动刷新，避免重建日志与界面动画抢主线程。 */
    private var uiBusy = false
    private var followTail = false
    private var fileMissing = false
    private var highlightEnabled = true
    private var wordWrapEnabled = true
    private var visibleLineNumbers = IntArray(0)
    private var visibleLineOffsets = IntArray(0)
    private var jumpHighlightLine: Int? = null
    private var pendingScrollLine: Int? = null
    private var scrollPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var pendingRestoreAnchor: Pair<Int, Int>? = null
    private var pendingRestoreBottom = false
    private var restoreLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var restorePreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var restoreLastContentHeight = -1
    private var searchQuery = ""
    private var searchCaseSensitive = false
    private var searchRegex = false
    private var searchMatchStarts = IntArray(0)
    private var searchMatchEnds = IntArray(0)
    private var searchCurrentIndex = -1
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!uiBusy) refresh()
            handler.postDelayed(this, REFRESH_INTERVAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Md3Theme.applyBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_engine_log)
        Md3Theme.applyAfterSetContentView(this)

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
        applyExpressiveMotion()
        locateLogFile()
    }

    /** 顶栏图标按钮接入 Expressive 弹簧手感。 */
    private fun applyExpressiveMotion() {
        try {
            Md3Motion.attachPressBounce(
                findViewById(R.id.md3_button_back),
                findViewById(R.id.engine_log_filter),
                findViewById(R.id.engine_log_menu)
            )
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

    override fun onResume() {
        super.onResume()
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
        super.onDestroy()
    }

    private fun showMainMenu(anchor: View) {
        uiBusy = true
        PopupMenu(this, anchor).apply {
            setOnDismissListener { uiBusy = false }
            menu.add(0, MENU_SEARCH, 0, R.string.engine_log_search).apply {
                icon = null
            }
            menu.add(0, MENU_HIGHLIGHT, 1, R.string.engine_log_highlight).apply {
                isCheckable = true
                isChecked = highlightEnabled
            }
            menu.add(0, MENU_CLEAR, 2, R.string.engine_log_clear)
            menu.add(0, MENU_GO_TO_LINE, 3, R.string.engine_log_go_to_line)
            menu.add(0, MENU_WORD_WRAP, 4, R.string.engine_log_word_wrap).apply {
                isCheckable = true
                isChecked = wordWrapEnabled
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_SEARCH -> {
                        showSearchDialog()
                        true
                    }
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
                        wordWrapEnabled = !wordWrapEnabled
                        saveDisplayPrefs()
                        rebuild()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showFilterMenu(anchor: View) {
        uiBusy = true
        PopupMenu(this, anchor).apply {
            setOnDismissListener { uiBusy = false }
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
                // Filter change: we intentionally want to start from the top of the filtered list.
                // Cancel any in-flight restore and disable the scroll-restore inside rebuild so a
                // stale OnPreDrawListener can't later yank the user's manual scrolling back down.
                cancelPendingScroll()
                rebuild(restoreScroll = false)
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

    private fun showSearchDialog() {
        uiBusy = true
        val container = layoutInflater.inflate(R.layout.dialog_engine_log_search, null)
        val inputLayout = container.findViewById<TextInputLayout>(R.id.engine_log_search_input_layout)
        val input = container.findViewById<TextInputEditText>(R.id.engine_log_search_input)
        val caseSwitch = container.findViewById<MaterialSwitch>(R.id.engine_log_search_case_sensitive)
        val regexSwitch = container.findViewById<MaterialSwitch>(R.id.engine_log_search_regex)
        val status = container.findViewById<TextView>(R.id.engine_log_search_status)
        val prevButton = container.findViewById<MaterialButton>(R.id.engine_log_search_prev)
        val nextButton = container.findViewById<MaterialButton>(R.id.engine_log_search_next)

        input.setText(searchQuery)
        caseSwitch.isChecked = searchCaseSensitive
        regexSwitch.isChecked = searchRegex

        fun updateMatches(anchorIndex: Int?) {
            searchQuery = input.text.toString()
            searchCaseSensitive = caseSwitch.isChecked
            searchRegex = regexSwitch.isChecked
            searchCurrentIndex = anchorIndex ?: searchCurrentIndex
            rebuild()
            status.text = if (searchMatchStarts.isEmpty()) getString(R.string.engine_log_search_no_matches) else getString(R.string.engine_log_search_count, searchCurrentIndex + 1, searchMatchStarts.size)
        }

        fun navigate(delta: Int) {
            if (searchMatchStarts.isEmpty()) return
            searchCurrentIndex = (searchCurrentIndex + delta + searchMatchStarts.size) % searchMatchStarts.size
            val start = searchMatchStarts[searchCurrentIndex]
            status.text = getString(R.string.engine_log_search_count, searchCurrentIndex + 1, searchMatchStarts.size)
            rebuild()
            scrollToOffset(start)
        }

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateMatches(null)
            }
        })
        caseSwitch.setOnCheckedChangeListener { _, _ -> updateMatches(null) }
        regexSwitch.setOnCheckedChangeListener { _, _ -> updateMatches(null) }
        prevButton.setOnClickListener { navigate(-1) }
        nextButton.setOnClickListener { navigate(1) }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.engine_log_search)
            .setView(container)
            .setNegativeButton(android.R.string.cancel) { _, _ -> clearSearch() }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                searchQuery = input.text.toString()
                searchCaseSensitive = caseSwitch.isChecked
                searchRegex = regexSwitch.isChecked
            }
            .create()
        dialog.setOnDismissListener { uiBusy = false }
        dialog.setOnShowListener {
            input.post { input.requestFocus() }
            updateMatches(0)
        }
        dialog.show()
        Md3Theme.applyDialog(dialog)
    }

    /** 取消搜索：清空查询与匹配高亮，回到普通浏览状态。 */
    private fun clearSearch() {
        searchQuery = ""
        searchMatchStarts = IntArray(0)
        searchMatchEnds = IntArray(0)
        searchCurrentIndex = -1
        rebuild()
    }

    private fun compileSearchPattern(query: String, caseSensitive: Boolean, regex: Boolean): Pattern {
        val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return if (regex) Pattern.compile(query, flags) else Pattern.compile(Pattern.quote(query), flags)
    }

    private fun scrollToOffset(offset: Int) {
        cancelPendingScroll()
        val listener = ViewTreeObserver.OnPreDrawListener {
            val layout = textView.layout ?: return@OnPreDrawListener true
            cancelPendingScroll()
        val visualLine = layout.getLineForOffset(offset.coerceAtMost(textView.text.length))
        // getLineTop already includes TextView's top padding; don't add totalPaddingTop again.
        val targetY = (layout.getLineTop(visualLine) - dp(24)).coerceAtLeast(0)
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

    private fun clampIndex(index: Int, size: Int): Int = if (size == 0) -1 else ((index % size) + size) % size

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
        // 文件名栏是 Expressive 大标题的一部分，直接坐在 surface 上，跟其它页面的标题栏一致；
        // 与日志正文的层次靠正文自己的 surfaceContainerLow 拉开。
        findViewById<View>(R.id.engine_log_file_bar).setBackgroundColor(tokens.surface)
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
        cancelPendingScroll()
        refresh()
        rebuild(restoreScroll = false)
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
        // 错误：独立的 error/fatal/exception 单词，或明确的失败短语。
        // 用 \b 避免 GL_KHR_no_error 这种扩展名被误判为错误。
        if (ERROR_KEYWORD_PATTERN.matcher(lower).find() ||
            lower.contains("failed to load") ||
            lower.contains("unable to load")
        ) return Level.ERROR
        if (lower.contains("warning:") ||
            lower.contains("can't find") ||
            lower.contains("can't use") ||
            lower.contains("missing") ||
            lower.contains("conflicting") ||
            lower.contains("not allowed") ||
            lower.contains("not found") ||
            lower.contains("doesn't exist") ||
            lower.contains("multiple help strings")
        ) return Level.WARNING
        return Level.INFO
    }

    /**
     * Applies mtsx-style token highlighting to one log line. Rules run in priority order
     * (first match wins). Once a character range is colored, later rules never overwrite it.
     * maxLength lets us skip overly broad matches (e.g. the entire GL_EXTENSIONS quoted list).
     */
    private fun applyLogTokenHighlight(builder: SpannableStringBuilder, lineStart: Int, lineEnd: Int, text: String, dark: Boolean) {
        val n = text.length
        if (n <= 0) return
        val colored = BooleanArray(n)
        for (rule in LOG_TOKEN_RULES) {
            val matcher = rule.pattern.matcher(text)
            val color = if (dark) rule.nightColor else rule.dayColor
            while (matcher.find()) {
                var rs = matcher.start()
                val re = matcher.end()
                if (re - rs > rule.maxLength) continue
                if (rs >= n) break
                // Trim to the uncolored part of this match (higher-priority rules win).
                while (rs < re && colored[rs]) rs++
                var e = re
                while (e > rs && colored[e - 1]) e--
                if (e <= rs) continue
                for (i in rs until e) colored[i] = true
                val absStart = lineStart + rs
                val absEnd = lineStart + e
                builder.setSpan(ForegroundColorSpan(color), absStart, absEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (rule.bold) {
                    builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), absStart, absEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    private fun rebuild(jumpToLine: Int? = null, restoreScroll: Boolean = true) {
        val tokens: Md3Tokens = Md3Theme.buildTokens(this)
        val builder = SpannableStringBuilder()
        val lineNumbers = ArrayList<Int>()
        val lineOffsets = ArrayList<Int>()
        val jumpHighlight = jumpHighlightLine
        val jumpBackground = if (tokens.dark) 0x55C9A227 else 0x66FFF3B0
        // Capture scroll anchor (text offset + intra-line pixel delta) so the exact visible
        // position can be restored after the text is rebuilt (highlight toggle, wrap toggle, refresh).
        // Decide "at bottom" from the actual scroll position rather than the followTail flag, which
        // can be stale right after an auto-refresh grows the content.
        //
        // Guard: if a restore is ALREADY pending from a prior rebuild that hasn't settled yet
        // (e.g. an auto-refresh fired while a menu-triggered rebuild's OnPreDrawListener is still
        // active), do NOT overwrite its anchor. Otherwise the newer rebuild might capture a
        // scrollY that has already been clamped toward the top by a relayout, and restore to the
        // wrong place. Reusing the in-flight anchor keeps the user's position stable.
        val useInFlight = restorePreDrawListener != null
        val wasAtBottom = if (useInFlight) pendingRestoreBottom else {
            val contentView = scroll.getChildAt(0)
            val maxScroll = if (contentView == null) 0 else contentView.height - scroll.height
            scroll.scrollY >= maxScroll - SCROLL_FOLLOW_MARGIN
        }
        val scrollAnchor = if (useInFlight) pendingRestoreAnchor else computeScrollAnchor()

        for (entry in entries) {
            if (selectedLevel != null && entry.level != selectedLevel) continue
            val start = builder.length
            lineNumbers.add(entry.lineNumber)
            lineOffsets.add(start)
            builder.append(entry.text).append('\n')
            val lineEnd = builder.length - 1
            if (jumpHighlight != null && entry.lineNumber == jumpHighlight) {
                builder.setSpan(BackgroundColorSpan(jumpBackground), start, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (highlightEnabled) {
                // 行级淡背景：一眼区分日志等级（Error 淡红、Warning 淡橙），不覆盖跳转高亮行
                when (entry.level) {
                    Level.ERROR -> if (jumpHighlight != entry.lineNumber) builder.setSpan(
                        BackgroundColorSpan(if (tokens.dark) 0x1AFF5252.toInt() else 0x14D32F2F.toInt()),
                        start, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    Level.WARNING -> if (jumpHighlight != entry.lineNumber) builder.setSpan(
                        BackgroundColorSpan(if (tokens.dark) 0x1AFFB300.toInt() else 0x14F57F17.toInt()),
                        start, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    Level.INFO -> {}
                }
                // mtsx 风格 token 高亮（时间戳/错误/警告/路径/资源/数字/关键词等）
                applyLogTokenHighlight(builder, start, lineEnd, entry.text, tokens.dark)
            }
        }

        val matchStarts = ArrayList<Int>()
        val matchEnds = ArrayList<Int>()
        if (searchQuery.isNotEmpty()) {
            try {
                val matcher = compileSearchPattern(searchQuery, searchCaseSensitive, searchRegex).matcher(builder)
                while (matcher.find()) {
                    matchStarts.add(matcher.start())
                    matchEnds.add(matcher.end())
                }
            } catch (_: PatternSyntaxException) {
            }
        }
        searchMatchStarts = matchStarts.toIntArray()
        searchMatchEnds = matchEnds.toIntArray()
        if (searchMatchStarts.isNotEmpty()) searchCurrentIndex = clampIndex(searchCurrentIndex, searchMatchStarts.size) else searchCurrentIndex = -1
        val matchBackground = if (tokens.dark) 0x55FFFFFF else 0x55FFD54F
        val currentMatchBackground = if (tokens.dark) 0xAAFFB300.toInt() else 0x88FF6F00.toInt()
        for (i in searchMatchStarts.indices) {
            val background = if (i == searchCurrentIndex) currentMatchBackground else matchBackground
            builder.setSpan(BackgroundColorSpan(background), searchMatchStarts[i], searchMatchEnds[i], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        textView.text = builder
        textView.setTextColor(tokens.onSurface)
        visibleLineNumbers = lineNumbers.toIntArray()
        visibleLineOffsets = lineOffsets.toIntArray()
        textView.setLineMetadata(visibleLineNumbers, visibleLineOffsets)
        applyWordWrap()
        updateStatus(lineNumbers.size)
        metaView.text = getString(R.string.engine_log_meta, filterLabel(), lineNumbers.size)

        // Restore scroll position. We use a SELF-RE-ARMING OnPreDrawListener instead of a single
        // post {} or OnGlobalLayoutListener. An OnPreDrawListener fires AFTER the view hierarchy
        // has been measured/layouted for that frame, so textView.layout is guaranteed fresh and
        // never reports a stale lineTop of 0. We re-apply the restore on EVERY draw until:
        //   - the anchor/bottom has actually been reached, AND
        //   - the content height has stopped changing between consecutive passes (layout settled).
        // This is robust against the multiple relayouts triggered by word-wrap reparenting, popup
        // menu close, and auto-refresh growth — and it never "gives up early" on a stale layout.
        if (!restoreScroll) {
            // Scroll restore disabled (e.g. filter change → we reset to top separately). Make sure
            // no stale restore listener survives.
            removeRestoreListeners()
        } else if (jumpToLine != null) {
            followTail = false
            scrollToLine(jumpToLine)
        } else {
            removeRestoreListeners()
            pendingRestoreBottom = wasAtBottom
            pendingRestoreAnchor = scrollAnchor
            restoreLastContentHeight = -1
            restorePreDrawListener = ViewTreeObserver.OnPreDrawListener {
                val layout = textView.layout
                val content = scroll.getChildAt(0)
                val contentHeight = content?.height ?: 0
                val layoutSettled = layout != null && contentHeight > 0 && contentHeight == restoreLastContentHeight
                // If we still need to scroll, do it on this pass.
                if (pendingRestoreBottom) {
                    val maxScroll = (contentHeight - scroll.height).coerceAtLeast(0)
                    val maxReached = layout != null && contentHeight > 0 && scroll.scrollY >= maxScroll - 1
                    if (maxReached && layoutSettled) {
                        removeRestoreListeners()
                    } else {
                        if (layout != null && contentHeight > 0) {
                            scroll.scrollTo(0, maxScroll)
                        }
                        restoreLastContentHeight = contentHeight
                    }
                } else {
                    val anchor = pendingRestoreAnchor
                    if (anchor == null || layout == null) {
                        return@OnPreDrawListener true // not ready yet; keep listener armed
                    }
                    val (offset, delta) = anchor
                    val newVisualLine = layout.getLineForOffset(offset.coerceAtMost(textView.text.length))
                    val targetY = (layout.getLineTop(newVisualLine) + delta).coerceAtLeast(0)
                    val reached = kotlin.math.abs(targetY - scroll.scrollY) <= 1
                    if (reached && layoutSettled) {
                        removeRestoreListeners()
                    } else {
                        scroll.scrollTo(0, targetY)
                        restoreLastContentHeight = contentHeight
                    }
                }
                true
            }
            scroll.viewTreeObserver.addOnPreDrawListener(restorePreDrawListener)
            textView.requestLayout()
        }
    }

    private fun removeRestoreListeners() {
        restorePreDrawListener?.let { listener ->
            val observer = scroll.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
        restorePreDrawListener = null
        restoreLayoutListener?.let { listener ->
            val observer = scroll.viewTreeObserver
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
        restoreLayoutListener = null
        pendingRestoreAnchor = null
        pendingRestoreBottom = false
        restoreLastContentHeight = -1
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
            // getLineTop already includes TextView's top padding.
            val targetY = (layout.getLineTop(visualLine) - dp(16)).coerceAtLeast(0)
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
        removeRestoreListeners()
    }

    /**
     * Captures the exact currently-visible position as a (textOffset, intraLineDelta) pair.
     * textOffset is the character offset of the topmost visible visual line; intraLineDelta is
     * how far the actual scroll position is below that visual line's top (for partially-scrolled
     * lines). After a rebuild we re-locate the same textOffset in the new layout, which keeps the
     * visible position stable even when total height or per-line heights change (e.g. word wrap toggle).
     */
    private fun computeScrollAnchor(): Pair<Int, Int>? {
        val layout = textView.layout ?: return null
        if (visibleLineNumbers.isEmpty()) return null
        // scroll.scrollY is the in-TextView y (TextView top == 0 inside the ScrollView) and it
        // already includes the TextView's top padding. Layout coordinates share that same origin,
        // so pass scroll.scrollY directly without subtracting padding.
        val y = scroll.scrollY
        val visualLine = layout.getLineForVertical(y)
        val offset = layout.getLineStart(visualLine).coerceAtMost(textView.text.length)
        val delta = (y - layout.getLineTop(visualLine)).coerceAtLeast(0)
        return offset to delta
    }

    private fun currentTopLine(): Int? {
        val layout = textView.layout ?: return null
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
        private const val MENU_SEARCH = 0
        private const val MENU_HIGHLIGHT = 1
        private const val MENU_CLEAR = 2
        private const val MENU_GO_TO_LINE = 3
        private const val MENU_WORD_WRAP = 4
        private const val FILTER_GROUP = 20
        private const val FILTER_ALL = 21
        private const val FILTER_WARNING = 22
        private const val FILTER_INFO = 23
        private const val FILTER_ERROR = 24

        /** 用于 classify()，避免 GL_KHR_no_error 这类扩展名被误判为 error。 */
        private val ERROR_KEYWORD_PATTERN = Pattern.compile("""\b(?:error|fatal|exception)\b""")

        /** One token-highlight rule, mirroring the srcenglog.mtsx grammar. */
        private data class LogTokenRule(
            val pattern: Pattern,
            val dayColor: Int,
            val nightColor: Int,
            val bold: Boolean = false,
            val maxLength: Int = Int.MAX_VALUE,
        )

        /**
         * Token highlight rules in priority order (first match wins, mirroring the mtsx
         * "contains" order: timestamp → fatal → error → warning → info → LoadLibrary →
         * module tag → keyword → keyword2 → path → quoted resource → number).
         */
        private val LOG_TOKEN_RULES = listOf(
            // 1. 时间戳 [0.1469]
            LogTokenRule(Pattern.compile("^\\[\\d+\\.\\d+\\]"), 0xFF888888.toInt(), 0xFF666666.toInt()),
            // 2. 致命错误（服务器关闭、断开连接、断言失败）
            LogTokenRule(Pattern.compile("Fatal|Assertion|Server shutting down|Dropped .* from server"), 0xFFFF0000.toInt(), 0xFFCC3333.toInt(), bold = true),
            // 3. 普通错误（Error:、unknown shader、failed to load、Unable to load）
            LogTokenRule(Pattern.compile("Error:[^\\n]*|unknown shader|failed to load|Unable to load"), 0xFFCC0000.toInt(), 0xFFDD4444.toInt(), bold = true),
            // 4. 警告（Warning、Can't find module、conflicting、Missing Vgui material、not allowed、not found、not loaded 等）
            LogTokenRule(
                Pattern.compile(
                    "Warning:[^\\n]*|Can't find module[^\\n]*|Unable to load[^\\n]*|conflicting[^\\n]*|" +
                    "Can't use cheat cvar[^\\n]*|Missing Vgui material[^\\n]*|not allowed[^\\n]*|" +
                    "multiple help strings[^\\n]*|not found[^\\n]*|not loaded[^\\n]*"
                ),
                0xFFFF8800.toInt(), 0xFFDD7700.toInt()
            ),
            // 5. 正常加载/成功（loaded for、Found font、started at、SDL version、success、OK）
            LogTokenRule(Pattern.compile("loaded for|Found font|started at|SDL version|\\bsuccess\\b|\\bOK\\b"), 0xFF008800.toInt(), 0xFF66AA66.toInt()),
            // 6. 加载库动作 LoadLibrary:
            LogTokenRule(Pattern.compile("LoadLibrary:[^\\n]*"), 0xFF008800.toInt(), 0xFF66AA66.toInt()),
            // 7. 核心模块标签 [Engine]、[AppFramework]
            LogTokenRule(Pattern.compile("\\[[A-Za-z_]+\\]"), 0xFF0055AA.toInt(), 0xFF77AADD.toInt()),
            // 8. 图形/渲染关键词（GL_、OpenGL、Material、Shader、Texture、Vulkan）
            LogTokenRule(Pattern.compile("GL_\\w+|OpenGL|Render|Material|Shader|\\bTexture\\b|Vulkan"), 0xFF7B1FA2.toInt(), 0xFFCE93D8.toInt()),
            // 9. 服务器/网络关键词（SV_、CL_、Steam、server、client、tickrate、maxplayers）
            LogTokenRule(Pattern.compile("SV_|CL_|\\bSteam\\b|\\bserver\\b|\\bclient\\b|\\btickrate\\b|\\bmaxplayers\\b"), 0xFFAD1457.toInt(), 0xFFF48FB1.toInt()),
            // 10. 文件路径
            LogTokenRule(Pattern.compile("/[^\\s\"]+"), 0xFF996600.toInt(), 0xFFCCAA44.toInt()),
            // 11. 引号内资源名（超长引号跳过，避免 GL_EXTENSIONS 整段被涂成绿色）
            LogTokenRule(Pattern.compile("\"[^\"]*\""), 0xFF22AA22.toInt(), 0xFF77CC77.toInt(), maxLength = 120),
            // 12. 数字（浮点/整数）
            LogTokenRule(Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b"), 0xFF0055CC.toInt(), 0xFF8899DD.toInt()),
        )
    }
}
