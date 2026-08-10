package pl.dakil.appanalyser.net

import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The HTML served to whoever opens the share link.
 *
 * Deliberately tiny: one request, one inline stylesheet, no fonts, no scripts, no images beyond a
 * pair of inline SVG symbols. A listing of a few dozen files lands well under 3 KB, so it paints
 * essentially instantly over a LAN. Not localized — it renders for arbitrary remote browsers.
 */
internal object SharePage {

    /** Shared by every page. Kept to the cheapest rules that still look deliberate. */
    private const val STYLE = """
:root{--bg:#fff;--fg:#1b1b1f;--dim:#5c5f6b;--line:#e3e3e8;--accent:#3b5bdb}
@media(prefers-color-scheme:dark){:root{--bg:#131316;--fg:#e5e5ea;--dim:#9b9ba5;--line:#2b2b31;--accent:#8ea3f5}}
*{box-sizing:border-box}
body{margin:0;padding:24px 16px;background:var(--bg);color:var(--fg);
font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:720px;margin:0 auto}
h1{margin:0 0 2px;font-size:19px;font-weight:600}
p.sub{margin:0 0 20px;color:var(--dim);font-size:13px}
a{color:inherit;text-decoration:none}
ul{list-style:none;margin:0;padding:0;border-top:1px solid var(--line)}
li{display:flex;align-items:center;border-bottom:1px solid var(--line)}
.r{flex:1;min-width:0;display:flex;align-items:center;gap:12px;padding:11px 8px}
.r:hover,.dl:hover{background:var(--line)}
.dl{display:flex;padding:11px 10px;border-radius:8px}
.dl svg{width:18px;height:18px;fill:var(--accent)}
svg{width:20px;height:20px;flex:none;fill:var(--dim)}
.dir svg{fill:var(--accent)}
.n{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.m{color:var(--dim);font-size:12px;flex:none}
.empty{color:var(--dim);padding:24px 8px}
form{display:flex;gap:8px;margin-top:16px}
input{flex:1;padding:10px 12px;font:inherit;color:var(--fg);background:var(--bg);
border:1px solid var(--line);border-radius:8px}
button{padding:10px 18px;font:inherit;color:var(--bg);background:var(--accent);
border:0;border-radius:8px;cursor:pointer}
.err{color:#d33;font-size:13px;margin-top:12px}
"""

    /** Two symbols defined once and referenced per row, so the markup does not repeat paths. */
    private const val ICONS = """<svg style="display:none"><defs>
<g id="d"><path d="M2 5a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z"/></g>
<g id="f"><path d="M6 2h8l4 4v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zm7 1.5V7h3.5z"/></g>
<g id="dl"><path d="M11 3h2v7h3.5L12 15.5 7.5 10H11z"/><path d="M5 18h14v2H5z"/></g>
</defs></svg>"""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun listing(directory: File, entries: List<File>, basePath: String, relative: String): String {
        val body = StringBuilder(1024)
        body.append("<h1>").append(escape(directory.name)).append("</h1>")
        body.append("<p class=\"sub\">").append(breadcrumb(basePath, relative)).append("</p>")

        if (entries.isEmpty()) {
            body.append("<p class=\"empty\">This folder is empty.</p>")
        } else {
            body.append("<ul>")
            if (relative.isNotEmpty()) {
                val parent = relative.substringBeforeLast('/', "")
                body.append("<li class=\"dir\"><a class=\"r\" href=\"").append(href(basePath, parent))
                    .append("\"><svg><use href=\"#d\"/></svg><span class=\"n\">..</span></a></li>")
            }
            // Folders first, then case-insensitive by name — the same order as the in-app browser.
            entries.sortedWith(
                compareByDescending<File> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            ).forEach { entry ->
                val child = if (relative.isEmpty()) entry.name else "$relative/${entry.name}"
                val link = href(basePath, child)
                body.append("<li")
                if (entry.isDirectory) body.append(" class=\"dir\"")
                body.append("><a class=\"r\" href=\"").append(link).append("\">")
                body.append("<svg><use href=\"#").append(if (entry.isDirectory) "d" else "f")
                    .append("\"/></svg>")
                body.append("<span class=\"n\">").append(escape(entry.name)).append("</span>")
                body.append("<span class=\"m\">")
                if (!entry.isDirectory) body.append(humanSize(entry.length())).append(" &middot; ")
                body.append(dateFormat.format(Date(entry.lastModified())))
                body.append("</span></a>")
                if (!entry.isDirectory) {
                    // `?dl=1` makes the server answer with Content-Disposition: attachment, so
                    // images and audio save to disk instead of opening inline in the browser.
                    body.append("<a class=\"dl\" download title=\"Download\" href=\"")
                        .append(link).append("?dl=1\"><svg><use href=\"#dl\"/></svg></a>")
                }
                body.append("</li>")
            }
            body.append("</ul>")
        }
        return document(directory.name, body.toString())
    }

    fun pinPage(basePath: String, wrong: Boolean): String {
        val body = StringBuilder(256)
        body.append("<h1>Enter code</h1>")
        body.append("<p class=\"sub\">This share is protected with a 4-digit code.</p>")
        body.append("<form method=\"post\" action=\"").append(escape(basePath)).append("\">")
        body.append("<input name=\"pin\" inputmode=\"numeric\" maxlength=\"4\" autofocus ")
        body.append("placeholder=\"0000\"><button type=\"submit\">Open</button></form>")
        if (wrong) body.append("<p class=\"err\">Wrong code. Try again.</p>")
        return document("Enter code", body.toString())
    }

    fun message(title: String, detail: String): String =
        document(title, "<h1>$title</h1><p class=\"sub\">${escape(detail)}</p>")

    private fun document(title: String, body: String): String = buildString(1536) {
        append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>").append(escape(title)).append("</title>")
        // A data: favicon stops the browser from firing a second request for /favicon.ico.
        append("<link rel=\"icon\" href=\"data:,\">")
        append("<style>").append(STYLE).append("</style></head><body>")
        append(ICONS)
        append("<main>").append(body).append("</main></body></html>")
    }

    private fun breadcrumb(basePath: String, relative: String): String {
        if (relative.isEmpty()) return "Shared folder"
        val parts = relative.split('/')
        val builder = StringBuilder("<a href=\"").append(href(basePath, "")).append("\">Shared</a>")
        var accumulated = ""
        parts.forEachIndexed { index, part ->
            accumulated = if (accumulated.isEmpty()) part else "$accumulated/$part"
            builder.append(" / ")
            if (index == parts.lastIndex) {
                builder.append(escape(part))
            } else {
                builder.append("<a href=\"").append(href(basePath, accumulated)).append("\">")
                    .append(escape(part)).append("</a>")
            }
        }
        return builder.toString()
    }

    /** Percent-encodes each segment but keeps the slashes, so nested paths stay navigable. */
    private fun href(basePath: String, relative: String): String {
        if (relative.isEmpty()) return escape(basePath)
        val encoded = relative.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return escape(basePath + encoded)
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.0f KB".format(Locale.US, bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(Locale.US, bytes / (1024.0 * 1024 * 1024))
    }

    /** A file named `<script>alert(1)</script>.txt` has to render as text, not run. */
    private fun escape(value: String): String = buildString(value.length + 16) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}
