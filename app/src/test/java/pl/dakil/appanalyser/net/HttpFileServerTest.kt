package pl.dakil.appanalyser.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.dakil.appanalyser.domain.ShareAccessMode
import java.io.File
import java.net.Socket
import java.nio.file.Files

/**
 * Exercises the real [HttpFileServer] over a loopback socket. These cover the parts most likely to
 * be wrong — request parsing, path traversal, byte ranges and the access gates — without a device.
 */
class HttpFileServerTest {

    private lateinit var root: File
    private lateinit var outside: File
    private var server: HttpFileServer? = null
    private var port: Int = 0

    @Before
    fun setUp() {
        root = Files.createTempDirectory("share-root").toFile()
        File(root, "notes.txt").writeText("hello from the share server")
        File(root, "nested").mkdirs()
        File(root, "nested/data.json").writeText("{\"a\":1}")
        File(root, "big.bin").writeBytes(ByteArray(4096) { (it % 251).toByte() })
        // A sibling of the root that must never be reachable through the server.
        outside = File(root.parentFile, "secret-${root.name}.txt")
        outside.writeText("TOP SECRET")
    }

    @After
    fun tearDown() {
        server?.stop()
        root.deleteRecursively()
        outside.delete()
    }

    private fun start(mode: ShareAccessMode = ShareAccessMode.OPEN): HttpFileServer {
        val instance = HttpFileServer(root, mode, onRequestStart = {}, onRequestEnd = {})
        port = requireNotNull(instance.start()) { "server failed to bind" }
        server = instance
        return instance
    }

    /** Raw request/response so header handling is tested as written, not through a client library. */
    private fun request(rawRequest: String): Pair<String, ByteArray> {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().apply {
                write(rawRequest.toByteArray(Charsets.UTF_8))
                flush()
            }
            val all = socket.getInputStream().readBytes()
            val separator = indexOfHeaderEnd(all)
            val headers = String(all, 0, separator, Charsets.UTF_8)
            val body = all.copyOfRange((separator + 4).coerceAtMost(all.size), all.size)
            return headers to body
        }
    }

    private fun indexOfHeaderEnd(bytes: ByteArray): Int {
        for (index in 0..bytes.size - 4) {
            if (bytes[index] == '\r'.code.toByte() && bytes[index + 1] == '\n'.code.toByte() &&
                bytes[index + 2] == '\r'.code.toByte() && bytes[index + 3] == '\n'.code.toByte()
            ) {
                return index
            }
        }
        return bytes.size
    }

    private fun get(path: String, extraHeaders: String = ""): Pair<String, ByteArray> =
        request("GET $path HTTP/1.1\r\nHost: localhost\r\n$extraHeaders\r\n")

    private fun status(headers: String): Int = headers.lineSequence().first().split(' ')[1].toInt()

    // -----------------------------------------------------------------------

    @Test
    fun `serves a directory listing at the base path`() {
        val instance = start()
        val (headers, body) = get(instance.basePath)
        val html = String(body, Charsets.UTF_8)

        assertEquals(200, status(headers))
        assertTrue(headers.contains("text/html"))
        assertTrue(html.contains("notes.txt"))
        assertTrue(html.contains("nested"))
        // Folders come before files, matching the in-app ordering.
        assertTrue(html.indexOf("nested") < html.indexOf("notes.txt"))
    }

    @Test
    fun `listing page stays small and has no external references`() {
        val instance = start()
        val html = String(get(instance.basePath).second, Charsets.UTF_8)

        assertTrue("page was ${html.length} bytes", html.length < 3500)
        assertFalse(html.contains("http://"))
        assertFalse(html.contains("https://"))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun `serves file contents`() {
        val instance = start()
        val (headers, body) = get("${instance.basePath}notes.txt")

        assertEquals(200, status(headers))
        assertEquals("hello from the share server", String(body, Charsets.UTF_8))
        assertTrue(headers.contains("Accept-Ranges: bytes"))
    }

    @Test
    fun `listing offers a download link per file but not per folder`() {
        val instance = start()
        val html = String(get(instance.basePath).second, Charsets.UTF_8)

        assertTrue(html.contains("href=\"${instance.basePath}notes.txt?dl=1\""))
        assertTrue(html.contains("href=\"${instance.basePath}big.bin?dl=1\""))
        // A folder cannot be downloaded as one file, so it gets no button.
        assertFalse(html.contains("nested?dl=1"))
        // notes.txt and big.bin get a button; the `nested` folder does not.
        assertEquals(2, Regex("class=\"dl\"").findAll(html).count())
    }

    @Test
    fun `download link forces an attachment instead of inline display`() {
        val instance = start()
        val (headers, body) = get("${instance.basePath}notes.txt?dl=1")

        assertEquals(200, status(headers))
        assertTrue(headers.contains("Content-Disposition: attachment; filename=\"notes.txt\""))
        assertEquals("hello from the share server", String(body, Charsets.UTF_8))
    }

    @Test
    fun `plain link stays inline so media can preview`() {
        val instance = start()
        val (headers, _) = get("${instance.basePath}notes.txt")

        assertFalse(headers.contains("Content-Disposition"))
    }

    @Test
    fun `serves a nested file`() {
        val instance = start()
        val (headers, body) = get("${instance.basePath}nested/data.json")

        assertEquals(200, status(headers))
        assertEquals("{\"a\":1}", String(body, Charsets.UTF_8))
    }

    @Test
    fun `honours a byte range`() {
        val instance = start()
        val (headers, body) = get("${instance.basePath}big.bin", "Range: bytes=0-99\r\n")

        assertEquals(206, status(headers))
        assertTrue(headers.contains("Content-Range: bytes 0-99/4096"))
        assertEquals(100, body.size)
    }

    @Test
    fun `honours a suffix range`() {
        val instance = start()
        val (headers, body) = get("${instance.basePath}big.bin", "Range: bytes=-50\r\n")

        assertEquals(206, status(headers))
        assertEquals(50, body.size)
    }

    @Test
    fun `rejects an unsatisfiable range`() {
        val instance = start()
        val (headers, _) = get("${instance.basePath}big.bin", "Range: bytes=99999-\r\n")

        assertEquals(416, status(headers))
    }

    @Test
    fun `HEAD returns headers without a body`() {
        val instance = start()
        val (headers, body) = request(
            "HEAD ${instance.basePath}notes.txt HTTP/1.1\r\nHost: localhost\r\n\r\n"
        )

        assertEquals(200, status(headers))
        assertTrue(headers.contains("Content-Length: 27"))
        assertEquals(0, body.size)
    }

    @Test
    fun `refuses path traversal with dot segments`() {
        val instance = start()
        val (headers, body) = get("${instance.basePath}../${outside.name}")

        assertEquals(404, status(headers))
        assertFalse(String(body, Charsets.UTF_8).contains("TOP SECRET"))
    }

    @Test
    fun `refuses percent-encoded path traversal`() {
        val instance = start()
        val (headers, body) = get("${instance.basePath}%2e%2e%2f${outside.name}")

        assertEquals(404, status(headers))
        assertFalse(String(body, Charsets.UTF_8).contains("TOP SECRET"))
    }

    @Test
    fun `refuses an absolute path outside the root`() {
        val instance = start()
        val (headers, body) = get("${instance.basePath}nested/../../${outside.name}")

        assertEquals(404, status(headers))
        assertFalse(String(body, Charsets.UTF_8).contains("TOP SECRET"))
    }

    @Test
    fun `escapes html in file names`() {
        // No slash in the name: a real file name cannot contain one, so `<script>` alone is the
        // realistic hostile case.
        File(root, "<script>alert(1)&\".txt").writeText("x")
        val instance = start()
        val html = String(get(instance.basePath).second, Charsets.UTF_8)

        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&amp;"))
        assertFalse(html.contains("<script>alert(1)"))
    }

    @Test
    fun `rejects unsupported methods`() {
        val instance = start()
        val (headers, _) = request(
            "DELETE ${instance.basePath}notes.txt HTTP/1.1\r\nHost: localhost\r\n\r\n"
        )

        assertEquals(405, status(headers))
    }

    @Test
    fun `redirects the site root to the share path`() {
        val instance = start()
        val (headers, _) = get("/")

        assertEquals(302, status(headers))
        assertTrue(headers.contains("Location: ${instance.basePath}"))
    }

    @Test
    fun `the slashless share path resolves`() {
        val instance = start()
        val (headers, _) = get(instance.basePath.trimEnd('/'))

        assertEquals(302, status(headers))
        assertTrue(headers.contains("Location: ${instance.basePath}"))
    }

    @Test
    fun `token mode hides the bare share path`() {
        val instance = start(ShareAccessMode.TOKEN)

        assertTrue(instance.basePath.startsWith("/dakil_share/"))
        assertTrue(instance.basePath.length > "/dakil_share/".length)
        assertEquals(404, status(get("/dakil_share/").first))
        assertEquals(404, status(get("/dakil_share").first))
        assertEquals(200, status(get(instance.basePath).first))
        // The slashless form of the real link still works for whoever has it.
        assertEquals(302, status(get(instance.basePath.trimEnd('/')).first))
    }

    @Test
    fun `token mode never leaks the token through a redirect`() {
        val instance = start(ShareAccessMode.TOKEN)

        for (probe in listOf("/", "/dakil_share", "/dakil_share/", "/dakil_share/wrong/")) {
            val (headers, body) = get(probe)
            assertEquals("probe $probe should be a dead end", 404, status(headers))
            assertFalse(
                "token leaked via $probe",
                headers.contains(instance.token) || String(body, Charsets.UTF_8).contains(instance.token),
            )
        }
    }

    @Test
    fun `pin mode gates the listing until the code is posted`() {
        val instance = start(ShareAccessMode.PIN)
        val pin = assertNotNull(instance.pin).let { instance.pin!! }

        val (gateHeaders, gateBody) = get(instance.basePath)
        assertEquals(200, status(gateHeaders))
        val gateHtml = String(gateBody, Charsets.UTF_8)
        assertTrue(gateHtml.contains("Enter code"))
        assertFalse(gateHtml.contains("notes.txt"))

        val wrong = "pin=0000".takeIf { pin != "0000" } ?: "pin=1111"
        val (wrongHeaders, _) = request(
            "POST ${instance.basePath} HTTP/1.1\r\nHost: localhost\r\n" +
                "Content-Length: ${wrong.length}\r\n\r\n$wrong"
        )
        assertEquals(401, status(wrongHeaders))

        val correct = "pin=$pin"
        val (okHeaders, _) = request(
            "POST ${instance.basePath} HTTP/1.1\r\nHost: localhost\r\n" +
                "Content-Length: ${correct.length}\r\n\r\n$correct"
        )
        assertEquals(302, status(okHeaders))
        val cookie = okHeaders.lineSequence()
            .first { it.startsWith("Set-Cookie:") }
            .removePrefix("Set-Cookie:").trim().substringBefore(';')

        val (listingHeaders, listingBody) = get(instance.basePath, "Cookie: $cookie\r\n")
        assertEquals(200, status(listingHeaders))
        assertTrue(String(listingBody, Charsets.UTF_8).contains("notes.txt"))
    }

    @Test
    fun `open mode issues no pin and no token`() {
        val instance = start(ShareAccessMode.OPEN)

        assertEquals("/dakil_share/", instance.basePath)
        assertEquals(null, instance.pin)
        assertEquals("", instance.token)
    }

    @Test
    fun `stops accepting after stop`() {
        val instance = start()
        assertEquals(200, status(get(instance.basePath).first))

        instance.stop()
        server = null

        val refused = runCatching { Socket("127.0.0.1", port).close() }
        assertTrue("port should be closed", refused.isFailure)
    }
}
