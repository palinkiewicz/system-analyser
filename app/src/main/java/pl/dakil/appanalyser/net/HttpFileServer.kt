package pl.dakil.appanalyser.net

import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import pl.dakil.appanalyser.domain.ShareAccessMode
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * A minimal read-only HTTP/1.1 server for one shared file or folder.
 *
 * Hand-rolled rather than pulled from a library: the whole surface is `GET`, `HEAD` and a single
 * `POST` for the PIN form, which is a few hundred lines and no dependency. Every response closes
 * its connection, so there is no keep-alive state machine to get wrong — and since the page inlines
 * its CSS, a full render is one request anyway.
 *
 * Cost when idle is a thread parked in [ServerSocket.accept]; there is no polling of any kind.
 */
class HttpFileServer(
    private val root: File,
    private val accessMode: ShareAccessMode,
    /** Ref-counted around live request handling so the CPU wake lock is only held mid-transfer. */
    private val onRequestStart: () -> Unit,
    private val onRequestEnd: () -> Unit,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionLimit = Semaphore(MAX_CONNECTIONS)
    private val random = SecureRandom()

    private var serverSocket: ServerSocket? = null
    private val rootCanonical: String = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)

    /** Secret path segment in [ShareAccessMode.TOKEN]; empty otherwise. */
    val token: String = if (accessMode == ShareAccessMode.TOKEN) randomHex(TOKEN_BYTES) else ""

    /** The 4-digit code in [ShareAccessMode.PIN]; null otherwise. */
    val pin: String? = if (accessMode == ShareAccessMode.PIN) {
        (random.nextInt(10_000)).toString().padStart(4, '0')
    } else {
        null
    }

    /** Handed out as a cookie once the PIN is accepted; regenerated per session. */
    private val sessionCookie: String = randomHex(TOKEN_BYTES)

    val basePath: String = if (token.isEmpty()) SHARE_PATH else "$SHARE_PATH$token/"

    private val lastActivity = AtomicLong(System.currentTimeMillis())

    fun millisSinceLastRequest(): Long = System.currentTimeMillis() - lastActivity.get()

    /**
     * Binds and starts accepting. Returns the bound port, or null when even the ephemeral bind
     * failed. Prefers [PREFERRED_PORT] so the advertised URL is short and memorable.
     */
    fun start(): Int? {
        val socket = bind() ?: return null
        serverSocket = socket
        scope.launch {
            while (isActive && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break // Closed by stop(); nothing else to do.
                }
                launch {
                    connectionLimit.withPermit { handle(client) }
                }
            }
        }
        return socket.localPort
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope.cancel()
    }

    private fun bind(): ServerSocket? =
        runCatching { ServerSocket(PREFERRED_PORT) }.getOrNull()
            ?: runCatching { ServerSocket(0) }.getOrNull()

    // -----------------------------------------------------------------------
    // Connection handling
    // -----------------------------------------------------------------------

    private fun handle(socket: Socket) {
        onRequestStart()
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.tcpNoDelay = true
            val input = socket.getInputStream().buffered()
            val output = BufferedOutputStream(socket.getOutputStream())
            val request = parseRequest(input) ?: run {
                respondText(output, 400, "Bad Request", SharePage.message("400", "Malformed request."))
                return
            }
            lastActivity.set(System.currentTimeMillis())
            route(request, input, output)
            output.flush()
        } catch (e: IOException) {
            // Client vanished mid-transfer; nothing recoverable, and nothing worth logging.
        } finally {
            runCatching { socket.close() }
            onRequestEnd()
        }
    }

    private data class Request(
        val method: String,
        val path: String,
        val query: String,
        val headers: Map<String, String>,
        val contentLength: Int,
    )

    /** Bounded parse: a client cannot make us read an unbounded header block. */
    private fun parseRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "")

        val headers = HashMap<String, String>()
        var consumed = requestLine.length
        var lines = 0
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            consumed += line.length
            if (++lines > MAX_HEADER_LINES || consumed > MAX_HEADER_BYTES) return null
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }
        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
        return Request(method, path, query, headers, contentLength)
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder(64)
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (builder.isEmpty()) null else builder.toString()
            if (byte == '\n'.code) return builder.toString().trimEnd('\r')
            if (builder.length > MAX_LINE_LENGTH) return null
            builder.append(byte.toChar())
        }
    }

    // -----------------------------------------------------------------------
    // Routing
    // -----------------------------------------------------------------------

    private fun route(request: Request, input: InputStream, output: OutputStream) {
        if (request.method !in setOf("GET", "HEAD", "POST")) {
            respondText(output, 405, "Method Not Allowed", SharePage.message("405", "Only GET is served."))
            return
        }

        // Anything outside the share prefix is a 404 — including a wrong token, which must be
        // indistinguishable from a path that was never there.
        if (!request.path.startsWith(basePath)) {
            if (request.path == "/" || request.path == SHARE_PATH.trimEnd('/')) {
                respondRedirect(output, basePath)
            } else {
                respondNotFound(output)
            }
            return
        }

        if (accessMode == ShareAccessMode.PIN && !isAuthorized(request)) {
            handlePin(request, input, output)
            return
        }
        if (request.method == "POST") {
            respondRedirect(output, basePath)
            return
        }

        val relative = decodePath(request.path.removePrefix(basePath))
        val target = resolve(relative)
        if (target == null || !target.exists()) {
            respondNotFound(output)
            return
        }

        if (target.isDirectory) {
            val children = target.listFiles()?.toList().orEmpty()
            val html = SharePage.listing(target, children, basePath, relative.trim('/'))
            respondText(output, 200, "OK", html, headOnly = request.method == "HEAD")
        } else {
            serveFile(target, request, output)
        }
    }

    /** Canonicalized containment check — the one thing here that must not be wrong. */
    private fun resolve(relative: String): File? {
        if (relative.isEmpty()) return root
        val candidate = runCatching { File(root, relative).canonicalFile }.getOrNull() ?: return null
        val path = candidate.path
        val inside = path == rootCanonical || path.startsWith(rootCanonical + File.separator)
        return candidate.takeIf { inside }
    }

    private fun decodePath(raw: String): String = runCatching {
        raw.split('/').joinToString("/") { URLDecoder.decode(it, "UTF-8") }
    }.getOrDefault("").trim('/')

    // -----------------------------------------------------------------------
    // PIN gate
    // -----------------------------------------------------------------------

    private fun isAuthorized(request: Request): Boolean {
        val cookies = request.headers["cookie"] ?: return false
        return cookies.split(';').any { it.trim() == "$COOKIE_NAME=$sessionCookie" }
    }

    private fun handlePin(request: Request, input: InputStream, output: OutputStream) {
        if (request.method != "POST") {
            respondText(output, 200, "OK", SharePage.pinPage(basePath, wrong = false))
            return
        }
        val body = readBody(input, request.contentLength)
        val submitted = body.split('&')
            .firstOrNull { it.startsWith("pin=") }
            ?.removePrefix("pin=")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault("") }
            ?.trim()

        if (submitted != null && submitted == pin) {
            respondRedirect(output, basePath, "$COOKIE_NAME=$sessionCookie; Path=/; HttpOnly")
        } else {
            // A fixed delay makes a brute force over 10 000 codes impractical over a LAN.
            Thread.sleep(WRONG_PIN_DELAY_MS)
            respondText(output, 401, "Unauthorized", SharePage.pinPage(basePath, wrong = true))
        }
    }

    private fun readBody(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(buffer, read, length - read)
            if (count <= 0) break
            read += count
        }
        return String(buffer, 0, read, Charsets.UTF_8)
    }

    // -----------------------------------------------------------------------
    // Responses
    // -----------------------------------------------------------------------

    /** Streams a file, honouring a single byte range so downloads resume and media can seek. */
    private fun serveFile(file: File, request: Request, output: OutputStream) {
        val length = file.length()
        val range = parseRange(request.headers["range"], length)
        if (range == null && request.headers["range"] != null) {
            respondHeaders(
                output, 416, "Range Not Satisfiable",
                mapOf("Content-Range" to "bytes */$length", "Content-Length" to "0"),
            )
            return
        }

        val start = range?.first ?: 0L
        val end = range?.second ?: (length - 1).coerceAtLeast(0L)
        val count = if (length == 0L) 0L else end - start + 1

        val headers = linkedMapOf(
            "Content-Type" to mimeType(file),
            "Content-Length" to count.toString(),
            "Accept-Ranges" to "bytes",
        )
        if (request.query.contains("dl=1")) {
            headers["Content-Disposition"] = "attachment; filename=\"${file.name.replace('"', '_')}\""
        }
        if (range != null) headers["Content-Range"] = "bytes $start-$end/$length"

        respondHeaders(output, if (range != null) 206 else 200, "OK", headers)
        if (request.method == "HEAD" || count == 0L) return

        // Streamed in fixed chunks — a shared video must never be read into the heap.
        RandomAccessFile(file, "r").use { source ->
            source.seek(start)
            val buffer = ByteArray(TRANSFER_BUFFER)
            var remaining = count
            while (remaining > 0) {
                val read = source.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.flush()
        }
    }

    /** Only the single-range form is supported; multipart ranges are not worth the code. */
    private fun parseRange(header: String?, length: Long): Pair<Long, Long>? {
        if (header == null || !header.startsWith("bytes=") || length <= 0) return null
        val spec = header.removePrefix("bytes=").substringBefore(',')
        val from = spec.substringBefore('-').trim()
        val to = spec.substringAfter('-').trim()
        val start: Long
        val end: Long
        if (from.isEmpty()) {
            val suffix = to.toLongOrNull() ?: return null
            if (suffix <= 0) return null
            start = (length - suffix).coerceAtLeast(0)
            end = length - 1
        } else {
            start = from.toLongOrNull() ?: return null
            end = to.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)
        }
        return if (start in 0 until length && end >= start) start to end else null
    }

    private fun respondText(
        output: OutputStream,
        code: Int,
        reason: String,
        html: String,
        headOnly: Boolean = false,
    ) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        respondHeaders(
            output, code, reason,
            mapOf(
                "Content-Type" to "text/html; charset=utf-8",
                "Content-Length" to bytes.size.toString(),
            ),
        )
        if (!headOnly) output.write(bytes)
    }

    private fun respondRedirect(output: OutputStream, location: String, cookie: String? = null) {
        val headers = linkedMapOf("Location" to location, "Content-Length" to "0")
        cookie?.let { headers["Set-Cookie"] = it }
        respondHeaders(output, 302, "Found", headers)
    }

    private fun respondNotFound(output: OutputStream) =
        respondText(output, 404, "Not Found", SharePage.message("404", "Nothing here."))

    private fun respondHeaders(
        output: OutputStream,
        code: Int,
        reason: String,
        headers: Map<String, String>,
    ) {
        val builder = StringBuilder(160)
        builder.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        headers.forEach { (name, value) -> builder.append(name).append(": ").append(value).append("\r\n") }
        // Served over a LAN to a browser we do not control; keep it out of caches and frames.
        builder.append("Cache-Control: no-store\r\n")
        builder.append("X-Content-Type-Options: nosniff\r\n")
        builder.append("Connection: close\r\n\r\n")
        output.write(builder.toString().toByteArray(Charsets.US_ASCII))
    }

    /** Null-safe against the platform map being unavailable, as it is under JVM unit tests. */
    private fun mimeType(file: File): String {
        val extension = file.name.substringAfterLast('.', "").lowercase()
        return runCatching { MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(extension) }
            .getOrNull() ?: "application/octet-stream"
    }

    private fun randomHex(bytes: Int): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return buffer.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SHARE_PATH = "/dakil_share/"
        const val PREFERRED_PORT = 8080
        const val MAX_CONNECTIONS = 4
        const val SOCKET_TIMEOUT_MS = 15_000
        const val TRANSFER_BUFFER = 64 * 1024
        const val MAX_HEADER_BYTES = 8 * 1024
        const val MAX_HEADER_LINES = 64
        const val MAX_LINE_LENGTH = 2048
        const val MAX_BODY_BYTES = 1024
        const val TOKEN_BYTES = 6
        const val COOKIE_NAME = "dshare"
        const val WRONG_PIN_DELAY_MS = 400L
    }
}
