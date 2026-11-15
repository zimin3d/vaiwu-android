package su.neit.vaiwu

import java.net.URL
import org.junit.Assert.assertFalse
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
}
