package su.neit.vaiwu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Strict slug format for heroine IDs matching `vaiwu-service`. */
internal val HEROINE_ID_REGEX = Regex("^[a-z0-9_-]{2,32}$")

/** Allowed file extensions inside a heroine pack ZIP. */
private val ALLOWED_PACK_EXTENSIONS = setOf("json", "png", "webp")

/** Security limits enforced while verifying and unpacking heroine ZIP archives. */
internal const val MAX_HEROINE_ZIP_BYTES = 25L * 1024L * 1024L
internal const val MAX_HEROINE_UNPACKED_BYTES = 48L * 1024L * 1024L
internal const val MAX_HEROINE_PACK_FILES = 80

private const val COPY_BUFFER_SIZE = 8 * 1024
private const val VALID_ARCHETYPE_MAX = 7

/** Computes lowercase hex SHA-256 of [file]. */
internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    FileInputStream(file).use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Pure filesystem pack verifier and unpacker (tested directly on the JVM without Android Context).
 *
 * Enforces SHA-256 integrity, Zip Bomb size/entry bounds, extension allowlist,
 * canonical-path Zip Slip protection, `meta.json` schema validation, and atomic swap.
 */
internal object HeroinePackInstaller {

    fun unpackAndValidate(
        zipFile: File,
        expectedHeroineId: String,
        expectedSha256: String,
        packsRootDir: File,
        maxZipBytes: Long = MAX_HEROINE_ZIP_BYTES,
        maxUnpackedBytes: Long = MAX_HEROINE_UNPACKED_BYTES,
        maxFiles: Int = MAX_HEROINE_PACK_FILES,
    ): InstalledHeroine {
        if (!HEROINE_ID_REGEX.matches(expectedHeroineId)) {
            throw IOException("Invalid heroine ID: $expectedHeroineId")
        }
        if (!zipFile.isFile) {
            throw IOException("Pack ZIP file missing")
        }
        if (zipFile.length() <= 0L || zipFile.length() > maxZipBytes) {
            throw IOException("Pack ZIP exceeds allowed size")
        }
        val actualSha = sha256Hex(zipFile)
        if (expectedSha256.isNotBlank() && !actualSha.equals(expectedSha256.trim(), ignoreCase = true)) {
            throw IOException("Pack SHA-256 mismatch")
        }

        packsRootDir.mkdirs()
        val stagingDir = File(packsRootDir, ".staging_$expectedHeroineId")
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        if (!stagingDir.mkdirs()) {
            throw IOException("Cannot create staging directory")
        }

        try {
            val stagingCanonical = stagingDir.canonicalPath + File.separator
            var fileCount = 0
            var totalUnpacked = 0L
            val buffer = ByteArray(COPY_BUFFER_SIZE)

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val entryName = entry.name.replace('\\', '/')
                    if (entryName.startsWith("/") || entryName.contains("..")) {
                        throw IOException("Unsafe ZIP entry path: $entryName")
                    }
                    val target = File(stagingDir, entryName)
                    val targetCanonical = target.canonicalPath
                    if (!targetCanonical.startsWith(stagingCanonical)) {
                        throw IOException("Zip Slip detected: $entryName")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                        zis.closeEntry()
                        continue
                    }

                    fileCount += 1
                    if (fileCount > maxFiles) {
                        throw IOException("Too many files in heroine pack")
                    }
                    val ext = target.extension.lowercase()
                    if (ext !in ALLOWED_PACK_EXTENSIONS) {
                        throw IOException("Disallowed file extension in pack: .$ext")
                    }

                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out ->
                        while (true) {
                            val read = zis.read(buffer)
                            if (read < 0) break
                            totalUnpacked += read
                            if (totalUnpacked > maxUnpackedBytes) {
                                throw IOException("Heroine pack exceeds unpacked size limit")
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                    zis.closeEntry()
                }
            }

            val metaFile = File(stagingDir, "meta.json")
            if (!metaFile.isFile) {
                throw IOException("Heroine pack missing meta.json")
            }
            val meta = JSONObject(metaFile.readText(Charsets.UTF_8))
            if (meta.optInt("schemaVersion", -1) != 1) {
                throw IOException("Unsupported meta.json schemaVersion")
            }
            val metaId = meta.optString("id", "")
            if (metaId != expectedHeroineId) {
                throw IOException("meta.json id mismatch: $metaId")
            }
            val version = meta.optString("version", "").trim()
            if (version.isEmpty()) {
                throw IOException("meta.json missing version")
            }
            val archetypeId = meta.optInt("archetypeId", -1)
            if (archetypeId !in 0..VALID_ARCHETYPE_MAX) {
                throw IOException("meta.json invalid archetypeId: $archetypeId")
            }
            val name = meta.optString("name", "").trim()
            if (name.isEmpty()) {
                throw IOException("meta.json missing name")
            }
            val tagline = meta.optString("tagline", "").trim()

            val spritesDir = File(stagingDir, "sprites")
            val hasIdleSprite = spritesDir.isDirectory && spritesDir.listFiles().orEmpty().any { file ->
                file.isFile && (
                    file.name == "sprite_${expectedHeroineId}_idle.png" ||
                        file.name == "sprite_${expectedHeroineId}_idle_00.png" ||
                        file.name == "sprite_${expectedHeroineId}_00.png" ||
                        file.name == "sprite_${expectedHeroineId}_idle.webp" ||
                        file.name == "sprite_${expectedHeroineId}_idle_00.webp"
                    )
            }
            if (!hasIdleSprite) {
                throw IOException("Heroine pack missing idle sprite")
            }

            val finalDir = File(packsRootDir, expectedHeroineId)
            val backupDir = File(packsRootDir, ".backup_$expectedHeroineId")
            if (backupDir.exists()) backupDir.deleteRecursively()
            if (finalDir.exists() && !finalDir.renameTo(backupDir)) {
                throw IOException("Cannot prepare swap for existing heroine")
            }
            if (!stagingDir.renameTo(finalDir)) {
                if (backupDir.exists()) backupDir.renameTo(finalDir)
                throw IOException("Cannot commit unpacked heroine pack")
            }
            if (backupDir.exists()) backupDir.deleteRecursively()

            return InstalledHeroine(
                id = expectedHeroineId,
                version = version,
                archetypeId = archetypeId,
                name = name,
                tagline = tagline,
                installedAtMs = System.currentTimeMillis(),
            )
        } catch (error: Exception) {
            stagingDir.deleteRecursively()
            throw error
        }
    }
}

/**
 * Manages downloaded heroine packs in `noBackupFilesDir/heroines/` and lightweight
 * index / active-selection metadata in `filesDir`.
 */
class HeroineStore(private val context: Context) {
    private val packsRootDir: File
        get() = File(context.noBackupFilesDir, "heroines")

    private val indexFile: File
        get() = File(context.filesDir, "heroines_index.json")

    private val activeFile: File
        get() = File(context.filesDir, "active_heroine.json")

    fun listInstalled(): List<InstalledHeroine> {
        if (!indexFile.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(indexFile.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("heroines") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    if (!HEROINE_ID_REGEX.matches(id)) continue
                    if (!File(packsRootDir, id).isDirectory) continue
                    add(
                        InstalledHeroine(
                            id = id,
                            version = obj.optString("version", "1.0.0"),
                            archetypeId = obj.optInt("archetypeId", 0),
                            name = obj.optString("name", id),
                            tagline = obj.optString("tagline", ""),
                            installedAtMs = obj.optLong("installedAtMs", 0L),
                        ),
                    )
                }
            }.sortedBy { it.id }
        }.getOrDefault(emptyList())
    }

    fun getActiveHeroineId(): String? {
        if (!activeFile.isFile) return null
        val id = runCatching {
            JSONObject(activeFile.readText(Charsets.UTF_8)).optString("activeHeroineId", "")
        }.getOrDefault("")
        if (!HEROINE_ID_REGEX.matches(id)) return null
        return if (File(packsRootDir, id).isDirectory) id else null
    }

    fun setActiveHeroineId(heroineId: String?) {
        if (heroineId == null) {
            activeFile.delete()
            return
        }
        if (!HEROINE_ID_REGEX.matches(heroineId) || !File(packsRootDir, heroineId).isDirectory) return
        val json = JSONObject().put("activeHeroineId", heroineId).toString()
        writeAtomically(activeFile, json)
    }

    fun installPack(zipFile: File, expectedHeroineId: String, expectedSha256: String): InstalledHeroine {
        val installed = HeroinePackInstaller.unpackAndValidate(
            zipFile = zipFile,
            expectedHeroineId = expectedHeroineId,
            expectedSha256 = expectedSha256,
            packsRootDir = packsRootDir,
        )
        val updated = listInstalled().filterNot { it.id == installed.id } + installed
        saveIndex(updated.sortedBy { it.id })
        return installed
    }

    fun deleteHeroine(heroineId: String) {
        if (!HEROINE_ID_REGEX.matches(heroineId)) return
        File(packsRootDir, heroineId).deleteRecursively()
        val updated = listInstalled().filterNot { it.id == heroineId }
        saveIndex(updated)
        if (getActiveHeroineId() == heroineId) {
            setActiveHeroineId(null)
        }
    }

    fun spritesDir(heroineId: String): File? {
        if (!HEROINE_ID_REGEX.matches(heroineId)) return null
        val dir = File(File(packsRootDir, heroineId), "sprites")
        return if (dir.isDirectory) dir else null
    }

    private fun saveIndex(entries: List<InstalledHeroine>) {
        val arr = JSONArray()
        entries.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("version", item.version)
                    .put("archetypeId", item.archetypeId)
                    .put("name", item.name)
                    .put("tagline", item.tagline)
                    .put("installedAtMs", item.installedAtMs),
            )
        }
        val payload = JSONObject().put("schemaVersion", 1).put("heroines", arr).toString()
        writeAtomically(indexFile, payload)
    }

    private fun writeAtomically(target: File, content: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(content, Charsets.UTF_8)
            tmp.delete()
        }
    }
}
