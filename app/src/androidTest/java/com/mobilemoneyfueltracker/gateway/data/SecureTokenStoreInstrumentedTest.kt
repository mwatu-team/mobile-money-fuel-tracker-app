package com.mobilemoneyfueltracker.gateway.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android Keystore, which JVM unit tests cannot. Run on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class SecureTokenStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsName = "mmft_gateway_settings"
    private val alias = "mmft_test_key_${System.nanoTime()}"

    @Before
    fun clean() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let {
            if (it.containsAlias(alias)) it.deleteEntry(alias)
        }
    }

    @Test
    fun cipherRoundTripsAndNeverReturnsPlaintextAsCiphertext() {
        val cipher = AndroidKeystoreTokenCipher(alias)
        val plain = "fictional-token-123".toByteArray()
        val blob = cipher.encrypt(plain)
        assertThat(blob.ciphertext).isNotEqualTo(plain)
        assertThat(cipher.decrypt(blob)).isEqualTo(plain)
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        val cipher = AndroidKeystoreTokenCipher(alias)
        val first = cipher.encrypt("same".toByteArray())
        val second = cipher.encrypt("same".toByteArray())
        assertThat(first.iv).isNotEqualTo(second.iv)
        assertThat(first.ciphertext).isNotEqualTo(second.ciphertext)
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val cipher = AndroidKeystoreTokenCipher(alias)
        val blob = cipher.encrypt("fictional-token-123".toByteArray())
        val tampered = blob.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertThrows(Exception::class.java) { cipher.decrypt(EncryptedBlob(blob.iv, tampered)) }
    }

    @Test
    fun storeKeepsTheTokenEncryptedInSharedPreferences() {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val store = SecureTokenStore(prefs, AndroidKeystoreTokenCipher(alias))
        store.write("fictional-token-123")
        assertThat(store.read()).isEqualTo("fictional-token-123")
        assertThat(prefs.all.values.joinToString()).doesNotContain("fictional-token-123")
    }

    @Test
    fun settingsRepositoryMigratesAndDeletesAPreReleasePlaintextToken() {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString(SecureTokenStore.LEGACY_PLAINTEXT_KEY, "legacy-token-456").commit()

        val repository = GatewaySettingsRepository(context)

        assertThat(repository.settings.value.deviceToken).isEqualTo("legacy-token-456")
        assertThat(prefs.contains(SecureTokenStore.LEGACY_PLAINTEXT_KEY)).isFalse()
        assertThat(prefs.all.values.joinToString()).doesNotContain("legacy-token-456")
    }

    @Test
    fun savingAPairingStoresCredentialsWithoutDisturbingTrustedSenders() {
        val repository = GatewaySettingsRepository(context)
        repository.update(repository.settings.value.copy(trustedSenders = setOf("FICTIONAL-MOMO")))

        repository.savePairing("device-1", "paired-token-789", "Demo station")

        val settings = repository.settings.value
        assertThat(settings.deviceIdentifier).isEqualTo("device-1")
        assertThat(settings.deviceToken).isEqualTo("paired-token-789")
        assertThat(settings.stationName).isEqualTo("Demo station")
        assertThat(settings.isPaired).isTrue()
        assertThat(settings.trustedSenders).isNotEmpty()
    }
}
