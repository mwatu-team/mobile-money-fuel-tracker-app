package com.mobilemoneyfueltracker.gateway.data

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** In-memory SharedPreferences, so the store can be tested without an Android runtime. */
class FakePreferences : SharedPreferences {
    val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (values[key] as Set<String>?)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            removals.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}

/** Reversible stand-in for the Keystore cipher. Records whether a plaintext ever reached it. */
class FakeCipher(var failDecrypt: Boolean = false, var failEncrypt: Boolean = false) : TokenCipher {
    override fun encrypt(plain: ByteArray): EncryptedBlob {
        check(!failEncrypt) { "keystore unavailable" }
        return EncryptedBlob(iv = byteArrayOf(1, 2, 3), ciphertext = plain.map { (it + 1).toByte() }.toByteArray())
    }

    override fun decrypt(blob: EncryptedBlob): ByteArray {
        check(!failDecrypt) { "key invalidated" }
        return blob.ciphertext.map { (it - 1).toByte() }.toByteArray()
    }
}

class SecureTokenStoreTest {
    private val prefs = FakePreferences()
    private val cipher = FakeCipher()
    private val store = SecureTokenStore(prefs, cipher)

    @Test
    fun roundTripsTheToken() {
        store.write("fictional-token-123")
        assertThat(store.read()).isEqualTo("fictional-token-123")
    }

    @Test
    fun neverStoresThePlaintextToken() {
        store.write("fictional-token-123")
        assertThat(prefs.values.values.map { it.toString() }.joinToString()).doesNotContain("fictional-token-123")
    }

    @Test
    fun readsNullWhenNothingIsStored() {
        assertThat(store.read()).isNull()
    }

    @Test
    fun undecryptableTokenIsDiscardedSoThePhoneMustBePairedAgain() {
        store.write("fictional-token-123")
        cipher.failDecrypt = true
        assertThat(store.read()).isNull()
        cipher.failDecrypt = false
        assertThat(store.read()).isNull()
    }

    @Test
    fun clearRemovesTheToken() {
        store.write("fictional-token-123")
        store.clear()
        assertThat(store.read()).isNull()
    }

    @Test
    fun blankTokenIsRejectedAndNothingIsStored() {
        runCatching { store.write("  ") }
        assertThat(store.read()).isNull()
    }

    @Test
    fun failedEncryptionStoresNothing() {
        cipher.failEncrypt = true
        runCatching { store.write("fictional-token-123") }
        cipher.failEncrypt = false
        assertThat(store.read()).isNull()
    }

    @Test
    fun migrationMovesLegacyPlaintextIntoEncryptedStorageAndDeletesIt() {
        prefs.values[SecureTokenStore.LEGACY_PLAINTEXT_KEY] = "legacy-token-456"
        store.migrateLegacyPlaintextToken()
        assertThat(store.read()).isEqualTo("legacy-token-456")
        assertThat(prefs.contains(SecureTokenStore.LEGACY_PLAINTEXT_KEY)).isFalse()
        assertThat(prefs.values.values.map { it.toString() }.joinToString()).doesNotContain("legacy-token-456")
    }

    @Test
    fun migrationNeverOverwritesAnAlreadyEncryptedToken() {
        store.write("new-token")
        prefs.values[SecureTokenStore.LEGACY_PLAINTEXT_KEY] = "stale-legacy-token"
        store.migrateLegacyPlaintextToken()
        assertThat(store.read()).isEqualTo("new-token")
        assertThat(prefs.contains(SecureTokenStore.LEGACY_PLAINTEXT_KEY)).isFalse()
    }

    @Test
    fun migrationDeletesTheLegacyKeyEvenWhenEncryptionFails() {
        cipher.failEncrypt = true
        prefs.values[SecureTokenStore.LEGACY_PLAINTEXT_KEY] = "legacy-token-456"
        store.migrateLegacyPlaintextToken()
        assertThat(prefs.contains(SecureTokenStore.LEGACY_PLAINTEXT_KEY)).isFalse()
        cipher.failEncrypt = false
        assertThat(store.read()).isNull()
    }

    @Test
    fun migrationIsANoOpWithoutALegacyValue() {
        store.migrateLegacyPlaintextToken()
        assertThat(store.read()).isNull()
    }
}
