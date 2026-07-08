package org.openauto.companion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class NetworkSecurityConfigTest {
    @Test
    fun appAllowsCleartextOnlyForHeadUnitWebConfigHost() {
        val manifest = parseXml(projectFile("src/main/AndroidManifest.xml"))
        val application = manifest.documentElement
            .getElementsByTagName("application")
            .item(0) as Element

        assertEquals(
            "@xml/network_security_config",
            application.getAttributeNS(ANDROID_NS, "networkSecurityConfig")
        )
        assertFalse(
            "Do not enable app-wide cleartext traffic.",
            application.hasAttributeNS(ANDROID_NS, "usesCleartextTraffic")
        )

        val config = parseXml(projectFile("src/main/res/xml/network_security_config.xml"))
        val baseConfigs = config.documentElement.getElementsByTagName("base-config")
        for (i in 0 until baseConfigs.length) {
            val baseConfig = baseConfigs.item(i) as Element
            assertFalse(
                "Do not enable cleartext traffic in base-config.",
                baseConfig.getAttribute("cleartextTrafficPermitted") == "true"
            )
        }

        val domainConfigs = config.documentElement.getElementsByTagName("domain-config")
        var foundHeadUnitCleartextConfig = false
        for (i in 0 until domainConfigs.length) {
            val domainConfig = domainConfigs.item(i) as Element
            if (domainConfig.getAttribute("cleartextTrafficPermitted") != "true") continue

            val domains = domainConfig.getElementsByTagName("domain")
            for (j in 0 until domains.length) {
                val domain = domains.item(j).textContent.trim()
                if (domain == "10.0.0.1") {
                    foundHeadUnitCleartextConfig = true
                }
            }
        }

        assertTrue(
            "Network security config must permit HTTP to the head-unit web-config host.",
            foundHeadUnitCleartextConfig
        )
    }

    private fun projectFile(relativePath: String): Path {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("app", relativePath)
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Could not find $relativePath from ${Path.of("").toAbsolutePath()}")
    }

    private fun parseXml(path: Path) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(path.toFile())

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
