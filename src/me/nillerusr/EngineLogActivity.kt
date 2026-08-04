package me.nillerusr

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.valvesoftware.source.R
import java.io.File
import java.io.RandomAccessFile
import me.nillerusr.md3.Md3Theme
import me.nillerusr.md3.Md3Tokens

class EngineLogActivity : Activity() {
    private enum class Level { ERROR, WARNING, INFO }

    private data class Entry(val text: String, val level: Level)

    private lateinit var predictiveBack: PredictiveBackController
    private lateinit var pathView: TextView
    private lateinit var statusArea: View
    private lateinit var statusView: TextView
    private lateinit var textView: TextView
    private lateinit var scroll: ScrollView

    private var logFile: File? = null
    private val entries = ArrayList<Entry>()
    private val pending = StringBuilder()
    private var lastReadOffset = 0L
    private var selectedLevel: Level? = null
    private var refreshing = false
    private var followTail = true
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

        pathView = findViewById(R.id.engine_log_path)
        statusArea = findViewById(R.id.engine_log_status_area)
        statusView = findViewById(R.id.engine_log_status)
        textView = findViewById(R.id.engine_log_text)
        scroll = findViewById(R.id.engine_log_scroll)

        findViewById<ImageButton>(R.id.md3_button_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.engine_log_refresh).setOnClickListener { forceReload() }
        findViewById<RadioGroup>(R.id.engine_log_filter_group).setOnCheckedChangeListener { _, checkedId ->
            selectedLevel = when (checkedId) {
                R.id.engine_log_filter_error -> Level.ERROR
                R.id.engine_log_filter_warning -> Level.WARNING
                R.id.engine_log_filter_info -> Level.INFO
                else -> null
            }
            rebuild()
            scroll.post { scroll.scrollTo(0, 0) }
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val reachable = scroll.getChildAt(0)
                val max = if (reachable == null) 0 else reachable.height - scroll.height
                followTail = scrollY >= max - SCROLL_FOLLOW_MARGIN
            }
        }

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
        if (::predictiveBack.isInitialized) predictiveBack.release()
        super.onDestroy()
    }

    private fun locateLogFile() {
        val prefs = getSharedPreferences("mod", 0)
        var gamePath = prefs.getString("gamepath", "").orEmpty()
        if (gamePath.isEmpty()) gamePath = LauncherActivity.getDefaultDir() + "/srceng"
        var candidate = File(gamePath, "engine.log")
        if (!candidate.isFile) {
            val alt = File(LauncherActivity.getDefaultDir() + "/srceng", "engine.log")
            if (alt.isFile) candidate = alt
        }
        logFile = candidate
        pathView.text = candidate.path
        forceReload()
    }

    private fun forceReload() {
        lastReadOffset = 0L
        entries.clear()
        pending.setLength(0)
        refresh()
    }

    private fun refresh() {
        val file = logFile ?: return
        if (refreshing) return
        refreshing = true
        try {
            if (!file.isFile) {
                if (statusArea.visibility != View.VISIBLE) {
                    statusArea.visibility = View.VISIBLE
                    statusView.setText(R.string.engine_log_not_found)
                }
                return
            }
            if (statusArea.visibility == View.VISIBLE) statusArea.visibility = View.GONE
            val length = file.length()
            if (length < lastReadOffset) {
                lastReadOffset = 0L
                entries.clear()
                pending.setLength(0)
            }
            if (length == lastReadOffset) return
            readRange(file, lastReadOffset, length)
            lastReadOffset = length
        } catch (_: Throwable) {
        } finally {
            refreshing = false
        }
        rebuild()
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
        var index: Int
        while (true) {
            index = pending.indexOf("\n")
            if (index < 0) break
            val line = pending.substring(0, index).removeSuffix("\r")
            pending.delete(0, index + 1)
            if (line.isNotEmpty()) entries.add(Entry(line, classify(line)))
        }
        if (entries.size > MAX_ENTRIES) {
            entries.subList(0, entries.size - MAX_ENTRIES).clear()
        }
    }

    private fun classify(text: String): Level {
        val lower = text.lowercase()
        if (lower.contains("error") || lower.contains("fatal") || lower.contains("exception")) return Level.ERROR
        if (lower.contains("can't find") || lower.contains("can't use") || lower.contains("unable to load") ||
            lower.contains("missing") || lower.contains("conflicting") || lower.contains("not allowed") ||
            lower.contains("warning") || lower.contains("doesn't exist") || lower.contains("not found")
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

    @SuppressLint("SetTextI18n")
    private fun rebuild() {
        val tokens: Md3Tokens = Md3Theme.buildTokens(this)
        val errorColor = if (tokens.dark) 0xFFFF5252.toInt() else 0xFFD32F2F.toInt()
        val warnColor = if (tokens.dark) 0xFFFFB300.toInt() else 0xFFF57F17.toInt()
        val infoColor = tokens.onSurface
        val timestampColor = tokens.onSurfaceVariant

        val atBottom = followTail
        val builder = SpannableStringBuilder()
        var matching = 0
        for (entry in entries) {
            if (selectedLevel != null && entry.level != selectedLevel) continue
            matching++
            val start = builder.length
            builder.append(entry.text).append('\n')
            val tsEnd = timestampEnd(entry.text)
            if (tsEnd > 0) {
                builder.setSpan(ForegroundColorSpan(timestampColor), start, start + tsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val color = when (entry.level) {
                Level.ERROR -> errorColor
                Level.WARNING -> warnColor
                Level.INFO -> infoColor
            }
            builder.setSpan(ForegroundColorSpan(color), start + tsEnd, builder.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textView.text = builder
        statusArea.visibility = if (matching == 0) View.VISIBLE else View.GONE
        if (matching == 0) statusView.setText(R.string.engine_log_no_matches)
        if (atBottom || entries.isEmpty()) {
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    companion object {
        private const val REFRESH_INTERVAL = 2000L
        private const val MAX_ENTRIES = 20000
        private const val SCROLL_FOLLOW_MARGIN = 64
    }
}
