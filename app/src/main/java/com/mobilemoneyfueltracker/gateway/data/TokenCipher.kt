package com.mobilemoneyfueltracker.gateway.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedBlob(val iv: ByteArray, val ciphertext: ByteArray)

/** Encrypts small secrets. The real implementation is hardware-backed where the device allows. */
interface TokenCipher {
    fun encrypt(plain: ByteArray): EncryptedBlob

    /** Throws if the blob cannot be decrypted, for example after the key was invalidated. */
    fun decrypt(blob: EncryptedBlob): ByteArray
}

/** AES-256-GCM with a non-exportable key held in the Android Keystore. */
class AndroidKeystoreTokenCipher(private val keyAlias: String = DEFAULT_ALIAS) : TokenCipher {
    override fun encrypt(plain: ByteArray): EncryptedBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedBlob(iv = cipher.iv, ciphertext = cipher.doFinal(plain))
    }

    override fun decrypt(blob: EncryptedBlob): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, blob.iv))
        return cipher.doFinal(blob.ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "mmft_device_token_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
