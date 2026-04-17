package com.aare.vmax.core.engine

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityGuardEngine {

    private val KEY_ALIAS = "AARE_VMAX_KEY"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"

    private val transformation = "AES/GCM/NoPadding"

    init {
        generateKeyIfNeeded()
    }

    // -----------------------------
    // KEY GENERATION
    // -----------------------------
    private fun generateKeyIfNeeded() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {

            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)

            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )

            keyGenerator.generateKey()
        }
    }

    // -----------------------------
    // ENCRYPT
    // -----------------------------
    fun encrypt(data: String): String {

        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        // IV + DATA combine
        val combined = iv + encrypted

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    // -----------------------------
    // DECRYPT (IMPORTANT)
    // -----------------------------
    fun decrypt(encryptedData: String): String {

        val decoded = Base64.decode(encryptedData, Base64.NO_WRAP)

        val ivSize = 12 // GCM standard IV size
        val iv = decoded.sliceArray(0 until ivSize)
        val data = decoded.sliceArray(ivSize until decoded.size)

        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(128, iv)

        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

        val decrypted = cipher.doFinal(data)

        return String(decrypted, Charsets.UTF_8)
    }

    // -----------------------------
    // GET KEY
    // -----------------------------
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }
}
