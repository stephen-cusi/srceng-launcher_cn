package me.nillerusr

import com.valvesoftware.source.R

/**
 * Lua/GLua lexer + syntax checker shared with the editor highlighter.
 * Single-pass scanner producing typed tokens; the checker runs a bracket and
 * block-keyword (function/if/for/while/do/repeat...end/until) analysis over it
 * and returns the first issue with line/column for jump-to-error.
 */
object LuaSyntaxChecker {
    const val T_IDENT = 1
    const val T_KEYWORD = 2
    const val T_BUILTIN = 3
    const val T_STRING = 4
    const val T_NUMBER = 5
    const val T_COMMENT = 6
    const val T_PUNCT = 7

    class Token(val type: Int, val start: Int, val end: Int, val line: Int, val column: Int, val text: String)

    class Issue(val messageRes: Int, val line: Int, val column: Int, val args: Array<String>)

    val KEYWORDS = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto",
        "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"
    )

    private val GLUA_BUILTIN_RAW = """
        print pairs ipairs next type tostring tonumber select unpack rawequal rawget rawset
        setmetatable getmetatable pcall xpcall error assert coroutine
        CurTime RealTime FrameTime SysTime SysTime Lerp EyePos EyeAngles EyeVector LocalPlayer
        IsValid IsEntity Color ColorAlpha HSVToColor Vector Angle AngleIdentity AngleDifference
        isvector isangle isnumber isstring istable isentity isfunction ismatrix isbool IsFirstTimePredicted
        hook.Add hook.Remove hook.Run hook.Call hook.GetTable
        timer.Create timer.Remove timer.Simple timer.Adjust timer.Stop timer.Start timer.Repeat
        player.GetAll player.GetHumans player.GetCount player.GetBots
        ents.GetAll ents.FindByClass ents.FindByModel ents.Create ents.CreateClientProp
        ents.FindInSphere ents.FindInBox ents.FindAlongRay ents.FindInCone
        net.Receive net.Start net.SendToServer net.Broadcast net.WriteUInt net.ReadInt
        net.WriteString net.ReadString net.WriteTable net.WriteVector net.WriteAngle
        net.WriteEntity net.WriteBool net.WriteFloat net.ReadUInt net.ReadFloat
        surface.CreateFont surface.SetDrawColor surface.SetTextColor surface.DrawRect
        surface.DrawTexturedRect surface.PlaySound surface.GetTextSize surface.SetMaterial
        draw.RoundedBox draw.DrawText draw.SimpleText draw.NoTexture
        render.RenderView render.Capture render.PushRenderTarget render.PopRenderTarget
        render.SetRenderTarget render.SetScissorRect render.Clear render.DrawBox
        gui.EnableScreenClicker gui.MousePos gui.OpenURL
        chat.AddText chat.PlaySound
        sound.Play sound.PlayFile sound.PlayURL CreateSound StopSound
        game.GetMap game.GetTimeScale game.ConsoleCommand game.BuildNumber game.SinglePlayer
        Msg MsgN MsgC MsgAll PrintTable ErrorNoHalt ErrorNoHaltWithStack Error
        RunString RunStringEx CompileString include AddCSLuaFile
        util.TraceLine util.TraceHull util.JSONToTable util.TableToJSON util.CRC
        util.SharedRandom util.PrecacheModel util.PrecacheSound util.PrecacheParticleSystem
        util.Effect util.SpriteTrail util.ScreenShake util.BlastDamage util.Decal
        util.BSAngleDiff util.IntersectRayWithPlane util.RelativePathToFull
        GetConVar GetConVarNumber GetConVarString FindMetaTable LocalizeString
        language.Add language.GetPhrase vgui.Create vgui.Register vgui.RegisterFromTable
        concommand.Add concommand.Remove cvars.AddChangeCallback cvars.Number cvars.Bool cvars.String
        cam.Start2D cam.End2D cam.Start3D cam.End3D cam.Start3D2D cam.End3D2D
        file.Read file.Write file.Exists file.Find file.FindAll file.Delete file.Size
        math.Clamp math.Round math.Approach math.random math.randomseed math.floor math.ceil
        math.abs math.max math.min math.sin math.cos math.atan2 math.pow math.sqrt math.deg math.rad
        math.Distance math.TimeFraction math.Remap math.Truncate math.huge
        string.format string.sub string.find string.gsub string.gsub string.upper string.lower
        string.len string.rep string.Explode string.Split string.ToTable string.Trim string.TrimLeft string.TrimRight
        table.insert table.remove table.Count table.KeysFromValues table.Inherit table.Copy table.Merge
        table.HasValue table.Empty table.Add table.SortByMember
        SortedPairs SortedPairsByMemberValue SortedPairsByValue
        bit.band bit.bor bit.bnot bit.lshift bit.rshift bit.bxor bit.bswap
        player_manager.GetPlayerModel weapons.GetStored scripted_ents.GetStored
        List list.Get list.Set ListToTable hud.Add
        Material Resource vgui.CursorAllowed? cam.Start
    """.trimIndent()

    private val C_KEYWORDS = setOf(
        "var", "let", "const", "function", "return", "if", "else", "for", "while", "class",
        "new", "this", "null", "true", "false", "void", "int", "float", "double", "bool",
        "struct", "public", "private", "protected", "static", "final", "import", "export",
        "interface", "extends", "implements", "try", "catch", "finally", "throw", "typeof",
        "switch", "case", "break", "continue", "do", "delete", "instanceof", "in", "of",
        "unsigned", "long", "short", "char", "auto", "sizeof", "template", "namespace"
    )

    /** space-separated builtin words used both by the checker (as identifiers) and the editor */
    val GLUA_BUILTINS: Set<String> = GLUA_BUILTIN_RAW
        .split(Regex("[\\s]+"))
        .filter { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '_' } }
        .toSet()

    fun isKeyword(word: String) = word in KEYWORDS
    fun isBuiltin(word: String) = word in GLUA_BUILTINS

    // ------------------------------------------------------------- lexer

    fun lex(source: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        var line = 1
        var lineStart = 0
        val n = source.length

        fun col(pos: Int) = pos - lineStart + 1

        while (i < n) {
            val c = source[i]
            when {
                c == '\n' -> { i++; line++; lineStart = i }
                c == ' ' || c == '\t' || c == '\r' -> i++
                c == '-' && i + 1 < n && source[i + 1] == '-' -> {
                    val start = i
                    val startLine = line
                    val startCol = col(i)
                    i += 2
                    var level = 0
                    if (i < n && source[i] == '[') {
                        var j = i + 1
                        while (j < n && source[j] == '=') j++
                        if (j < n && source[j] == '[') { level = j - (i + 1); i = j + 1 }
                    }
                    val textEnd: Int
                    var unclosed = false
                    if (level > 0) {
                        val closer = "]" + "=".repeat(level) + "]"
                        val close = source.indexOf(closer, i)
                        if (close < 0) { textEnd = n; i = n; unclosed = true }
                        else { textEnd = close + closer.length; i = textEnd }
                    } else {
                        textEnd = source.indexOf('\n', i).let { if (it < 0) n else it }
                        i = textEnd
                    }
                    var l = startLine
                    var ls = lineStart
                    for (idx in start until textEnd) {
                        if (source[idx] == '\n') { l++; ls = idx + 1 }
                    }
                    line = l; lineStart = ls
                    tokens.add(Token(T_COMMENT, start, i, startLine, startCol, source.substring(start, i)))
                    if (unclosed) tokens.add(Token(-2, i, i, startLine, startCol, "EOF_COMMENT"))
                }
                c == '[' && i + 1 < n && (source[i + 1] == '[' || source[i + 1] == '=') -> {
                    var j = i + 1
                    while (j < n && source[j] == '=') j++
                    if (j < n && source[j] == '[') {
                        val level = j - (i + 1)
                        val start = i
                        val startLine = line
                        val startCol = col(i)
                        val closer = "]" + "=".repeat(level) + "]"
                        val close = source.indexOf(closer, j + 1)
                        val textEnd: Int
                        if (close < 0) { textEnd = n; i = n }
                        else { textEnd = close + closer.length; i = textEnd }
                        var l = line
                        var ls = lineStart
                        for (idx in start until textEnd) {
                            if (source[idx] == '\n') { l++; ls = idx + 1 }
                        }
                        line = l; lineStart = ls
                        tokens.add(Token(T_STRING, start, textEnd, startLine, startCol, source.substring(start, textEnd)))
                    } else {
                        tokens.add(Token(T_PUNCT, i, i + 1, line, col(i), "[")); i++
                    }
                }
                c == '"' || c == '\'' -> {
                    val start = i
                    val startLine = line
                    val startCol = col(i)
                    val quote = c
                    i++
                    var closed = false
                    while (i < n) {
                        val ch = source[i]
                        if (ch == '\\') {
                            if (i + 1 < n && source[i + 1] == '\n') { line++; lineStart = i + 2 }
                            i += 2
                        } else if (ch == quote) { i++; closed = true; break }
                        else if (ch == '\n') { break }
                        else i++
                    }
                    if (!closed) {
                        tokens.add(Token(T_STRING, start, i, startLine, startCol, source.substring(start, i)))
                        tokens.add(Token(-1, i, i, startLine, startCol, "EOF_STRING"))
                    } else {
                        tokens.add(Token(T_STRING, start, i, startLine, startCol, source.substring(start, i)))
                    }
                }
                c.isDigit() -> {
                    val start = i
                    if (c == '0' && i + 1 < n && (source[i + 1] == 'x' || source[i + 1] == 'X')) {
                        i += 2
                        while (i < n && source[i].isLetterOrDigit()) i++
                    } else {
                        while (i < n && (source[i].isDigit() || source[i] == '.')) i++
                        if (i < n && (source[i] == 'e' || source[i] == 'E')) {
                            var j = i + 1
                            if (j < n && (source[j] == '+' || source[j] == '-')) j++
                            if (j < n && source[j].isDigit()) {
                                i = j
                                while (i < n && source[i].isDigit()) i++
                            }
                        }
                    }
                    tokens.add(Token(T_NUMBER, start, i, line, col(start), source.substring(start, i)))
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < n && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    val word = source.substring(start, i)
                    val type = when {
                        word in KEYWORDS -> T_KEYWORD
                        word in GLUA_BUILTINS -> T_BUILTIN
                        else -> T_IDENT
                    }
                    tokens.add(Token(type, start, i, line, col(start), word))
                }
                else -> {
                    tokens.add(Token(T_PUNCT, i, i + 1, line, col(i), c.toString()))
                    i++
                }
            }
        }
        return tokens
    }

    // ------------------------------------------------------------- checker

    fun check(source: String): Issue? {
        val tokens = lex(source)
        val brackets = ArrayList<Token>()
        val blocks = ArrayList<Token>()

        for (t in tokens) {
            when (t.type) {
                -1 -> return Issue(R.string.check_unclosed_string, t.line, t.column, emptyArray())
                -2 -> return Issue(R.string.check_unclosed_comment, t.line, t.column, emptyArray())
                T_STRING -> if (t.text.isNotEmpty() && t.text[0] == '"' || t.text.isNotEmpty() && t.text[0] == '\'') {
                    val quote = t.text[0]
                    val body = t.text.substring(1)
                    if (!(body.isNotEmpty() && body.endsWith(quote))) {
                        return Issue(R.string.check_unclosed_string, t.line, t.column, emptyArray())
                    }
                }
                T_PUNCT -> when (t.text) {
                    "(", "[", "{" -> brackets.add(t)
                    ")", "]", "}" -> {
                        val opener = brackets.removeLastOrNull()
                            ?: return Issue(R.string.check_unmatched_bracket, t.line, t.column, arrayOf(t.text))
                        val expected = when (opener.text) { "(" -> ")" ; "[" -> "]" ; else -> "}" }
                        if (expected != t.text) {
                            return Issue(R.string.check_unmatched_bracket, t.line, t.column, arrayOf(t.text))
                        }
                    }
                }
                T_KEYWORD -> when (t.text) {
                    "function", "if", "repeat" -> blocks.add(t)
                    "for", "while" -> blocks.add(t)
                    "do" -> {
                        val top = blocks.lastOrNull()
                        if (top == null || top.text != "for" && top.text != "while") blocks.add(t)
                    }
                    "end" -> {
                        val top = blocks.removeLastOrNull()
                            ?: return Issue(R.string.check_unexpected_end, t.line, t.column, emptyArray())
                        if (top.text == "repeat") {
                            return Issue(R.string.check_unexpected_end, t.line, t.column, emptyArray())
                        }
                    }
                    "until" -> {
                        val top = blocks.removeLastOrNull()
                            ?: return Issue(R.string.check_unexpected_until, t.line, t.column, emptyArray())
                        if (top.text != "repeat") {
                            return Issue(R.string.check_until_without_repeat, t.line, t.column, arrayOf(top.text))
                        }
                    }
                }
            }
        }
        if (brackets.isNotEmpty()) {
            val b = brackets.last()
            return Issue(R.string.check_unclosed_bracket, b.line, b.column, arrayOf(b.text))
        }
        if (blocks.isNotEmpty()) {
            val b = blocks.last()
            return if (b.text == "repeat") Issue(R.string.check_missing_until, b.line, b.column, arrayOf(b.line.toString()))
            else Issue(R.string.check_missing_end, b.line, b.column, arrayOf(b.text, b.line.toString()))
        }
        return null
    }

    // ------------------------------------------------------ generic highlighter

    class Span(val start: Int, val end: Int, val color: Int)

    /**
     * Simple single-pass colorizer for KV/cfg/C-like files: quoted strings,
     * // and # comments, block comments for C-family, numbers, keywords.
     */
    fun scanGeneric(source: String, cFamily: Boolean): List<Pair<Regex, Int>> {
        val rules = ArrayList<Pair<Regex, Int>>()
        rules.add(Regex("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?)\\b") to EditorColors.NUMBER)
        if (cFamily) {
            rules.add(Regex("\\b(" + C_KEYWORDS.joinToString("|") + ")\\b") to EditorColors.KEYWORD)
        }
        rules.add(Regex("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'") to EditorColors.STRING)
        rules.add(
            (if (cFamily) Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/") else Regex("//[^\\n]*|#[^\\n]*"))
                to EditorColors.COMMENT
        )
        return rules
    }

    object EditorColors {
        // overridden by the activity per theme
        var KEYWORD = 0xFF569CD6.toInt()
        var STRING = 0xFFCE9178.toInt()
        var COMMENT = 0xFF6A9955.toInt()
        var NUMBER = 0xFFB5CEA8.toInt()
        var BUILTIN = 0xFFDCDCAA.toInt()
        var PUNCT = 0xFF9CDCFE.toInt()
    }
}
