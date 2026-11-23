package su.neit.vaiwu

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Default production URL of `vaiwu-service`. */
internal const val VAIWU_SERVICE_BASE_URL = "https://api.t.neit.su"

/** Allowed HTTPS hosts for `vaiwu-service` requests so Bearer tokens can never leak elsewhere. */
private val ALLOWED_SERVICE_HOSTS = setOf("api.t.neit.su")

private const val HEROINE_CONNECT_TIMEOUT_MS = 15_000
private const val HEROINE_READ_TIMEOUT_MS = 30_000
private const val HEROINE_CATALOG_MAX_BYTES = 512 * 1024
private const val HEROINE_DOWNLOAD_BUFFER_BYTES = 16 * 1024

sealed interface CatalogFetchResult {
    data object NotModified : CatalogFetchResult
    data class Updated(val heroines: List<RemoteHeroine>, val etag: String?) : CatalogFetchResult
    data class AuthError(val httpCode: Int) : CatalogFetchResult
    data class Error(val message: String) : CatalogFetchResult
}

/** Validates that [urlString] uses HTTPS and points to an allowlisted `vaiwu-service` host. */
internal fun validateVaiwuServiceUrl(urlString: String): URL {
    val parsed = URL(urlString)
    if (!parsed.protocol.equals("https", ignoreCase = true)) {
        throw IOException("Only HTTPS is allowed for Vaiwu service")
    }
    if (parsed.host.lowercase() !in ALLOWED_SERVICE_HOSTS) {
        throw IOException("Untrusted Vaiwu service host: ${parsed.host}")
    }
    return parsed
}

/** Parses `GET /v1/heroines` JSON into [RemoteHeroine] records, filtering out malformed entries. */
internal fun parseHeroinesCatalogJson(rawJson: String): List<RemoteHeroine> {
    val root = JSONObject(rawJson)
    val array = root.optJSONArray("heroines") ?: return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (!HEROINE_ID_REGEX.matches(id)) continue
            add(
                RemoteHeroine(
                    id = id,
                    version = item.optString("version", "1.0.0"),
                    minAppVersion = item.optInt("minAppVersion", 1),
                    archetypeId = item.optInt("archetypeId", 0),
                    name = item.optString("name", id),
                    tagline = item.optString("tagline", ""),
                    sizeBytes = item.optLong("sizeBytes", 0L),
                    sha256 = item.optString("sha256", ""),
                    packUrl = item.optString("packUrl", "/v1/heroines/$id/pack"),
                    previewUrl = item.optString("previewUrl", "").ifBlank { null },
                ),
            )
        }
    }
}

/**
 * HTTPS client for `vaiwu-service` heroine catalog and ZIP pack downloads.
 *
 * Disables automatic redirects (`instanceFollowRedirects = false`) and enforces
 * [ALLOWED_SERVICE_HOSTS] so the user's Bearer key is never forwarded to a third party.
 */
class HeroineClient(
    private val baseUrl: String = VAIWU_SERVICE_BASE_URL,
) {
    fun fetchCatalog(apiKey: String, cachedEtag: String? = null): CatalogFetchResult {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isBlank()) return CatalogFetchResult.AuthError(HttpURLConnection.HTTP_UNAUTHORIZED)

        val url = validateVaiwuServiceUrl("${baseUrl.trimEnd('/')}/v1/heroines")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = HEROINE_CONNECT_TIMEOUT_MS
        connection.readTimeout = HEROINE_READ_TIMEOUT_MS
        connection.setRequestProperty("Authorization", "Bearer $cleanKey")
        connection.setRequestProperty("X-Vaiwu-Client", "android")
        if (!cachedEtag.isNullOrBlank()) {
            connection.setRequestProperty("If-None-Match", cachedEtag)
        }

        return try {
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> CatalogFetchResult.NotModified
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> {
                    runCatching { connection.errorStream?.close() }
                    CatalogFetchResult.AuthError(code)
                }
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.use { it.readBoundedUtf8(HEROINE_CATALOG_MAX_BYTES) }
                    val etag = connection.getHeaderField("ETag")
                    CatalogFetchResult.Updated(parseHeroinesCatalogJson(body), etag)
                }
                else -> {
                    runCatching { connection.errorStream?.close() }
                    CatalogFetchResult.Error("HTTP $code")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Streams the heroine ZIP pack into [destination] while reporting progress `(received, total)`.
     * Returns the expected SHA-256 (preferring `X-Pack-Sha256` header if present, verified against [heroine.sha256]).
     */
    fun downloadPack(
        apiKey: String,
        heroine: RemoteHeroine,
        destination: File,
        onProgress: (Long, Long) -> Unit,
    ): String {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isBlank()) throw IOException("Missing access key")

        val relativePath = if (heroine.packUrl.startsWith("/")) heroine.packUrl else "/v1/heroines/${heroine.id}/pack"
        val url = validateVaiwuServiceUrl("${baseUrl.trimEnd('/')}$relativePath")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = HEROINE_CONNECT_TIMEOUT_MS
        connection.readTimeout = HEROINE_READ_TIMEOUT_MS
        connection.setRequestProperty("Authorization", "Bearer $cleanKey")
        connection.setRequestProperty("X-Vaiwu-Client", "android")

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                runCatching { connection.errorStream?.close() }
                throw IOException("HTTP $code")
            }

            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_HEROINE_ZIP_BYTES) {
                throw IOException("Pack exceeds size limit")
            }
            val headerSha = connection.getHeaderField("X-Pack-Sha256")?.trim().orEmpty()
            if (headerSha.isNotEmpty() && heroine.sha256.isNotBlank() && !headerSha.equals(heroine.sha256, ignoreCase = true)) {
                throw IOException("Pack SHA-256 header mismatch")
            }

            destination.parentFile?.mkdirs()
            val expectedTotal = if (contentLength > 0L) contentLength else heroine.sizeBytes
            var received = 0L
            val buffer = ByteArray(HEROINE_DOWNLOAD_BUFFER_BYTES)

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { out ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > MAX_HEROINE_ZIP_BYTES) {
                            throw IOException("Pack exceeds size limit")
                        }
                        out.write(buffer, 0, count)
                        onProgress(received, expectedTotal)
                    }
                }
            }
            if (contentLength > 0L && received != contentLength) {
                throw IOException("Incomplete pack download")
            }
            return headerSha.ifEmpty { heroine.sha256 }
        } finally {
            connection.disconnect()
        }
    }
}
