package com.example.liftrix.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NetworkSecurityConfigTest {

    @Test
    fun `release disables cleartext and trusts only system certificate authorities`() {
        val config = readBaseConfig("src/main/res/xml/network_security_config.xml")

        assertEquals("false", config.cleartextPolicy)
        assertEquals(setOf("system"), config.trustAnchors)
        assertFalse(config.hasPinSet)
    }

    @Test
    fun `debug disables cleartext and additionally trusts user certificate authorities`() {
        val config = readBaseConfig("src/debug/res/xml/network_security_config.xml")

        assertEquals("false", config.cleartextPolicy)
        assertEquals(setOf("system", "user"), config.trustAnchors)
        assertFalse(config.hasPinSet)
    }

    private fun readBaseConfig(relativePath: String): ParsedNetworkConfig {
        val workingDirectory = File(System.getProperty("user.dir"))
        val file = sequenceOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath")
        ).firstOrNull(File::isFile) ?: File(workingDirectory, relativePath)
        assertTrue("Missing network security config: ${file.absolutePath}", file.isFile)

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val baseConfig = document.getElementsByTagName("base-config").item(0) as Element
        val certificateNodes = baseConfig.getElementsByTagName("certificates")
        val trustAnchors = buildSet {
            repeat(certificateNodes.length) { index ->
                add((certificateNodes.item(index) as Element).getAttribute("src"))
            }
        }

        return ParsedNetworkConfig(
            cleartextPolicy = baseConfig.getAttribute("cleartextTrafficPermitted"),
            trustAnchors = trustAnchors,
            hasPinSet = document.getElementsByTagName("pin-set").length > 0
        )
    }

    private data class ParsedNetworkConfig(
        val cleartextPolicy: String,
        val trustAnchors: Set<String>,
        val hasPinSet: Boolean
    )
}
