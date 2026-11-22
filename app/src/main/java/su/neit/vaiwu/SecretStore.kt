package su.neit.vaiwu

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Printable non-whitespace ASCII range allowed in HTTP `Authorization` header values. */
private val PRINTABLE_ASCII = '\u0021'..'\u007E'

/** Upper character limit applied to stored API keys. */
private const val MAX_API_KEY_LENGTH = 300

/** Geographic coordinate bounds (WGS-84 degrees). */
private val VALID_LATITUDE_RANGE = -90.0..90.0
private val VALID_LONGITUDE_RANGE = -180.0..180.0

/**
 * Strips whitespace, control characters and non-ASCII bytes so an API key can
 * never inject HTTP headers or break `HttpURLConnection`.
 */
internal fun sanitizeApiKey(raw: String): String =
    raw.lineSequence()
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .filter { it in PRINTABLE_ASCII }
        .take(MAX_API_KEY_LENGTH)

/**
 * Parses a decimal coordinate accepting both dot and comma decimal separators
 * (common on Russian keyboards) while rejecting NaN and Infinity.
 */
internal fun parseCoordinate(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Returns true when [latitude] is within `[-90, 90]` and [longitude] within `[-180, 180]`. */
internal fun isValidCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude in VALID_LATITUDE_RANGE && longitude in VALID_LONGITUDE_RANGE

/**
 * Keeps the existing API key when the settings form is saved with a blank key
 * field, unless the user explicitly asked to clear it.
 */
internal fun resolveApiKeyUpdate(
    input: String,
    clearRequested: Boolean,
    existing: String,
): String {
    if (clearRequested) return ""
    val sanitized = sanitizeApiKey(input)
    return if (sanitized.isNotEmpty()) sanitized else sanitizeApiKey(existing)
}

/**
 * Stores sensitive user secrets (OpenRouter API key) encrypted with an
 * AndroidKeyStore AES-256-GCM key instead of plaintext SharedPreferences.
 */
class SecretStore(private val preferences: SharedPreferences) {
    fun hasApiKey(): Boolean = readApiKey().isNotBlank()

    fun readApiKey(): String {
        val encrypted = preferences.getString(EncryptedKeyPref, null)
        if (!encrypted.isNullOrBlank()) {
            val decrypted = decrypt(encrypted)
            if (decrypted != null) {
                return sanitizeApiKey(decrypted)
            }
        }
        // Transparent one-time migration from legacy plaintext storage.
        val legacy = sanitizeApiKey(preferences.getString(LegacyPlaintextKeyPref, "").orEmpty())
        if (legacy.isNotEmpty()) {
            writeApiKey(legacy)
            return legacy
        }
        return ""
    }

    fun writeApiKey(rawKey: String): Boolean {
        val clean = sanitizeApiKey(rawKey)
        if (clean.isEmpty()) {
            preferences.edit()
                .remove(EncryptedKeyPref)
                .remove(LegacyPlaintextKeyPref)
                .apply()
            return true
        }
        val encrypted = encrypt(clean) ?: return false
        preferences.edit()
            .putString(EncryptedKeyPref, encrypted)
            .remove(LegacyPlaintextKeyPref)
            .apply()
        return true
    }

    private fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        "$FormatPrefix$ivB64:$ctB64"
    }.getOrNull()

    private fun decrypt(encoded: String): String? = runCatching {
        if (!encoded.startsWith(FormatPrefix)) return null
        val payload = encoded.removePrefix(FormatPrefix)
        val separator = payload.indexOf(':')
        if (separator <= 0 || separator >= payload.lastIndex) return null
        val iv = Base64.decode(payload.substring(0, separator), Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GcmTagBits, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        val existing = keyStore.getKey(KeyAlias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AesKeyBits)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "vaiwu_openrouter_key_v1"
        const val Transformation = "AES/GCM/NoPadding"
        const val AesKeyBits = 256
        const val GcmTagBits = 128
        const val FormatPrefix = "v1:"
        const val EncryptedKeyPref = "openrouter_key_enc"
        const val LegacyPlaintextKeyPref = "openrouter_key"
    }
}
