package su.neit.vaiwu

import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSecurityTest {
    @Test
    fun `trusted GitHub HTTPS endpoints are accepted`() {
        val urls = listOf(
            "https://api.github.com/repos/zimin3d/vaiwu-android/releases/latest",
            "https://github.com/zimin3d/vaiwu-android/releases/download/v1.0.0/vaiwu-android.apk",
            "https://objects.githubusercontent.com/asset",
            "https://release-assets.githubusercontent.com/asset",
        )

        urls.forEach { url ->
            assertTrue(url, isAllowedUpdateUrl(URL(url)))
        }
    }

    @Test
    fun `insecure and untrusted update URLs are rejected`() {
        val urls = listOf(
            "http://github.com/zimin3d/vaiwu-android/releases/latest",
            "https://example.com/vaiwu-android.apk",
            "https://github.com.example.com/vaiwu-android.apk",
            "https://github.com@example.com/vaiwu-android.apk",
            "https://api.github.com.example.com/releases/latest",
            "ftp://github.com/vaiwu-android.apk",
        )

        urls.forEach { url ->
            assertFalse(url, isAllowedUpdateUrl(URL(url)))
        }
    }

    @Test
    fun `redirect validation uses the final URL`() {
        assertTrue(isAllowedUpdateUrl(URL("https://release-assets.githubusercontent.com/final.apk")))
        assertFalse(isAllowedUpdateUrl(URL("https://example.com/redirected.apk")))
    }

    @Test
    fun `per-hop redirect resolution allows only trusted HTTPS targets`() {
        val origin = URL("https://github.com/zimin3d/vaiwu-android/releases/download/v1.0.0/vaiwu-android.apk")

        assertEquals(
            URL("https://release-assets.githubusercontent.com/asset/123"),
            resolveAllowedRedirect(origin, "https://release-assets.githubusercontent.com/asset/123"),
        )
        assertEquals(
            URL("https://github.com/other/path.apk"),
            resolveAllowedRedirect(origin, "/other/path.apk"),
        )

        assertNull(resolveAllowedRedirect(origin, null))
        assertNull(resolveAllowedRedirect(origin, "   "))
        assertNull(resolveAllowedRedirect(origin, "http://release-assets.githubusercontent.com/asset/123"))
        assertNull(resolveAllowedRedirect(origin, "https://evil.example.com/vaiwu-android.apk"))
        assertNull(resolveAllowedRedirect(origin, "https://github.com.evil.example.com/vaiwu-android.apk"))
        assertNull(resolveAllowedRedirect(origin, "javascript:alert(1)"))
    }

    @Test
    fun `release download URL must belong to the official repository releases`() {
        assertTrue(
            isAllowedReleaseDownloadUrl(
                "https://github.com/zimin3d/vaiwu-android/releases/download/v1.0.0/vaiwu-android.apk"
            )
        )
        assertFalse(
            isAllowedReleaseDownloadUrl(
                "https://github.com/attacker/malicious/releases/download/v1.0.0/vaiwu-android.apk"
            )
        )
        assertFalse(
            isAllowedReleaseDownloadUrl(
                "https://evil.com/releases/download/v1.0.0/vaiwu-android.apk"
            )
        )
    }
}
