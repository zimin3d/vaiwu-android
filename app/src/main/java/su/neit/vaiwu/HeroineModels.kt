package su.neit.vaiwu

/**
 * Catalog entry returned by `GET /v1/heroines` on `vaiwu-service`.
 */
data class RemoteHeroine(
    val id: String,
    val kind: String = "heroine",
    val targetCharacterId: Int? = null,
    val version: String,
    val minAppVersion: Int,
    val archetypeId: Int,
    val name: String,
    val tagline: String,
    val sizeBytes: Long,
    val sha256: String,
    val packUrl: String,
    val previewUrl: String?,
)

/**
 * Metadata for a heroine pack unpacked locally in `noBackupFilesDir/heroines/<id>/`.
 */
data class InstalledHeroine(
    val id: String,
    val kind: String = "heroine",
    val targetCharacterId: Int? = null,
    val version: String,
    val archetypeId: Int,
    val name: String,
    val tagline: String,
    val installedAtMs: Long,
)

/**
 * UI state for [HeroinesScreen], combining offline installed packs with the remote catalog.
 */
data class HeroinesUiState(
    val loading: Boolean = false,
    val hasAccessKey: Boolean = false,
    val catalog: List<RemoteHeroine> = emptyList(),
    val installed: List<InstalledHeroine> = emptyList(),
    val previewPaths: Map<String, String> = emptyMap(),
    val activeHeroineId: String? = null,
    val downloadingId: String? = null,
    val downloadProgress: Float? = null,
    val errorMessage: String? = null,
)
