package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HeroinePackSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildZip(entries: Map<String, ByteArray>): File {
        val zipFile = tempFolder.newFile("pack_${System.nanoTime()}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return zipFile
    }

    private fun validMeta(id: String = "sakura", version: String = "1.0.0"): ByteArray =
        """
        {
          "schemaVersion": 1,
          "id": "$id",
          "version": "$version",
          "minAppVersion": 2,
          "archetypeId": 3,
          "name": "Sakura",
          "tagline": "Spring guardian"
        }
        """.trimIndent().toByteArray(Charsets.UTF_8)

    @Test
    fun chatEndpointRoutesVaiwuKeysToUnifiedServiceAndLegacyKeysToOpenRouter() {
        val (proxyUrl, isProxy) = resolveChatEndpoint("vw_abc123")
        assertEquals("https://api.t.neit.su/v1/chat/completions", proxyUrl)
        assertTrue(isProxy)

        val (orUrl, isOrProxy) = resolveChatEndpoint("sk-or-v1-legacy")
        assertEquals("https://openrouter.ai/api/v1/chat/completions", orUrl)
        assertFalse(isOrProxy)
    }

    @Test
    fun serviceUrlAllowlistRejectsHttpAndForeignHosts() {
        val ok = validateVaiwuServiceUrl("https://api.t.neit.su/v1/heroines")
        assertEquals("api.t.neit.su", ok.host)

        for (bad in listOf("http://api.t.neit.su/v1/heroines", "https://evil.example/v1/heroines")) {
            var failed = false
            try {
                validateVaiwuServiceUrl(bad)
            } catch (_: IOException) {
                failed = true
            }
            assertTrue("Expected rejection for $bad", failed)
        }
    }

    @Test
    fun validPackUnpacksAtomicallyWithVerifiedSha256() {
        val zip = buildZip(
            mapOf(
                "meta.json" to validMeta("sakura", "1.0.0"),
                "sprites/sprite_sakura_idle.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            ),
        )
        val packsRoot = tempFolder.newFolder("packs")
        val sha = sha256Hex(zip)

        val installed = HeroinePackInstaller.unpackAndValidate(
            zipFile = zip,
            expectedHeroineId = "sakura",
            expectedSha256 = sha,
            packsRootDir = packsRoot,
        )
        assertEquals("sakura", installed.id)
        assertEquals("1.0.0", installed.version)
        assertEquals("Sakura", installed.name)
        assertTrue(File(packsRoot, "sakura/sprites/sprite_sakura_idle.png").isFile)
    }

    @Test
    fun unpackRejectsShaMismatchZipSlipDisallowedExtensionAndZipBomb() {
        val packsRoot = tempFolder.newFolder("packs_sec")

        val validZip = buildZip(
            mapOf(
                "meta.json" to validMeta("yuki"),
                "sprites/sprite_yuki_idle.png" to byteArrayOf(1, 2, 3),
            ),
        )
        assertFailsWithIo {
            HeroinePackInstaller.unpackAndValidate(validZip, "yuki", "0".repeat(64), packsRoot)
        }

        val slipZip = buildZip(
            mapOf(
                "../evil.png" to byteArrayOf(1, 2, 3),
                "meta.json" to validMeta("yuki"),
                "sprites/sprite_yuki_idle.png" to byteArrayOf(1, 2, 3),
            ),
        )
        assertFailsWithIo {
            HeroinePackInstaller.unpackAndValidate(slipZip, "yuki", sha256Hex(slipZip), packsRoot)
        }

        val badExtZip = buildZip(
            mapOf(
                "meta.json" to validMeta("yuki"),
                "sprites/sprite_yuki_idle.png" to byteArrayOf(1, 2, 3),
                "payload.sh" to "echo hi".toByteArray(),
            ),
        )
        assertFailsWithIo {
            HeroinePackInstaller.unpackAndValidate(badExtZip, "yuki", sha256Hex(badExtZip), packsRoot)
        }

        val bombZip = buildZip(
            mapOf(
                "meta.json" to validMeta("yuki"),
                "sprites/sprite_yuki_idle.png" to ByteArray(4096) { 7 },
            ),
        )
        assertFailsWithIo {
            HeroinePackInstaller.unpackAndValidate(
                zipFile = bombZip,
                expectedHeroineId = "yuki",
                expectedSha256 = sha256Hex(bombZip),
                packsRootDir = packsRoot,
                maxUnpackedBytes = 512L,
            )
        }
    }

    private fun assertFailsWithIo(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (_: IOException) {
            threw = true
        }
        assertTrue("Expected IOException", threw)
    }
}
