package su.neit.vaiwu

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

data class ReleaseInfo(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

private val AllowedUpdateHosts = setOf(
    "api.github.com",
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
)

internal fun isAllowedUpdateUrl(url: URL): Boolean =
    url.protocol == "https" && url.host in AllowedUpdateHosts

/**
 * Resolves a redirect `Location` against [current] and returns the target URL
 * only when it stays on an allowed HTTPS host. Returns `null` for missing,
 * malformed, downgraded or untrusted targets so no request is ever sent there.
 */
internal fun resolveAllowedRedirect(current: URL, location: String?): URL? {
    if (location.isNullOrBlank()) return null
    val resolved = try {
        URL(current, location.trim())
    } catch (_: Exception) {
        return null
    }
    return resolved.takeIf(::isAllowedUpdateUrl)
}

class GitHubUpdateManager(private val context: Context) {
    fun findLatestUpdate(currentVersion: String): ReleaseInfo? {
        val connection = openFollowingAllowedRedirects(RELEASES_API_URL)
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> return null
                else -> throw IOException(context.getString(R.string.update_http_error, connection.responseCode))
            }
            val body = connection.inputStream.use {
                it.readBoundedUtf8(
                    MaxApiResponseBytes,
                    context.getString(R.string.update_response_too_large),
                )
            }
            val release = parseRelease(JSONObject(body))
            return release.takeIf { isNewerVersion(it.version, currentVersion) }
        } finally {
            connection.disconnect()
        }
    }

    fun download(release: ReleaseInfo, onProgress: (Long, Long) -> Unit = { _, _ -> }): File {
        if (release.sizeBytes > MaxApkBytes) {
            throw IOException(context.getString(R.string.update_apk_too_large))
        }
        // Cache storage is excluded from backups by the platform and is
        // reachable through FileProvider (unlike noBackupFilesDir).
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(updatesDir, "vaiwu-${sanitizeFileName(release.version)}.apk")
        val partial = File(updatesDir, "${target.name}.part")
        cleanOldDownloads(updatesDir, keep = setOf(target.name, partial.name))
        partial.delete()

        val connection = openFollowingAllowedRedirects(release.apkUrl, readTimeoutMs = 60_000)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(context.getString(R.string.update_download_http_error, connection.responseCode))
            }
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MaxApkBytes) {
                            throw IOException(context.getString(R.string.update_apk_too_large))
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(total, release.sizeBytes)
                    }
                }
            }
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }

        if (partial.length() == 0L || (release.sizeBytes > 0 && partial.length() != release.sizeBytes)) {
            partial.delete()
            throw IOException(context.getString(R.string.update_apk_incomplete))
        }
        val expectedDigest = release.sha256.hexToBytes()
        if (!MessageDigest.isEqual(digest.digest(), expectedDigest)) {
            partial.delete()
            throw IOException(context.getString(R.string.update_sha_mismatch))
        }
        try {
            ZipFile(partial).use { apk ->
                if (apk.getEntry("AndroidManifest.xml") == null) {
                    throw IOException(context.getString(R.string.update_not_an_apk))
                }
            }
        } catch (error: Exception) {
            partial.delete()
            if (error is IOException) throw error
            throw IOException(context.getString(R.string.update_apk_corrupt), error)
        }

        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return target
    }

    fun install(apk: File) {
        val signatureFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archiveInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, signatureFlags)
        if (archiveInfo?.packageName != context.packageName) {
            apk.delete()
            throw IOException(context.getString(R.string.update_other_app))
        }
        val installedInfo = context.packageManager.getPackageInfo(context.packageName, signatureFlags)
        if (packageVersionCode(archiveInfo) <= packageVersionCode(installedInfo)) {
            apk.delete()
            throw IOException(context.getString(R.string.update_downgrade_blocked))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            installedInfo.signingInfo?.hasMultipleSigners() != archiveInfo.signingInfo?.hasMultipleSigners()
        ) {
            apk.delete()
            throw IOException(context.getString(R.string.update_signature_mismatch))
        }
        val installedCertificates = certificateHashes(installedInfo)
        val archiveCertificates = certificateHashes(archiveInfo)
        if (installedCertificates.isEmpty() || installedCertificates != archiveCertificates) {
            apk.delete()
            throw IOException(context.getString(R.string.update_signature_mismatch))
        }

        if (!context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            throw UnknownAppSourcesRequiredException()
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(install)
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun certificateHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            if (signingInfo == null) {
                null
            } else if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory ?: signingInfo.apkContentsSigners
            }
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .toHexString()
        }.toSet()
    }

    /**
     * Opens [url] and follows HTTP redirects manually so every single hop is
     * validated against [AllowedUpdateHosts] BEFORE connecting to it.
     */
    private fun openFollowingAllowedRedirects(url: String, readTimeoutMs: Int = 15_000): HttpURLConnection {
        var current = try {
            URL(url)
        } catch (_: Exception) {
            throw IOException(context.getString(R.string.update_bad_url))
        }
        if (!isAllowedUpdateUrl(current)) {
            throw IOException(context.getString(R.string.update_https_only))
        }
        repeat(MaxRedirects + 1) { hop ->
            val connection = openSingleHop(current, readTimeoutMs)
            val code = try {
                connection.responseCode
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            if (code !in RedirectCodes) {
                return connection
            }
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (hop == MaxRedirects) {
                throw IOException(context.getString(R.string.update_too_many_redirects))
            }
            current = resolveAllowedRedirect(current, location)
                ?: throw IOException(context.getString(R.string.update_untrusted_redirect))
        }
        throw IOException(context.getString(R.string.update_too_many_redirects))
    }

    private fun openSingleHop(url: URL, readTimeoutMs: Int): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("User-Agent", "Vaiwu-Android/${BuildConfig.VERSION_NAME}")
        if (url.host == "api.github.com") {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        return connection
    }

    private fun cleanOldDownloads(directory: File, keep: Set<String>) {
        directory.listFiles()?.forEach { file ->
            val stale = file.name.endsWith(".apk") || file.name.endsWith(".part")
            if (stale && file.name !in keep) {
                file.delete()
            }
        }
    }

    private fun sanitizeFileName(version: String): String {
        val safe = version.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
        return safe.ifBlank { "update" }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        val assets = json.getJSONArray("assets")
        var apk: JSONObject? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            if (asset.getString("name") == APK_ASSET_NAME) {
                apk = asset
                break
            }
        }
        val asset = apk ?: throw IOException(context.getString(R.string.update_asset_missing, APK_ASSET_NAME))
        val sha256 = asset.optString("digest").removePrefix("sha256:")
        if (!sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            throw IOException(context.getString(R.string.update_no_sha256))
        }
        return ReleaseInfo(
            version = json.getString("tag_name").removePrefix("v"),
            title = json.optString("name").ifBlank { json.getString("tag_name") },
            notes = json.optString("body"),
            apkUrl = asset.getString("browser_download_url"),
            sizeBytes = asset.optLong("size"),
            sha256 = sha256,
        )
    }

    companion object {
        private const val RELEASES_API_URL =
            "https://api.github.com/repos/zimin3d/vaiwu-android/releases/latest"
        private const val APK_ASSET_NAME = "vaiwu-android.apk"
        private const val MaxApkBytes = 128L * 1024 * 1024
        private const val MaxApiResponseBytes = 1024 * 1024
        private const val MaxRedirects = 5
        private val RedirectCodes = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
    }
}

private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}

private fun ByteArray.toHexString(): String = joinToString("") { byte ->
    "%02x".format(Locale.US, byte.toInt() and 0xff)
}

class UnknownAppSourcesRequiredException : IOException()

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateParts = versionParts(candidate) ?: return false
    val currentParts = versionParts(current) ?: return false
    val count = maxOf(candidateParts.size, currentParts.size)
    for (index in 0 until count) {
        val left = candidateParts.getOrElse(index) { 0L }
        val right = currentParts.getOrElse(index) { 0L }
        if (left != right) return left > right
    }
    return false
}

internal fun versionParts(version: String): List<Long>? {
    val core = version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
    if (!core.matches(Regex("\\d+(\\.\\d+)*"))) return null
    return core.split('.').map { it.toLongOrNull() ?: return null }
}
