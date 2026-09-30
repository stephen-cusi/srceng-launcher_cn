package me.nillerusr

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.valvesoftware.source.R
import java.io.File
import java.io.IOException
import me.nillerusr.md3.Md3Theme

/**
 * Lightweight text editor for game resource files (lua/txt/cfg/kv/...) with
 * simple regex-based syntax highlighting. Files inside VPK/GMA archives are
 * opened read-only via a cache copy made by the caller.
 */
open class CodeEditorActivity : Activity() {
    private lateinit var editor: EditText
    private lateinit var statusView: TextView
    private lateinit var saveButton: Button
    private lateinit var titleView: TextView
    private lateinit var predictiveBack: PredictiveBackController
    private var targetFile: File? = null
    private var readOnly = false
    private var dirty = false
    private val highlightRunnable = Runnable { applyHighlight() }

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, (24 * resources.displayMetrics.density).toInt(), pad, pad)
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
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        titleView = TextView(this).apply {
            text = title.ifEmpty { targetFile?.name ?: "" }
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * resources.displayMetrics.density).toInt()
            }
        }
        saveButton = Button(this).apply {
            setText(R.string.editor_save)
            tag = "filled"
            visibility = View.GONE
        }
        header.addView(backButton)
        header.addView(titleView)
        header.addView(saveButton)

        statusView = TextView(this).apply {
            textSize = 12f
            tag = "subtitle"
            setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
        }

        editor = EditText(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.TOP
            setHorizontallyScrolling(true)
            minWidth = (resources.displayMetrics.widthPixels * 9) / 10
        }
        root.addView(header)
        root.addView(statusView)
        root.addView(
            editor,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
        )
        setContentView(root)
        Md3Theme.applyAfterSetContentView(this)
        predictiveBack = PredictiveBackController(this) { finish() }
        predictiveBack.sync()

        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrEmpty()) {
            finish()
            return
        }
        targetFile = File(path)
        if (title.isEmpty()) titleView.text = targetFile!!.name
        readOnly = intent.getBooleanExtra(EXTRA_READONLY, false)
        val file = targetFile!!
        try {
            if (file.length() > SIZE_LIMIT) {
                readOnly = true
                try { Toast.makeText(this, R.string.editor_too_large, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
            }
            editor.setText(file.readText())
        } catch (_: Throwable) {
            try { Toast.makeText(this, R.string.editor_open_failed, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
            finish()
            return
        }
        if (readOnly) {
            editor.isEnabled = false
            statusView.setText(R.string.editor_readonly)
        } else {
            saveButton.visibility = View.VISIBLE
            saveButton.setOnClickListener { save() }
            editor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    dirty = true
                    editor.removeCallbacks(highlightRunnable)
                    editor.postDelayed(highlightRunnable, 350)
                }
            })
        }
        applyHighlight()
    }

    override fun onDestroy() {
        editor.removeCallbacks(highlightRunnable)
        if (::predictiveBack.isInitialized) predictiveBack.release()
        super.onDestroy()
    }

    private fun save() {
        val file = targetFile ?: return
        try {
            file.writeText(editor.text.toString())
            dirty = false
            try { Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {}
        } catch (_: Throwable) {
            try { Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_LONG).show() } catch (_: Throwable) {}
        }
    }

    // ------------------------------------------------------------------ highlight

    private class Rule(val regex: Regex, val color: Int)

    private fun rulesFor(name: String): List<Rule> {
        val lower = name.lowercase()
        val strings = Rule(Regex("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'"), COLOR_STRING)
        val numbers = Rule(Regex("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?)\\b"), COLOR_NUMBER)
        val luaKeywords = Rule(Regex("\\b(and|break|do|else|elseif|end|false|for|function|goto|if|in|local|nil|not|or|repeat|return|then|true|until|while)\\b"), COLOR_KEYWORD)
        val cKeywords = Rule(Regex("\\b(var|let|const|function|return|if|else|for|while|class|new|this|null|true|false|void|int|float|bool|struct|public|private|static)\\b"), COLOR_KEYWORD)
        return when {
            lower.endsWith(".lua") -> listOf(
                strings, numbers, luaKeywords,
                Rule(Regex("--\\[\\[[\\s\\S]*?\\]\\]|--[^\\n]*"), COLOR_COMMENT)
            )
            lower.endsWith(".kv") || lower.endsWith(".vmt") || lower.endsWith(".vcf") ||
                lower.endsWith(".res") || lower.endsWith(".cfg") || lower.endsWith(".txt") -> listOf(
                strings, numbers,
                Rule(Regex("//[^\\n]*"), COLOR_COMMENT)
            )
            lower.endsWith(".js") || lower.endsWith(".json") || lower.endsWith(".c") || lower.endsWith(".cpp") ||
                lower.endsWith(".h") || lower.endsWith(".hpp") || lower.endsWith(".glsl") || lower.endsWith(".java") -> listOf(
                strings, numbers, cKeywords,
                Rule(Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), COLOR_COMMENT)
            )
            else -> listOf(strings, numbers)
        }
    }

    private fun applyHighlight() {
        val text = editor.editableText ?: return
        if (text.length > HIGHLIGHT_LIMIT) return
        val file = targetFile ?: return
        val rules = rulesFor(file.name)
        for (span in text.getSpans(0, text.length, CharacterStyle::class.java)) {
            if (span is ForegroundColorSpan) text.removeSpan(span)
        }
        val content = text.toString()
        // later rules overwrite earlier ones, so comments go last and win
        for (rule in rules) {
            for (match in rule.regex.findAll(content)) {
                text.setSpan(
                    ForegroundColorSpan(rule.color),
                    match.range.first, match.range.last + 1,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "code_editor_path"
        const val EXTRA_READONLY = "code_editor_readonly"
        const val EXTRA_TITLE = "code_editor_title"

        private const val HIGHLIGHT_LIMIT = 200_000
        private const val SIZE_LIMIT = 2_000_000

        private val COLOR_KEYWORD = 0xFF7CB7FF.toInt()
        private val COLOR_STRING = 0xFFE0A075.toInt()
        private val COLOR_COMMENT = 0xFF7FA97C.toInt()
        private val COLOR_NUMBER = 0xFFB5CEA8.toInt()

        private val EDITABLE_EXTENSIONS = setOf(
            "lua", "txt", "cfg", "kv", "vmt", "vcf", "res", "json", "js", "xml", "html",
            "css", "ini", "log", "md", "sh", "py", "c", "cpp", "h", "hpp", "glsl"
        )

        fun isEditableTextFile(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in EDITABLE_EXTENSIONS
    }
}
