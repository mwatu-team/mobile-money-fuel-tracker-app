package com.mobilemoneyfueltracker.gateway.data

import android.content.SharedPreferences
import java.util.Base64

/**
 * Keeps the device token only in encrypted form. SharedPreferences holds ciphertext and IV, never
 * the token. A token that cannot be decrypted is discarded, so the phone must be paired again
 * rather than falling back to anything insecure.
 */
class SecureTokenStore(
    private val preferences: SharedPreferences,
    private val cipher: TokenCipher,
) {
    fun read(): String? {
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return try {
            String(
                cipher.decrypt(EncryptedBlob(decode(iv), decode(ciphertext))),
                Charsets.UTF_8,
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    /** Throws if the token cannot be encrypted; nothing is stored in that case. */
    fun write(token: String) {
        require(token.isNotBlank()) { "Token must not be blank" }
        val blob = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_IV, encode(blob.iv))
            .putString(KEY_CIPHERTEXT, encode(blob.ciphertext))
            .apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_IV).remove(KEY_CIPHERTEXT).apply()
    }

    /**
     * One-time move of the pre-release plaintext token into encrypted storage. The plaintext key
     * is always removed afterwards, even if encryption fails, so a secret is never left behind.
     */
    fun migrateLegacyPlaintextToken() {
        val legacy = preferences.getString(LEGACY_PLAINTEXT_KEY, null) ?: return
        try {
            if (legacy.isNotBlank() && read() == null) write(legacy)
        } catch (_: Exception) {
            // The phone will need to be paired again.
        } finally {
            preferences.edit().remove(LEGACY_PLAINTEXT_KEY).apply()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    companion object {
        const val LEGACY_PLAINTEXT_KEY = "development_device_token"
        private const val KEY_IV = "device_token_iv"
        private const val KEY_CIPHERTEXT = "device_token_ciphertext"
    }
}
