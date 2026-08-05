package wanjie.quicklook.ui.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * 轻量语法高亮引擎（自研）。
 *
 * 按文件扩展名识别语言，用一组预编译正则把文本切分为 token，
 * 再合成带 [SpanStyle] 的 [AnnotatedString] 交给编辑器渲染。
 *
 * 性能说明：每次调用都会对全文跑一遍正则。调用方应通过 remember 缓存
 * 结果，并在超大文件上主动关闭高亮。
 */
object CodeHighlighter {

    /** 高亮配色（VS Code 风格），按明暗主题切换 */
    internal data class Palette(
        val keyword: Color,
        val type: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val annotation: Color,
    ) {
        companion object {
            val Light = Palette(
                keyword = Color(0xFF0000CC),
                type = Color(0xFF008080),
                string = Color(0xFFA31515),
                comment = Color(0xFF008000),
                number = Color(0xFF098658),
                annotation = Color(0xFF795E26),
            )
            val Dark = Palette(
                keyword = Color(0xFF569CD6),
                type = Color(0xFF4EC9B0),
                string = Color(0xFFCE9178),
                comment = Color(0xFF6A9955),
                number = Color(0xFFB5CEA8),
                annotation = Color(0xFFDCDCAA),
            )
        }
    }

    private enum class Kind { KEYWORD, TYPE, STRING, COMMENT, NUMBER, ANNOTATION }

    private fun Kind.color(p: Palette): Color = when (this) {
        Kind.KEYWORD -> p.keyword
        Kind.TYPE -> p.type
        Kind.STRING -> p.string
        Kind.COMMENT -> p.comment
        Kind.NUMBER -> p.number
        Kind.ANNOTATION -> p.annotation
    }

    private data class LangRules(
        val keywords: Set<String> = emptySet(),
        val types: Set<String> = emptySet(),
        val lineComment: String? = null,
        val blockCommentStart: String? = null,
        val blockCommentEnd: String? = null,
        val strings: List<Char> = emptyList(),
        val annotation: Boolean = false,
        val caseInsensitive: Boolean = false,
        val xml: Boolean = false,
        val markdown: Boolean = false,
        val css: Boolean = false,
    )

    /** 根据文件名识别语言 id（扩展名 → 语言）。未知返回 null。 */
    fun detectLanguage(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return EXT_LANG[ext]
    }

    /**
     * 对 [text] 做语法高亮。语言按 [fileName] 自动识别；无法识别时原样返回。
     * @param dark 是否使用暗色配色
     */
    fun highlight(text: String, fileName: String, dark: Boolean): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")
        val lang = detectLanguage(fileName) ?: return AnnotatedString(text)
        val palette = if (dark) Palette.Dark else Palette.Light
        val rules = RULES[lang] ?: return AnnotatedString(text)

        val tokens = mutableListOf<Triple<Int, Int, Kind>>()
        for ((re, kind) in rules) {
            for (m in re.findAll(text)) {
                tokens += Triple(m.range.first, m.range.last + 1, kind)
            }
        }
        if (tokens.isEmpty()) return AnnotatedString(text)

        tokens.sortBy { it.first }
        val builder = AnnotatedString.Builder()
        var cursor = 0
        for ((s, e, kind) in tokens) {
            if (e <= s || s < cursor) continue
            builder.append(text, cursor, s)
            builder.pushStyle(SpanStyle(color = kind.color(palette)))
            builder.append(text, s, e)
            builder.pop()
            cursor = e
        }
        builder.append(text, cursor, text.length)
        return builder.toAnnotatedString()
    }

    private val RULES: Map<String, List<Pair<Regex, Kind>>> by lazy {
        LANG_RULES.mapValues { (_, r) -> compile(r) }
    }

    private fun compile(r: LangRules): List<Pair<Regex, Kind>> {
        val opts = if (r.caseInsensitive) RegexOption.IGNORE_CASE else null
        val out = mutableListOf<Pair<Regex, Kind>>()

        if (r.lineComment != null) {
            out += Regex(Regex.escape(r.lineComment) + ".*$", RegexOption.MULTILINE) to Kind.COMMENT
        }
        if (r.blockCommentStart != null && r.blockCommentEnd != null) {
            out += Regex(
                Regex.escape(r.blockCommentStart) + ".*?" + Regex.escape(r.blockCommentEnd),
                RegexOption.DOT_MATCHES_ALL,
            ) to Kind.COMMENT
        }
        for (q in r.strings) {
            out += Regex("$q(?:\\\\.|[^\\\\$q\\n])*$q") to Kind.STRING
        }
        if (r.keywords.isNotEmpty()) {
            val pat = "\\b(?:${r.keywords.joinToString("|") { Regex.escape(it) }})\\b"
            out += (if (opts != null) Regex(pat, opts) else Regex(pat)) to Kind.KEYWORD
        }
        if (r.types.isNotEmpty()) {
            val pat = "\\b(?:${r.types.joinToString("|") { Regex.escape(it) }})\\b"
            out += (if (opts != null) Regex(pat, opts) else Regex(pat)) to Kind.TYPE
        }
        if (r.annotation) {
            out += Regex("@[A-Za-z_][A-Za-z0-9_]*") to Kind.ANNOTATION
        }
        if (r.markdown) {
            out += Regex("^#{1,6}[ \t].*$", RegexOption.MULTILINE) to Kind.KEYWORD
            out += Regex("`[^`]*`") to Kind.STRING
            out += Regex("\\[[^\\]]*\\]\\([^)]*\\)") to Kind.ANNOTATION
            out += Regex("^>[ \t].*$", RegexOption.MULTILINE) to Kind.COMMENT
            out += Regex("^[ \\t]*[-*+] ") to Kind.NUMBER
        }
        if (r.css) {
            out += Regex("@[\\w-]+") to Kind.ANNOTATION
            out += Regex("#[0-9A-Fa-f]{3,8}\\b") to Kind.NUMBER
            out += Regex("[\\w-]+(?=\\s*:)") to Kind.ANNOTATION
        }
        if (r.xml) {
            out += Regex("<[A-Za-z][A-Za-z0-9_-]*") to Kind.TYPE
            out += Regex("</[A-Za-z][A-Za-z0-9_-]*>") to Kind.TYPE
            out += Regex("[A-Za-z_][A-Za-z0-9_.:-]*(?==)") to Kind.ANNOTATION
        }

        // 数字规则放在最后，避免与上面前缀规则重叠时被排到前面
        out += Regex("\\b\\d[\\d_]*(?:\\.[0-9]+)?(?:[eE][+-]?\\d+)?") to Kind.NUMBER
        return out
    }

    private val EXT_LANG = mapOf(
        "kt" to "kotlin", "kts" to "kotlin",
        "java" to "java",
        "c" to "c", "h" to "c",
        "cpp" to "cpp", "cc" to "cpp", "cxx" to "cpp", "hpp" to "cpp",
        "cs" to "csharp",
        "js" to "javascript", "mjs" to "javascript", "cjs" to "javascript", "jsx" to "javascript",
        "ts" to "typescript", "tsx" to "typescript",
        "py" to "python",
        "go" to "go",
        "rs" to "rust",
        "rb" to "ruby",
        "php" to "php",
        "swift" to "swift",
        "sh" to "shell", "bash" to "shell", "zsh" to "shell",
        "bat" to "batch", "cmd" to "batch",
        "sql" to "sql",
        "gradle" to "groovy", "groovy" to "groovy",
        "dart" to "dart",
        "json" to "json",
        "xml" to "xml",
        "html" to "html", "htm" to "html", "xhtml" to "html", "svg" to "html",
        "css" to "css", "scss" to "css", "less" to "css",
        "yml" to "yaml", "yaml" to "yaml",
        "md" to "markdown", "markdown" to "markdown",
        "properties" to "properties", "conf" to "properties", "ini" to "properties", "cfg" to "properties",
        "toml" to "toml",
    )

    private val LANG_RULES: Map<String, LangRules> = mapOf(
        "kotlin" to LangRules(
            keywords = setOf(
                "package", "import", "class", "object", "interface", "fun", "val", "var", "if", "else",
                "when", "for", "while", "do", "return", "try", "catch", "finally", "throw", "new",
                "this", "super", "true", "false", "null", "is", "in", "as", "out", "public", "private",
                "protected", "internal", "open", "abstract", "sealed", "data", "enum", "override",
                "suspend", "companion", "init", "constructor", "lateinit", "by", "break", "continue",
                "where", "typealias", "reified", "const", "tailrec", "operator", "infix", "external",
                "noinline", "crossinline", "vararg", "dynamic", "expect", "actual", "get", "set",
            ),
            types = setOf(
                "Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char", "String",
                "Any", "Unit", "Nothing", "Array", "List", "MutableList", "Set", "MutableSet",
                "Map", "MutableMap", "HashMap",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\'', '`'), annotation = true,
        ),
        "java" to LangRules(
            keywords = setOf(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private", "protected", "public",
                "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
                "null", "var", "record", "sealed", "permits",
            ),
            types = setOf(
                "String", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte",
                "Short", "Object", "Void", "List", "Map", "Set", "ArrayList", "HashMap",
                "Exception", "RuntimeException",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\''), annotation = true,
        ),
        "c" to LangRules(
            keywords = setOf(
                "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
                "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
                "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
                "switch", "typedef", "union", "unsigned", "void", "volatile", "while", "true",
                "false", "NULL",
            ),
            types = setOf(
                "size_t", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t",
                "uint32_t", "uint64_t", "FILE", "va_list",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\''),
        ),
        "cpp" to LangRules(
            keywords = setOf(
                "auto", "bool", "break", "case", "catch", "char", "class", "const", "constexpr",
                "continue", "default", "delete", "do", "double", "else", "enum", "explicit", "export",
                "extern", "false", "float", "for", "friend", "goto", "if", "inline", "int", "long",
                "namespace", "new", "nullptr", "operator", "private", "protected", "public", "register",
                "return", "short", "signed", "sizeof", "static", "struct", "switch", "template",
                "this", "throw", "true", "try", "typedef", "typename", "union", "unsigned", "using",
                "virtual", "void", "volatile", "while", "override", "final", "NULL",
            ),
            types = setOf(
                "size_t", "string", "vector", "map", "set", "unordered_map", "shared_ptr",
                "unique_ptr", "weak_ptr", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t",
                "uint16_t", "uint32_t", "uint64_t",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\''),
        ),
        "csharp" to LangRules(
            keywords = setOf(
                "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked",
                "class", "const", "continue", "decimal", "default", "delegate", "do", "double", "else",
                "enum", "event", "explicit", "extern", "false", "finally", "fixed", "float", "for",
                "foreach", "goto", "if", "implicit", "in", "int", "interface", "internal", "is",
                "lock", "long", "namespace", "new", "null", "object", "operator", "out", "override",
                "params", "private", "protected", "public", "readonly", "ref", "return", "sbyte",
                "sealed", "short", "sizeof", "stackalloc", "static", "string", "struct", "switch",
                "this", "throw", "true", "try", "typeof", "uint", "ulong", "unchecked", "unsafe",
                "ushort", "using", "virtual", "void", "volatile", "while", "var", "async", "await",
                "record", "init",
            ),
            types = setOf(
                "String", "Int32", "Int64", "Double", "Boolean", "Char", "Object", "Void",
                "List", "Dictionary", "Exception", "Task", "Func", "Action",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\''),
        ),
        "javascript" to LangRules(
            keywords = setOf(
                "var", "let", "const", "function", "return", "if", "else", "for", "while", "do",
                "switch", "case", "break", "continue", "new", "delete", "typeof", "instanceof", "in",
                "of", "try", "catch", "finally", "throw", "class", "extends", "super", "this",
                "import", "export", "default", "from", "async", "await", "yield", "null", "undefined",
                "true", "false", "void", "static", "get", "set", "constructor", "debugger",
            ),
            types = setOf(
                "number", "string", "boolean", "object", "symbol", "bigint", "any", "unknown",
                "never", "Array", "Promise", "Map", "Set", "Function", "Record",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\'', '`'),
        ),
        "typescript" to LangRules(
            keywords = setOf(
                "var", "let", "const", "function", "return", "if", "else", "for", "while", "do",
                "switch", "case", "break", "continue", "new", "delete", "typeof", "instanceof", "in",
                "of", "try", "catch", "finally", "throw", "class", "extends", "super", "this",
                "import", "export", "default", "from", "async", "await", "yield", "null", "undefined",
                "true", "false", "void", "static", "get", "set", "constructor", "debugger", "interface",
                "type", "enum", "namespace", "declare", "readonly", "keyof", "as", "implements",
                "public", "private", "protected", "abstract",
            ),
            types = setOf(
                "number", "string", "boolean", "object", "symbol", "bigint", "any", "unknown",
                "never", "Array", "Promise", "Map", "Set", "Function", "Record", "void",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\'', '`'), annotation = true,
        ),
        "python" to LangRules(
            keywords = setOf(
                "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
                "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
                "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
                "return", "try", "while", "with", "yield", "match", "case",
            ),
            types = setOf(
                "int", "float", "str", "bool", "bytes", "list", "tuple", "dict", "set", "frozenset",
                "object", "type", "Exception",
            ),
            lineComment = "#",
            strings = listOf('"', '\''), annotation = true,
        ),
        "go" to LangRules(
            keywords = setOf(
                "break", "case", "chan", "const", "continue", "default", "defer", "else",
                "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
                "package", "range", "return", "select", "struct", "switch", "type", "var", "nil",
                "true", "false",
            ),
            types = setOf(
                "int", "int8", "int16", "int32", "int64", "uint", "uint8", "uint16", "uint32",
                "uint64", "uintptr", "float32", "float64", "complex64", "complex128", "byte", "rune",
                "string", "bool", "error", "any",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\'', '`'),
        ),
        "rust" to LangRules(
            keywords = setOf(
                "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
                "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod",
                "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super",
                "trait", "true", "type", "unsafe", "use", "where", "while",
            ),
            types = setOf(
                "i8", "i16", "i32", "i64", "i128", "u8", "u16", "u32", "u64", "u128", "isize",
                "usize", "f32", "f64", "bool", "char", "str", "String", "Vec", "Option", "Result",
                "Box", "Rc", "Arc",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\''),
            annotation = true,
        ),
        "ruby" to LangRules(
            keywords = setOf(
                "alias", "and", "begin", "break", "case", "class", "def", "defined", "do", "else",
                "elsif", "end", "ensure", "false", "for", "if", "in", "module", "next", "nil", "not",
                "or", "redo", "rescue", "retry", "return", "self", "super", "then", "true", "undef",
                "unless", "until", "when", "while", "yield", "require", "require_relative",
                "attr_accessor", "attr_reader", "attr_writer",
            ),
            lineComment = "#", blockCommentStart = "=begin", blockCommentEnd = "=end",
            strings = listOf('"', '\''),
        ),
        "php" to LangRules(
            keywords = setOf(
                "abstract", "and", "array", "as", "break", "callable", "case", "catch", "class",
                "clone", "const", "continue", "declare", "default", "die", "do", "echo", "else",
                "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif", "endswitch",
                "endwhile", "eval", "exit", "extends", "final", "finally", "fn", "for", "foreach",
                "function", "global", "goto", "if", "implements", "include", "include_once",
                "instanceof", "insteadof", "interface", "isset", "list", "match", "namespace", "new",
                "or", "print", "private", "protected", "public", "readonly", "require", "require_once",
                "return", "static", "switch", "throw", "trait", "try", "unset", "use", "var", "while",
                "xor", "yield", "true", "false", "null",
            ),
            types = setOf(
                "string", "int", "float", "bool", "array", "object", "mixed", "void", "iterable",
                "callable", "resource",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\''), annotation = true,
        ),
        "swift" to LangRules(
            keywords = setOf(
                "as", "async", "await", "associatedtype", "break", "case", "catch", "class",
                "continue", "convenience", "default", "defer", "deinit", "didSet", "do", "dynamic",
                "else", "enum", "extension", "fallthrough", "fileprivate", "final", "for", "func",
                "get", "guard", "if", "import", "in", "indirect", "infix", "init", "inout", "internal",
                "is", "lazy", "let", "mutating", "nil", "open", "operator", "optional", "override",
                "postfix", "precedence", "prefix", "private", "protocol", "public", "repeat",
                "required", "rethrows", "return", "set", "some", "static", "struct", "subscript",
                "super", "switch", "throw", "throws", "true", "try", "typealias", "unowned", "var",
                "weak", "where", "while", "willSet", "false",
            ),
            types = setOf(
                "Int", "Int8", "Int16", "Int32", "Int64", "UInt", "UInt8", "UInt16", "UInt32",
                "UInt64", "Double", "Float", "Bool", "String", "Character", "Array", "Dictionary",
                "Set", "Optional", "Any", "AnyObject", "Void", "Never",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\''),
        ),
        "shell" to LangRules(
            keywords = setOf(
                "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case",
                "esac", "function", "in", "select", "time", "export", "readonly", "local", "return",
                "break", "continue", "exit", "shift", "set", "unset", "trap", "eval", "exec", "source",
                "true", "false",
            ),
            lineComment = "#",
            strings = listOf('"', '\''),
        ),
        "batch" to LangRules(
            keywords = setOf(
                "echo", "set", "if", "else", "for", "goto", "call", "exit", "pause", "rem", "cd",
                "copy", "del", "dir", "mkdir", "move", "ren", "start", "timeout", "title", "cls",
            ),
            lineComment = "::",
            strings = listOf('"'),
            caseInsensitive = true,
        ),
        "sql" to LangRules(
            keywords = setOf(
                "SELECT", "INSERT", "UPDATE", "DELETE", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT",
                "INNER", "OUTER", "ON", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "CREATE",
                "ALTER", "DROP", "TABLE", "INDEX", "VIEW", "TRIGGER", "PROCEDURE", "FUNCTION",
                "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "NOT", "NULL", "DEFAULT", "AND",
                "OR", "IN", "EXISTS", "BETWEEN", "LIKE", "IS", "AS", "DISTINCT", "UNION", "ALL",
                "CASE", "WHEN", "THEN", "ELSE", "END", "INTO", "VALUES", "SET", "DESC", "ASC",
            ),
            lineComment = "--", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('\'', '"'),
            caseInsensitive = true,
        ),
        "groovy" to LangRules(
            keywords = setOf(
                "def", "class", "interface", "trait", "enum", "extends", "implements", "import",
                "package", "new", "return", "if", "else", "for", "while", "do", "switch", "case",
                "break", "continue", "true", "false", "null", "void", "public", "private", "protected",
                "static", "final", "abstract", "this", "super", "in", "instanceof", "as", "try",
                "catch", "finally", "throw", "throws", "assert", "synchronized",
            ),
            types = setOf(
                "String", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte",
                "Short", "Object", "List", "Map", "Set", "Closure",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\'', '`'), annotation = true,
        ),
        "dart" to LangRules(
            keywords = setOf(
                "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class",
                "const", "continue", "covariant", "default", "deferred", "do", "dynamic", "else",
                "enum", "export", "extends", "extension", "external", "factory", "false", "final",
                "finally", "for", "get", "hide", "if", "implements", "import", "in", "interface",
                "is", "late", "library", "mixin", "new", "null", "on", "operator", "part", "required",
                "rethrow", "return", "set", "show", "static", "super", "switch", "sync", "this",
                "throw", "true", "try", "typedef", "var", "void", "while", "with", "yield",
            ),
            types = setOf(
                "int", "double", "num", "String", "bool", "Object", "List", "Map", "Set", "Function",
                "Future", "Stream", "Never", "dynamic",
            ),
            lineComment = "//", blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\''),
        ),
        "json" to LangRules(
            keywords = setOf("true", "false", "null"),
            strings = listOf('"'),
        ),
        "yaml" to LangRules(
            keywords = setOf("true", "false", "null", "yes", "no", "on", "off"),
            lineComment = "#",
            strings = listOf('"', '\''),
        ),
        "xml" to LangRules(
            blockCommentStart = "<!--", blockCommentEnd = "-->",
            strings = listOf('"', '\''),
            xml = true,
        ),
        "html" to LangRules(
            blockCommentStart = "<!--", blockCommentEnd = "-->",
            strings = listOf('"', '\''),
            xml = true,
        ),
        "css" to LangRules(
            blockCommentStart = "/*", blockCommentEnd = "*/",
            strings = listOf('"', '\''),
            css = true,
        ),
        "markdown" to LangRules(markdown = true),
        "properties" to LangRules(
            lineComment = "#",
            strings = listOf('"', '\''),
        ),
        "toml" to LangRules(
            keywords = setOf("true", "false"),
            lineComment = "#",
            strings = listOf('"', '\''),
        ),
    )
}
