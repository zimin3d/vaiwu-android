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

/** Metadata parsed from the latest GitHub Release containing a signed APK asset. */
data class ReleaseInfo(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** Whitelisted HTTPS hosts allowed during update metadata fetches and asset redirects. */
private val AllowedUpdateHosts = setOf(
    "api.github.com",
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
)

/** Prefix required for initial release APK download URLs before any CDN redirects. */
internal const val RELEASE_DOWNLOAD_PREFIX =
    "https://github.com/zimin3d/vaiwu-android/releases/download/"

/** Returns true only when [url] points to an official vaiwu-android GitHub release download. */
internal fun isAllowedReleaseDownloadUrl(url: String): Boolean =
    url.startsWith(RELEASE_DOWNLOAD_PREFIX)

/** Returns true only when [url] uses HTTPS and points to one of [AllowedUpdateHosts]. */
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

/**
 * Handles in-app self-updates from GitHub Releases with strict defense-in-depth:
 * - Direct release download URL prefix-pinned to [RELEASE_DOWNLOAD_PREFIX].
 * - HTTPS host allowlisting on every redirect hop (GitHub CDN / release assets).
 * - Release asset SHA-256 digest verification during streaming download (guards against transmission corruption).
 * - Trust Model: The primary cryptographic security guarantee is enforced by [install], which verifies
 *   the package name, rejects downgrades, and verifies that the downloaded APK's signing certificate
 *   matches the currently installed app's certificate history.
 * - APK zip sanity check and non-exported FileProvider.
 */
class GitHubUpdateManager(private val context: Context) {

    /** Queries the latest GitHub Release and returns [ReleaseInfo] if it is newer than [currentVersion]. */
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

    /** Downloads [release]'s APK to cache, streaming progress and verifying size, SHA-256, and zip structure. */
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

        val connection = openFollowingAllowedRedirects(release.apkUrl, readTimeoutMs = DownloadReadTimeoutMs)
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(context.getString(R.string.update_download_http_error, connection.responseCode))
            }
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DownloadBufferBytes)
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

    /**
     * Verifies package name, monotonic versionCode, and matching signing certificate
     * hashes before launching the system APK installer via FileProvider.
     */
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
    private fun openFollowingAllowedRedirects(url: String, readTimeoutMs: Int = ApiTimeoutMs): HttpURLConnection {
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

    /** Configures a single non-redirecting HTTPS connection with timeouts and GitHub API headers. */
    private fun openSingleHop(url: URL, readTimeoutMs: Int): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = ApiTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("User-Agent", "Vaiwu-Android/${BuildConfig.VERSION_NAME}")
        if (url.host == "api.github.com") {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        return connection
    }

    /** Removes stale `.apk` and `.part` files left in the update cache from earlier downloads. */
    private fun cleanOldDownloads(directory: File, keep: Set<String>) {
        directory.listFiles()?.forEach { file ->
            val stale = file.name.endsWith(".apk") || file.name.endsWith(".part")
            if (stale && file.name !in keep) {
                file.delete()
            }
        }
    }

    /** Strips unsafe path characters from a release tag before embedding it in a local filename. */
    private fun sanitizeFileName(version: String): String {
        val safe = version.replace(Regex("[^A-Za-z0-9._-]"), "_").take(MaxVersionFileNameChars)
        return safe.ifBlank { "update" }
    }

    /** Extracts the signed APK asset and its mandatory 64-hex-char SHA-256 digest from GitHub API JSON. */
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
        if (!sha256.matches(Regex("[0-9a-fA-F]{$Sha256HexLength}"))) {
            throw IOException(context.getString(R.string.update_no_sha256))
        }
        val apkUrl = asset.getString("browser_download_url")
        if (!isAllowedReleaseDownloadUrl(apkUrl)) {
            throw IOException(context.getString(R.string.update_bad_url))
        }
        return ReleaseInfo(
            version = json.getString("tag_name").removePrefix("v"),
            title = json.optString("name").ifBlank { json.getString("tag_name") },
            notes = json.optString("body"),
            apkUrl = apkUrl,
            sizeBytes = asset.optLong("size"),
            sha256 = sha256,
        )
    }

    companion object {
        private const val RELEASES_API_URL =
            "https://api.github.com/repos/zimin3d/vaiwu-android/releases/latest"
        private const val APK_ASSET_NAME = "vaiwu-android.apk"
        private const val MaxApkBytes = 256L * 1024 * 1024
        private const val MaxApiResponseBytes = 1024 * 1024
        private const val DownloadBufferBytes = 32 * 1024
        private const val ApiTimeoutMs = 15_000
        private const val DownloadReadTimeoutMs = 60_000
        private const val MaxVersionFileNameChars = 64
        private const val Sha256HexLength = 64
        private const val MaxRedirects = 5
        private const val HTTP_TEMP_REDIRECT = 307
        private const val HTTP_PERM_REDIRECT = 308
        private val RedirectCodes = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            HTTP_TEMP_REDIRECT,
            HTTP_PERM_REDIRECT,
        )
    }
}

/** Decodes an even-length hexadecimal string into raw bytes. */
private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}

/** Encodes raw bytes as a lowercase hexadecimal digest string. */
private fun ByteArray.toHexString(): String = joinToString("") { byte ->
    "%02x".format(Locale.US, byte.toInt() and 0xff)
}

/** Signals that Android's "Install unknown apps" permission screen was opened for the user. */
class UnknownAppSourcesRequiredException : IOException()

/** Returns true when semantic version [candidate] is strictly greater than [current]. */
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

/** Parses the numeric `major.minor.patch` core of a version string (ignoring `v` prefix and suffixes). */
internal fun versionParts(version: String): List<Long>? {
    val core = version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
    if (!core.matches(Regex("\\d+(\\.\\d+)*"))) return null
    return core.split('.').map { it.toLongOrNull() ?: return null }
}
