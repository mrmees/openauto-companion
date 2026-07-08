package org.openauto.companion.net

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ThemeTransferTest {
    @Test
    fun buildInstallUrl_usesDefaultHeadUnitHostAndWebConfigPort() {
        val url = ThemeTransfer.buildInstallUrl(null)

        assertEquals("http://10.0.0.1:8080/api/theme/install", url.toString())
    }

    @Test
    fun buildInstallUrl_trimsHostAndUsesWebConfigPort() {
        val url = ThemeTransfer.buildInstallUrl("  10.0.0.23  ")

        assertEquals("http://10.0.0.23:8080/api/theme/install", url.toString())
    }

    @Test
    fun sendToUrl_postsManifestAsPlainFormFieldAndWallpaperAsJpegFile() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"installed":true,"slug":"test-theme","applied":true}"""))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            val body = request.body.readUtf8()

            assertTrue(result is ThemeTransfer.TransferResult.Success)
            assertEquals("POST", request.method)
            assertEquals("/api/theme/install", request.path)
            assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
            assertTrue(body.contains("Content-Disposition: form-data; name=\"manifest\""))
            assertFalse(body.contains("Content-Disposition: form-data; name=\"manifest\"; filename="))
            assertTrue(body.contains("\"name\":\"Test Theme\""))
            assertTrue(body.contains("\"light\":{\"primary\":\"#FF8A65\"}"))
            assertTrue(body.contains("\"dark\":{\"primary\":\"#FFB59D\"}"))
            assertTrue(body.contains("Content-Disposition: form-data; name=\"wallpaper\"; filename=\"wallpaper.jpg\""))
            assertTrue(body.contains("Content-Type: image/jpeg"))
            assertTrue(body.contains("jpeg-bytes"))
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_omitsWallpaperPartWhenWallpaperBytesAreEmpty() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"installed":true,"slug":"color-only","applied":true}"""))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = ByteArray(0),
                client = client
            )

            val body = server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()

            assertTrue(result is ThemeTransfer.TransferResult.Success)
            assertTrue(body.contains("Content-Disposition: form-data; name=\"manifest\""))
            assertFalse(body.contains("name=\"wallpaper\""))
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsInstalledFalseResponseToFailedReason() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"installed":false,"error":"missing manifest"}"""))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            assertEquals(ThemeTransfer.TransferResult.Failed("missing manifest"), result)
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsPayloadTooLargeResponseToFailedReason() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(413).setBody("""{"installed":false}"""))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            assertEquals(ThemeTransfer.TransferResult.Failed("payload too large"), result)
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsQtAppNotRunningResponseToFailedReason() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"installed":false}"""))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            assertEquals(ThemeTransfer.TransferResult.Failed("Qt app not running"), result)
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsNonJsonPayloadTooLargeResponseToStatusFallback() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(413).setBody("<html>too large</html>"))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            assertEquals(ThemeTransfer.TransferResult.Failed("payload too large"), result)
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsMalformedJsonResponseToFailedReason() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        server.start()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = server.url("/api/theme/install"),
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            assertTrue(result is ThemeTransfer.TransferResult.Failed)
            assertTrue((result as ThemeTransfer.TransferResult.Failed).reason.startsWith("Invalid response:"))
        } finally {
            client.shutdownForTest()
            server.shutdown()
        }
    }

    @Test
    fun sendToUrl_mapsNetworkFailureToFailedReason() {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.start()
        val url = server.url("/api/theme/install")
        server.shutdown()

        try {
            val result = ThemeTransfer.sendToUrl(
                url = url,
                themeJson = sampleTheme(),
                wallpaperBytes = "jpeg-bytes".toByteArray(),
                client = client
            )

            assertTrue(result is ThemeTransfer.TransferResult.Failed)
            assertTrue((result as ThemeTransfer.TransferResult.Failed).reason.isNotBlank())
        } finally {
            client.shutdownForTest()
        }
    }

    private fun sampleTheme(): JSONObject = JSONObject().apply {
        put("name", "Test Theme")
        put("seed", "#FF8A65")
        put("light", JSONObject().apply {
            put("primary", "#FF8A65")
        })
        put("dark", JSONObject().apply {
            put("primary", "#FFB59D")
        })
    }

    private fun OkHttpClient.shutdownForTest() {
        dispatcher.executorService.shutdown()
        connectionPool.evictAll()
    }
}
