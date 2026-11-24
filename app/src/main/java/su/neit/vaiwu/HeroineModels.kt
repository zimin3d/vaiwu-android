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

/**
 * Optional custom dialogue lines and LLM system prompt loaded from `dialogues.json`
 * inside an installed heroine or outfit pack.
 */
data class HeroineDialogues(
    val systemPrompt: String? = null,
    val phrases: Map<String, List<String>> = emptyMap(),
)

/** Maps a [PhraseContext] integer constant to its canonical key in `dialogues.json`. */
internal fun phraseContextKey(context: Int): String? = when (context) {
    PhraseContext.Greeting -> "greeting"
    PhraseContext.Fed -> "fed"
    PhraseContext.Played -> "played"
    PhraseContext.Washed -> "washed"
    PhraseContext.Toilet -> "toilet"
    PhraseContext.Talked -> "talked"
    PhraseContext.Hugged -> "hugged"
    PhraseContext.Patted -> "patted"
    PhraseContext.Treated -> "treated"
    PhraseContext.RefusedFood -> "refused_food"
    PhraseContext.Sleeping -> "sleeping"
    PhraseContext.Sick -> "sick"
    PhraseContext.Sad -> "sad"
    PhraseContext.Dead -> "dead"
    PhraseContext.Poked -> "poked"
    PhraseContext.Woken -> "woken"
    PhraseContext.Victory -> "victory"
    PhraseContext.Fainted -> "fainted"
    else -> null
}
