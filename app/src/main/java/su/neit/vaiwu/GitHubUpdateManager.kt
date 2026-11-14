package su.neit.vaiwu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

data class ReleaseInfo(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

class GitHubUpdateManager(private val context: Context) {
    fun findLatestUpdate(currentVersion: String): ReleaseInfo? {
        val connection = openConnection(RELEASES_API_URL)
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> return null
                else -> throw IOException("GitHub вернул HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = parseRelease(JSONObject(body))
            return release.takeIf { isNewerVersion(it.version, currentVersion) }
        } finally {
            connection.disconnect()
        }
    }

    fun download(release: ReleaseInfo): File {
        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(updatesDir, "vaiwu-${release.version}.apk")
        val partial = File(updatesDir, "${target.name}.part")
        partial.delete()

        val connection = openConnection(release.apkUrl, readTimeoutMs = 60_000)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Загрузка APK: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    input.copyTo(output)
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
            throw IOException("APK загружен не полностью")
        }
        try {
            ZipFile(partial).use { apk ->
                if (apk.getEntry("AndroidManifest.xml") == null) {
                    throw IOException("Загруженный файл не является APK")
                }
            }
        } catch (error: Exception) {
            partial.delete()
            if (error is IOException) throw error
            throw IOException("APK повреждён", error)
        }

        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        return target
    }

    fun install(apk: File) {
        val archiveInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        if (archiveInfo?.packageName != context.packageName) {
            apk.delete()
            throw IOException("APK принадлежит другому приложению")
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

    private fun openConnection(url: String, readTimeoutMs: Int = 15_000): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Vaiwu-Android/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        return connection
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
        val asset = apk ?: throw IOException("В релизе нет $APK_ASSET_NAME")
        return ReleaseInfo(
            version = json.getString("tag_name").removePrefix("v"),
            title = json.optString("name").ifBlank { json.getString("tag_name") },
            notes = json.optString("body"),
            apkUrl = asset.getString("browser_download_url"),
            sizeBytes = asset.optLong("size"),
        )
    }

    companion object {
        private const val RELEASES_API_URL =
            "https://api.github.com/repos/zimin3d/vaiwu-android/releases/latest"
        private const val APK_ASSET_NAME = "vaiwu-android.apk"
    }
}

class UnknownAppSourcesRequiredException : IOException()

private fun isNewerVersion(candidate: String, current: String): Boolean {
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

private fun versionParts(version: String): List<Long>? {
    val core = version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
    if (!core.matches(Regex("\\d+(\\.\\d+)*"))) return null
    return core.split('.').map { it.toLongOrNull() ?: return null }
}
