package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * A deliberately small, dependency-free highlighter: a handful of pre-compiled regexes per language
 * producing flat, non-overlapping spans. It is not a parser and does not try to be — it only has to
 * make structure readable, and it has to stay cheap enough to re-run as the user types.
 */
enum class CodeLanguage { NONE, JSON, XML, CODE, MARKDOWN, PROPERTIES, SHELL }

private enum class SpanKind { KEYWORD, STRING, NUMBER, COMMENT, TAG, ATTRIBUTE, HEADING }

fun languageForExtension(extension: String): CodeLanguage = when (extension.lowercase()) {
    "json" -> CodeLanguage.JSON
    "xml", "html", "htm", "svg", "xhtml" -> CodeLanguage.XML
    "kt", "kts", "java", "js", "jsx", "ts", "tsx", "c", "h", "cpp", "hpp", "cs", "py", "rb", "go",
    "rs", "swift", "gradle", "css", "scss" -> CodeLanguage.CODE
    "md", "markdown" -> CodeLanguage.MARKDOWN
    "properties", "ini", "conf", "cfg", "toml", "env" -> CodeLanguage.PROPERTIES
    "sh", "bash", "zsh" -> CodeLanguage.SHELL
    else -> CodeLanguage.NONE
}

// Compiled once at class-load; never rebuilt per keystroke.
private val STRING_REGEX = Regex("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'")
private val NUMBER_REGEX = Regex("\\b-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b")
private val LINE_COMMENT_REGEX = Regex("//[^\\n]*")
private val HASH_COMMENT_REGEX = Regex("#[^\\n]*")
private val BLOCK_COMMENT_REGEX = Regex("/\\*[\\s\\S]*?\\*/")
private val XML_COMMENT_REGEX = Regex("<!--[\\s\\S]*?-->")
private val XML_TAG_REGEX = Regex("</?[A-Za-z_][\\w.:-]*|/?>")
private val XML_ATTRIBUTE_REGEX = Regex("\\b[A-Za-z_][\\w.:-]*(?=\\s*=)")
private val JSON_KEY_REGEX = Regex("\"(\\\\.|[^\"\\\\])*\"(?=\\s*:)")
private val MARKDOWN_HEADING_REGEX = Regex("(?m)^#{1,6} [^\\n]*")
private val MARKDOWN_EMPHASIS_REGEX = Regex("\\*\\*[^*\\n]+\\*\\*|`[^`\\n]+`")
private val PROPERTIES_KEY_REGEX = Regex("(?m)^\\s*[\\w.\\-]+(?=\\s*[=:])")
private val PROPERTIES_SECTION_REGEX = Regex("(?m)^\\s*\\[[^\\]\\n]*]")
private val SHELL_KEYWORD_REGEX = Regex(
    "\\b(if|then|else|elif|fi|for|while|do|done|case|esac|function|return|export|local|in)\\b"
)
private val CODE_KEYWORD_REGEX = Regex(
    "\\b(abstract|as|break|by|catch|class|companion|const|constructor|continue|data|def|delete|do|" +
        "elif|else|enum|export|extends|false|final|finally|for|from|fun|function|get|if|implements|" +
        "import|in|infix|init|inline|instanceof|interface|internal|is|it|lateinit|let|match|new|null|" +
        "object|open|operator|override|package|params|private|protected|public|return|sealed|self|set|" +
        "static|struct|super|suspend|switch|this|throw|throws|trait|true|try|typealias|use|val|var|" +
        "void|when|where|while|with|yield)\\b"
)

private data class Span(val range: IntRange, val kind: SpanKind)

/**
 * Builds the styled text for [text]. Callers must memoize on (text, language, colorScheme) and must
 * not call this for documents past the editor's highlighting budget.
 */
fun highlight(text: String, language: CodeLanguage, colors: ColorScheme): AnnotatedString {
    if (language == CodeLanguage.NONE || text.isEmpty()) return AnnotatedString(text)

    val spans = mutableListOf<Span>()
    when (language) {
        CodeLanguage.JSON -> {
            spans += JSON_KEY_REGEX.spans(text, SpanKind.ATTRIBUTE)
            spans += STRING_REGEX.spans(text, SpanKind.STRING)
            spans += NUMBER_REGEX.spans(text, SpanKind.NUMBER)
            spans += Regex("\\b(true|false|null)\\b").spans(text, SpanKind.KEYWORD)
        }
        CodeLanguage.XML -> {
            spans += XML_COMMENT_REGEX.spans(text, SpanKind.COMMENT)
            spans += XML_TAG_REGEX.spans(text, SpanKind.TAG)
            spans += XML_ATTRIBUTE_REGEX.spans(text, SpanKind.ATTRIBUTE)
            spans += STRING_REGEX.spans(text, SpanKind.STRING)
        }
        CodeLanguage.CODE -> {
            spans += BLOCK_COMMENT_REGEX.spans(text, SpanKind.COMMENT)
            spans += LINE_COMMENT_REGEX.spans(text, SpanKind.COMMENT)
            spans += STRING_REGEX.spans(text, SpanKind.STRING)
            spans += CODE_KEYWORD_REGEX.spans(text, SpanKind.KEYWORD)
            spans += NUMBER_REGEX.spans(text, SpanKind.NUMBER)
        }
        CodeLanguage.MARKDOWN -> {
            spans += MARKDOWN_HEADING_REGEX.spans(text, SpanKind.HEADING)
            spans += MARKDOWN_EMPHASIS_REGEX.spans(text, SpanKind.KEYWORD)
        }
        CodeLanguage.PROPERTIES -> {
            spans += HASH_COMMENT_REGEX.spans(text, SpanKind.COMMENT)
            spans += PROPERTIES_SECTION_REGEX.spans(text, SpanKind.HEADING)
            spans += PROPERTIES_KEY_REGEX.spans(text, SpanKind.ATTRIBUTE)
        }
        CodeLanguage.SHELL -> {
            spans += HASH_COMMENT_REGEX.spans(text, SpanKind.COMMENT)
            spans += STRING_REGEX.spans(text, SpanKind.STRING)
            spans += SHELL_KEYWORD_REGEX.spans(text, SpanKind.KEYWORD)
        }
        CodeLanguage.NONE -> Unit
    }

    // Earlier rules win: comments and strings are added first so a keyword inside them is dropped.
    val taken = BooleanArray(text.length)
    return AnnotatedString.Builder(text).apply {
        for (span in spans) {
            val start = span.range.first
            val end = (span.range.last + 1).coerceAtMost(text.length)
            if (start >= end) continue
            if ((start until end).any { taken[it] }) continue
            for (index in start until end) taken[index] = true
            addStyle(span.kind.style(colors), start, end)
        }
    }.toAnnotatedString()
}

private fun Regex.spans(text: String, kind: SpanKind): List<Span> =
    findAll(text).map { Span(it.range, kind) }.toList()

private fun SpanKind.style(colors: ColorScheme): SpanStyle = when (this) {
    SpanKind.KEYWORD -> SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)
    SpanKind.STRING -> SpanStyle(color = colors.tertiary)
    SpanKind.NUMBER -> SpanStyle(color = colors.secondary)
    SpanKind.COMMENT -> SpanStyle(
        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
        fontStyle = FontStyle.Italic,
    )
    SpanKind.TAG -> SpanStyle(color = colors.primary)
    SpanKind.ATTRIBUTE -> SpanStyle(color = colors.secondary)
    SpanKind.HEADING -> SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)
}

/** Paints every search hit, with the active one given a stronger background. */
fun applyMatchHighlights(
    source: AnnotatedString,
    matches: List<Int>,
    queryLength: Int,
    currentMatch: Int,
    activeColor: Color,
    inactiveColor: Color,
): AnnotatedString {
    if (matches.isEmpty() || queryLength <= 0) return source
    return AnnotatedString.Builder(source).apply {
        matches.forEachIndexed { index, start ->
            val end = (start + queryLength).coerceAtMost(source.length)
            if (start >= end) return@forEachIndexed
            addStyle(
                SpanStyle(background = if (index == currentMatch) activeColor else inactiveColor),
                start,
                end,
            )
        }
    }.toAnnotatedString()
}
