package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.valvesoftware.source.R
import java.io.File
import me.nillerusr.md3.Md3Theme
import me.nillerusr.md3.Md3Tokens

/**
 * Text editor for game resource files: theme-aware syntax highlighting that
 * matches the file type, GLua autocomplete popup, line numbers, a Lua/GLua
 * syntax checker with jump-to-error, and save for real files (archive content
 * is opened read-only by the caller).
 */
open class CodeEditorActivity : Activity() {
    private enum class Lang { LUA, KV, C, TEXT }

    private lateinit var editor: EditText
    private lateinit var lineNumbers: TextView
    private lateinit var statusView: TextView
    private lateinit var cursorView: TextView
    private lateinit var saveButton: Button
    private lateinit var checkButton: Button
    private lateinit var titleView: TextView
    private lateinit var predictiveBack: PredictiveBackController
    private var tokens: Md3Tokens? = null
    private var targetFile: File? = null
    private var readOnly = false
    private var lang: Lang = Lang.TEXT
    private var lastLineCount = -1
    private var issue: LuaSyntaxChecker.Issue? = null
    private var completionPopup: PopupWindow? = null
    private var completionList: ListView? = null
    private var completionStart = -1
    private val completionItems = ArrayList<String>()
    private var documentWords: Set<String> = emptySet()
    private var completionWords: Set<String> = emptySet()
    private var findTerm = ""

    private val highlightRunnable = Runnable { refreshDecorations() }

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
        tokens = try { Md3Theme.buildTokens(this) } catch (_: Throwable) { null }
        completionWords = try {
            assets.open("glua_words.txt").bufferedReader().use { reader ->
                reader.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.first().isLetter() }
                    .toSet()
            }
        } catch (_: Throwable) {
            LuaSyntaxChecker.GLUA_BUILTINS
        }
        applyLanguageColors()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (14 * resources.displayMetrics.density).toInt()
            setPadding(pad, (20 * resources.displayMetrics.density).toInt(), pad, (8 * resources.displayMetrics.density).toInt())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backButton = ImageButton(this).apply {
            setBackgroundResource(R.drawable.md3_btn_icon)
            setImageResource(R.drawable.ic_arrow_back)
            contentDescription = getString(R.string.md3_back)
            tag = "icon"
            setOnClickListener { finish() }
        }
        titleView = TextView(this).apply {
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (10 * resources.displayMetrics.density).toInt()
                marginEnd = (6 * resources.displayMetrics.density).toInt()
            }
        }
        checkButton = Button(this).apply {
            setText(R.string.editor_check)
            tag = "outlined"
            setOnClickListener { runCheck(jump = false) }
        }
        saveButton = Button(this).apply {
            setText(R.string.editor_save)
            tag = "filled"
            setOnClickListener { save() }
        }
        val menuButton = ImageButton(this).apply {
            setBackgroundResource(R.drawable.md3_btn_icon)
            setImageResource(R.drawable.ic_more_vert)
            contentDescription = getString(R.string.editor_menu_info)
            tag = "icon"
            setOnClickListener { showOptionsMenu(it) }
        }
        header.addView(backButton)
        header.addView(titleView)
        header.addView(checkButton)
        header.addView(saveButton)
        header.addView(menuButton)

        statusView = TextView(this).apply {
            textSize = 12f
            tag = "subtitle"
            isClickable = true
            isFocusable = true
            setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
            setOnClickListener { jumpToIssue() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        lineNumbers = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.TOP
            setPadding((6 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                cornerRadius = 8 * resources.displayMetrics.density
                setColor(tokens?.surfaceContainerLow ?: 0x11000000)
            }
        }
        editor = object : androidx.appcompat.widget.AppCompatEditText(this) {
            override fun onSelectionChanged(start: Int, end: Int) {
                super.onSelectionChanged(start, end)
                if (::cursorView.isInitialized) updateCursorStatus()
            }
        }.apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.TOP
            setHorizontallyScrolling(true)
            setPadding((10 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt(), (4 * resources.displayMetrics.density).toInt(), 0)
            setTextColor(tokens?.onSurface ?: 0xFFCCCCCC.toInt())
            setLineSpacing(0f, 1f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = (8 * resources.displayMetrics.density).toInt()
            }
        }
        content.addView(lineNumbers)
        content.addView(editor)

        cursorView = TextView(this).apply {
            textSize = 11f
            tag = "subtitle"
            setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        root.addView(header)
        root.addView(statusView)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(cursorView)
        setContentView(root)
        Md3Theme.applyAfterSetContentView(this)
        // Md3Theme styles every EditText with a filled ripple background; the
        // code surface needs a quiet, ripple-free canvas instead.
        try {
            editor.background = GradientDrawable().apply {
                cornerRadius = 10 * resources.displayMetrics.density
                setColor(tokens?.surfaceContainerLow ?: 0xFFECECEC.toInt())
            }
            editor.backgroundTintList = null
            editor.foreground = null
            editor.setTextColor(tokens?.onSurface ?: 0xFF1C1B17.toInt())
            val primaryTone = tokens?.primary?.color ?: 0xFF6750A4.toInt()
            editor.setHighlightColor((primaryTone and 0x00FFFFFF) or 0x33000000)
            lineNumbers.setTextColor(tokens?.onSurfaceVariant ?: 0xFF777777.toInt())
            lineNumbers.background = GradientDrawable().apply {
                cornerRadius = 10 * resources.displayMetrics.density
                setColor(tokens?.surfaceContainer ?: 0xFFE0E0E0.toInt())
            }
        } catch (_: Throwable) {
        }
        predictiveBack = PredictiveBackController(this) { finish() }
        predictiveBack.sync()

        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrEmpty()) { finish(); return }
        targetFile = File(path)
        val file = targetFile!!
        titleView.text = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifEmpty { file.name }
        readOnly = intent.getBooleanExtra(EXTRA_READONLY, false)
        var fileText = ""
        try {
            if (file.length() > SIZE_LIMIT) {
                readOnly = true
                try { Toast.makeText(this, R.string.editor_too_large, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
            }
            fileText = file.readText()
        } catch (_: Throwable) {
            try { Toast.makeText(this, R.string.editor_open_failed, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
            finish()
            return
        }
        lang = detectLanguage(file.name, fileText)
        checkButton.visibility = if (lang == Lang.LUA) View.VISIBLE else View.GONE
        editor.setText(fileText)
        editor.setSelection(0)
        if (readOnly) {
            editor.keyListener = null
            editor.isCursorVisible = false
            statusView.setText(R.string.editor_readonly)
            saveButton.visibility = View.GONE
        }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                editor.removeCallbacks(highlightRunnable)
                editor.postDelayed(highlightRunnable, 350)
                updateLineNumbers()
                updateCompletion()
            }
        })
        editor.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            lineNumbers.translationY = -scrollY.toFloat()
        }
        updateLineNumbers()
        refreshDecorations()
        updateCursorStatus()
    }

    override fun onDestroy() {
        editor.removeCallbacks(highlightRunnable)
        completionPopup?.dismiss()
        if (::predictiveBack.isInitialized) predictiveBack.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ setup

    private fun detectLanguage(name: String, content: String): Lang {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".lua") -> Lang.LUA
            lower.endsWith(".kv") || lower.endsWith(".cfg") || lower.endsWith(".vmt") ||
                lower.endsWith(".vcf") || lower.endsWith(".res") || lower.endsWith(".ini") -> Lang.KV
            lower.endsWith(".js") || lower.endsWith(".json") || lower.endsWith(".c") ||
                lower.endsWith(".cpp") || lower.endsWith(".h") || lower.endsWith(".hpp") ||
                lower.endsWith(".java") || lower.endsWith(".glsl") || lower.endsWith(".xml") ||
                lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".ts") -> Lang.C
            lower.contains(".") -> Lang.TEXT
            else -> if (looksLikeLua(content)) Lang.LUA else Lang.TEXT
        }
    }

    private fun looksLikeLua(content: String): Boolean {
        val head = content.take(4000)
        return Regex("(?m)^\\s*(local\\s+|function\\b|end\\b|if\\b|while\\b|for\\b|repeat\\b)").containsMatchIn(head) ||
            Regex("\\bfunction\\s+[A-Za-z_][\\w.]*\\s*\\(").containsMatchIn(head)
    }

    private fun applyLanguageColors() {
        val dark = tokens?.dark ?: true
        val c = LuaSyntaxChecker.EditorColors
        if (dark) {
            c.KEYWORD = 0xFF569CD6.toInt(); c.STRING = 0xFFCE9178.toInt()
            c.COMMENT = 0xFF6A9955.toInt(); c.NUMBER = 0xFFB5CEA8.toInt()
            c.BUILTIN = 0xFFDCDCAA.toInt(); c.PUNCT = 0xFF9CDCFE.toInt()
        } else {
            c.KEYWORD = 0xFF0000FF.toInt(); c.STRING = 0xFFA31515.toInt()
            c.COMMENT = 0xFF008000.toInt(); c.NUMBER = 0xFF098658.toInt()
            c.BUILTIN = 0xFF795E26.toInt(); c.PUNCT = 0xFF0431FA.toInt()
        }
    }

    // ------------------------------------------------------------------ render

    private fun refreshDecorations() {
        updateHighlight()
        val content = editor.text.toString()
        if (lang == Lang.LUA && content.length <= HIGHLIGHT_LIMIT) {
            documentWords = Regex("[A-Za-z_][A-Za-z0-9_]{2,}")
                .findAll(content).map { it.value }.distinct().take(400).toSet()
        }
        if (!readOnly) runCheck(jump = false)
        updateLineNumbers()
    }

    private fun updateHighlight() {
        val text = editor.editableText ?: return
        if (text.length > HIGHLIGHT_LIMIT) return
        for (span in text.getSpans(0, text.length, CharacterStyle::class.java)) {
            if (span is ForegroundColorSpan || span is BackgroundColorSpan) text.removeSpan(span)
        }
        val content = text.toString()
        when (lang) {
            Lang.LUA -> {
                for (token in LuaSyntaxChecker.lex(content)) {
                    val color = when (token.type) {
                        LuaSyntaxChecker.T_KEYWORD -> LuaSyntaxChecker.EditorColors.KEYWORD
                        LuaSyntaxChecker.T_BUILTIN -> LuaSyntaxChecker.EditorColors.BUILTIN
                        LuaSyntaxChecker.T_STRING -> LuaSyntaxChecker.EditorColors.STRING
                        LuaSyntaxChecker.T_NUMBER -> LuaSyntaxChecker.EditorColors.NUMBER
                        LuaSyntaxChecker.T_COMMENT -> LuaSyntaxChecker.EditorColors.COMMENT
                        else -> 0
                    }
                    if (color != 0 && token.end > token.start) {
                        text.setSpan(
                            ForegroundColorSpan(color), token.start, token.end,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            Lang.KV, Lang.C -> {
                for ((regex, color) in LuaSyntaxChecker.scanGeneric(content, lang == Lang.C)) {
                    for (match in regex.findAll(content)) {
                        text.setSpan(
                            ForegroundColorSpan(color), match.range.first, match.range.last + 1,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            Lang.TEXT -> Unit
        }
    }

    private fun updateLineNumbers() {
        val count = editor.text.count { it == '\n' } + 1
        if (count == lastLineCount) return
        lastLineCount = count
        val builder = StringBuilder(count * 5)
        for (i in 1..count) {
            if (i > 1) builder.append('\n')
            builder.append(i)
        }
        lineNumbers.text = builder.toString()
    }

    private fun updateCursorStatus() {
        val position = editor.selectionStart.coerceAtLeast(0)
        val layout = editor.layout ?: return
        val line = layout.getLineForOffset(position) + 1
        val column = position - layout.getLineStart(layout.getLineForOffset(position)) + 1
        val languageLabel = getString(
            when (lang) {
                Lang.LUA -> R.string.editor_lang_lua
                Lang.KV -> R.string.editor_lang_kv
                Lang.C -> R.string.editor_lang_c
                Lang.TEXT -> R.string.editor_lang_text
            }
        )
        cursorView.text = getString(R.string.editor_status_pos, line, column) + "  ·  " + languageLabel
    }

    // ------------------------------------------------------------------ check

    private fun runCheck(jump: Boolean) {
        if (lang != Lang.LUA) {
            issue = null
            statusView.text = ""
            return
        }
        val content = editor.text.toString()
        if (content.length > CHECK_LIMIT) return
        val result = LuaSyntaxChecker.check(content)
        issue = result
        if (result == null) {
            statusView.setTextColor(greenColor())
            statusView.text = getString(R.string.check_ok)
        } else {
            statusView.setTextColor(tokens?.error?.color ?: 0xFFC62828.toInt())
            statusView.text = getString(R.string.check_fail, result.line, result.column, getString(result.messageRes, *result.args))
            if (jump) jumpToIssue()
        }
    }

    private fun jumpToIssue() {
        val current = issue ?: return
        val layout = editor.layout ?: return
        val lineIndex = (current.line - 1).coerceIn(0, layout.lineCount - 1)
        val lineStart = layout.getLineStart(lineIndex)
        val lineEnd = layout.getLineEnd(lineIndex)
        val offset = (lineStart + current.column - 1).coerceIn(lineStart, lineEnd)
        editor.requestFocus()
        editor.setSelection(offset)
    }

    private fun greenColor(): Int = if (tokens?.dark == false) 0xFF2E7D32.toInt() else 0xFF4CAF50.toInt()

    // ------------------------------------------------------------------ save

    private fun save() {
        val file = targetFile ?: return
        try {
            file.writeText(editor.text.toString())
            try { Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
            runCheck(jump = false)
        } catch (_: Throwable) {
            try { Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
        }
    }

    // ------------------------------------------------------------------ completion

    private fun currentWord(): Pair<String, Int>? {
        val text = editor.text ?: return null
        var start = editor.selectionStart
        if (start < 0) return null
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
        val word = text.substring(start, editor.selectionStart)
        return if (word.length >= 2 && word.first().isLetter()) word to start else null
    }

    private fun updateCompletion() {
        if (readOnly) return
        val current = currentWord()
        if (current == null) { completionPopup?.dismiss(); return }
        val (word, start) = current
        val prefix = word.lowercase()
        val source = completionWords + documentWords + LuaSyntaxChecker.KEYWORDS
        // VSCode-style ranking: exact-case prefix > case-insensitive prefix >
        // camel-hump initials > substring; shorter first within each group.
        val exactPrefix = ArrayList<String>()
        val foldedPrefix = ArrayList<String>()
        val hump = ArrayList<String>()
        val substring = ArrayList<String>()
        for (candidate in source) {
            if (candidate == word) continue
            if (candidate.startsWith(word)) exactPrefix.add(candidate)
            else if (candidate.lowercase().startsWith(prefix)) foldedPrefix.add(candidate)
            else if (camelHumpStartsWith(candidate, prefix)) hump.add(candidate)
            else if (candidate.lowercase().contains(prefix)) substring.add(candidate)
        }
        val byLength = compareBy<String> { it.length }
        val matches = (exactPrefix.sortedWith(byLength) +
            foldedPrefix.sortedWith(byLength) +
            hump.sortedWith(byLength) +
            substring.sortedWith(byLength)).take(12)
        if (matches.isEmpty()) { completionPopup?.dismiss(); return }
        completionItems.clear()
        completionItems.addAll(matches)
        showCompletion(start)
    }

    /** "gpos" matches "GetPos" via camel-hump initials (G.Pos). */
    private fun camelHumpStartsWith(candidate: String, prefix: String): Boolean {
        if (prefix.length < 2) return false
        val initials = StringBuilder()
        var previousWasLower = false
        for (ch in candidate) {
            if (ch.isUpperCase() && previousWasLower) initials.append(ch.lowercaseChar())
            previousWasLower = ch.isLowerCase() || ch.isDigit()
        }
        return initials.isNotEmpty() && initials.startsWith(prefix)
    }

    private fun showCompletion(start: Int) {
        completionStart = start
        try {
            val popup = completionPopup
            if (popup != null) {
                (completionList?.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
                positionPopup(popup)
                return
            }
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, completionItems) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(tokens?.onSurface ?: 0xFFCCCCCC.toInt())
                    view.typeface = Typeface.MONOSPACE
                    view.textSize = 13f
                    view.setBackgroundColor(Color.TRANSPARENT)
                    val pad = (10 * resources.displayMetrics.density).toInt()
                    view.setPadding(pad, pad / 2, pad, pad / 2)
                    return view
                }
            }
            val listView = ListView(this).apply {
                divider = null
                this.adapter = adapter
                setBackgroundColor(tokens?.surfaceContainerHigh ?: 0xFF2A2A2A.toInt())
                setOnItemClickListener { _, _, position, _ ->
                    val word = completionItems.getOrNull(position) ?: return@setOnItemClickListener
                    val end = editor.selectionStart
                    if (completionStart in 0..end) {
                        editor.text.replace(completionStart, end, word)
                    }
                    completionPopup?.dismiss()
                }
            }
            val newPopup = PopupWindow(listView, (editor.width * 0.6).toInt().coerceAtLeast(400), ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                elevation = 12 * resources.displayMetrics.density
            }
            completionList = listView
            completionPopup = newPopup
            positionPopup(newPopup)
        } catch (error: Throwable) {
            Log.e("CodeEditor", "completion popup failed", error)
            completionPopup?.dismiss()
        }
    }

    private fun rootViewOf(): View = findViewById(android.R.id.content)

    // ------------------------------------------------------------------ menu

    private fun showOptionsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.editor_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.editor_menu_find -> { openFindDialog(); true }
                R.id.editor_menu_goto_line -> { openGotoLineDialog(); true }
                R.id.editor_menu_complete -> { manualComplete(); true }
                R.id.editor_menu_info -> { showFileInfo(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun openFindDialog() {
        val input = EditText(this).apply {
            setText(findTerm)
            setSelection(findTerm.length)
            hint = getString(R.string.editor_menu_find)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val wrapper = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.editor_menu_find)
            .setView(wrapper)
            .setPositiveButton(R.string.editor_find_next) { _, _ -> findNext(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Md3Theme.applyDialog(it) }
    }

    private fun findNext(term: String) {
        if (term.isEmpty()) return
        findTerm = term
        val content = editor.text.toString()
        val lower = content.lowercase()
        val needle = term.lowercase()
        val count = lower.split(needle).size - 1
        if (count == 0) {
            try { Toast.makeText(this, R.string.editor_find_not_found, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
            return
        }
        val from = (editor.selectionEnd).coerceIn(0, content.length)
        var index = lower.indexOf(needle, from)
        if (index < 0) index = lower.indexOf(needle)
        editor.requestFocus()
        editor.setSelection(index, index + term.length)
        applyFindHighlights(term)
        try { Toast.makeText(this, getString(R.string.editor_find_count, count), Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
    }

    private fun applyFindHighlights(term: String) {
        val text = editor.editableText ?: return
        val lower = text.toString().lowercase()
        val needle = term.lowercase()
        var index = lower.indexOf(needle)
        val highlight = 0x40FFC107
        while (index >= 0) {
            val span = BackgroundColorSpan(highlight)
            text.setSpan(span, index, index + term.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = lower.indexOf(needle, index + term.length)
        }
    }

    private fun openGotoLineDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.editor_menu_goto_line)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val wrapper = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.editor_menu_goto_line)
            .setView(wrapper)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val lineNumber = input.text.toString().toIntOrNull()
                val layout = editor.layout
                if (lineNumber != null && layout != null && lineNumber >= 1 && lineNumber <= layout.lineCount) {
                    editor.requestFocus()
                    editor.setSelection(layout.getLineStart(lineNumber - 1))
                } else {
                    try { Toast.makeText(this, R.string.editor_goto_invalid, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Md3Theme.applyDialog(it) }
    }

    private fun manualComplete() {
        if (readOnly) return
        if (currentWord() == null) {
            try { Toast.makeText(this, R.string.editor_complete_none, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
            return
        }
        updateCompletion()
        if (completionPopup?.isShowing != true) {
            try { Toast.makeText(this, R.string.editor_complete_none, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
        }
    }

    private fun showFileInfo() {
        val file = targetFile ?: return
        val lineCount = editor.text.count { it == '\n' } + 1
        val languageLabel = getString(
            when (lang) {
                Lang.LUA -> R.string.editor_lang_lua
                Lang.KV -> R.string.editor_lang_kv
                Lang.C -> R.string.editor_lang_c
                Lang.TEXT -> R.string.editor_lang_text
            }
        )
        val message = file.path + "\n" +
            getString(R.string.editor_info_lines, lineCount) + " · " + languageLabel + "\n" +
            getString(R.string.editor_info_size, file.length())
        MaterialAlertDialogBuilder(this)
            .setTitle(titleView.text)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show().also { Md3Theme.applyDialog(it) }
    }

    private fun positionPopup(popup: PopupWindow) {
        try {
            val layout = editor.layout ?: return
            val position = editor.selectionStart.coerceAtLeast(0)
            val line = layout.getLineForOffset(position)
            val x = layout.getPrimaryHorizontal(position).toInt()
            val lineTop = layout.getLineTop(line)
            val location = IntArray(2)
            editor.getLocationInWindow(location)
            val popupX = (location[0] + x - editor.scrollX).coerceAtLeast(location[0])
            val popupY = location[1] + lineTop - editor.scrollY + (layout.getLineBottom(line) - layout.getLineTop(line))
            if (popup.isShowing) popup.dismiss()
            if (popupY > 0) {
                popup.showAtLocation(rootViewOf(), Gravity.NO_GRAVITY, popupX, popupY)
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val EXTRA_PATH = "code_editor_path"
        const val EXTRA_READONLY = "code_editor_readonly"
        const val EXTRA_TITLE = "code_editor_title"

        private const val HIGHLIGHT_LIMIT = 300_000
        private const val CHECK_LIMIT = 500_000
        private const val SIZE_LIMIT = 2_000_000

        private val EDITABLE_EXTENSIONS = setOf(
            "lua", "txt", "cfg", "kv", "vmt", "vcf", "res", "ini", "json", "js", "xml", "html",
            "css", "log", "md", "sh", "py", "c", "cpp", "h", "hpp", "glsl", "java", "ts"
        )

        fun isEditableTextFile(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in EDITABLE_EXTENSIONS
    }
}
