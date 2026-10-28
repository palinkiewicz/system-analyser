package pl.dakil.appanalyser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareEndpointTest {

    @Test
    fun `displayed url drops the trailing slash`() {
        val endpoint = ShareEndpoint("192.168.1.42", 8080, "/dakil_share/")

        assertEquals("http://192.168.1.42:8080/dakil_share", endpoint.url)
    }

    @Test
    fun `displayed url drops the trailing slash in token mode too`() {
        val endpoint = ShareEndpoint("192.168.1.42", 8080, "/dakil_share/a7f3c9d1/")

        assertEquals("http://192.168.1.42:8080/dakil_share/a7f3c9d1", endpoint.url)
    }

    @Test
    fun `link keeps the trailing slash so it needs no redirect`() {
        val endpoint = ShareEndpoint("192.168.1.42", 8080, "/dakil_share/a7f3c9d1/")

        assertEquals("http://192.168.1.42:8080/dakil_share/a7f3c9d1/", endpoint.link)
    }

    @Test
    fun `base path is left untouched for routing`() {
        val endpoint = ShareEndpoint("192.168.1.42", 8080, "/dakil_share/")

        assertEquals("/dakil_share/", endpoint.basePath)
    }
}
