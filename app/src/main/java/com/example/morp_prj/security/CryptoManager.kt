package com.example.morp_prj.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager for E2EE encryption/decryption
 * Supports multi-device synchronization via password-based key derivation
 */
object CryptoManager {
    private const val TAG = "CryptoManager"

    // AES-GCM Configuration
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val AES_KEY_SIZE = 256

    // PBKDF2 Configuration for password-based encryption
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100000
    private const val PBKDF2_KEY_LENGTH = 256

    // Android KeyStore (device-specific storage)
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "MorpMasterKey"

    /**
     * Generate a new random AES key for team encryption
     * @return Base64 encoded 256-bit AES key
     */
    fun generateTeamKey(): String {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE, SecureRandom())
        val secretKey = keyGenerator.generateKey()
        return Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Generate deterministic team key from teamId
     * This ensures all members of the same team get the same key
     *
     * WARNING: This is a temporary solution for development/testing only!
     * Production should use proper key exchange via server.
     *
     * @param teamId Team identifier
     * @return Base64 encoded 256-bit AES key
     */
    fun generateDeterministicTeamKey(teamId: String): String {
        // Use PBKDF2 to derive key from teamId
        val salt = "MORP_TEAM_SALT_2026".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(teamId.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val key = factory.generateSecret(spec)
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    /**
     * Derive encryption key from user password using PBKDF2
     * Used to encrypt/decrypt team keys stored on server
     *
     * @param password User's password
     * @param salt Base64 encoded salt (should be unique per user)
     * @return Base64 encoded derived key
     */
    fun deriveKeyFromPassword(password: String, salt: String): String {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val key = factory.generateSecret(spec)
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    /**
     * Generate random salt for PBKDF2
     * @return Base64 encoded salt
     */
    fun generateSalt(): String {
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    /**
     * Encrypt data using team key
     *
     * @param plaintext Text to encrypt
     * @param teamKeyBase64 Base64 encoded team AES key
     * @return Base64 encoded string format: "IV:EncryptedData"
     */
    fun encryptWithTeamKey(plaintext: String?, teamKeyBase64: String?): String? {
        if (plaintext.isNullOrEmpty() || teamKeyBase64.isNullOrEmpty()) return plaintext

        try {
            val keyBytes = Base64.decode(teamKeyBase64, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            return "$ivBase64:$encryptedBase64"
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            return plaintext
        }
    }

    /**
     * Decrypt data using team key
     *
     * @param encryptedData Format: "IV:EncryptedData"
     * @param teamKeyBase64 Base64 encoded team AES key
     * @return Decrypted plaintext
     */
    fun decryptWithTeamKey(encryptedData: String?, teamKeyBase64: String?): String? {
        if (encryptedData.isNullOrEmpty() || teamKeyBase64.isNullOrEmpty()) return encryptedData

        try {
            val parts = encryptedData.split(":")
            if (parts.size != 2) return encryptedData

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

            val keyBytes = Base64.decode(teamKeyBase64, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            return encryptedData
        }
    }

    /**
     * Encrypt team key with user's password-derived key
     * Used to store team keys on server
     *
     * @param teamKey Base64 encoded team key
     * @param passwordDerivedKey Base64 encoded key derived from password
     * @return Encrypted team key format: "IV:EncryptedKey"
     */
    fun encryptTeamKeyForStorage(teamKey: String, passwordDerivedKey: String): String {
        return encryptWithTeamKey(teamKey, passwordDerivedKey) ?: teamKey
    }

    /**
     * Decrypt team key from server using password-derived key
     *
     * @param encryptedTeamKey Format: "IV:EncryptedKey"
     * @param passwordDerivedKey Base64 encoded key derived from password
     * @return Decrypted team key
     */
    fun decryptTeamKeyFromStorage(encryptedTeamKey: String, passwordDerivedKey: String): String {
        return decryptWithTeamKey(encryptedTeamKey, passwordDerivedKey) ?: encryptedTeamKey
    }

    /**
     * Get or create master key in Android KeyStore (device-specific)
     * Used for local caching only
     */
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            return (keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt data for local device storage (device-specific)
     */
    fun encryptForLocalStorage(plaintext: String): String {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            return "$ivBase64:$encryptedBase64"
        } catch (e: Exception) {
            Log.e(TAG, "Local encryption failed", e)
            return plaintext
        }
    }

    /**
     * Decrypt data from local device storage
     */
    fun decryptFromLocalStorage(encryptedData: String): String {
        try {
            val parts = encryptedData.split(":")
            if (parts.size != 2) return encryptedData

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Local decryption failed", e)
            return encryptedData
        }
    }

    /**
     * Encrypt list of strings
     */
    fun encryptList(list: List<String>?, teamKey: String?): List<String>? {
        return list?.map { encryptWithTeamKey(it, teamKey) ?: it }
    }

    /**
     * Decrypt list of strings
     */
    fun decryptList(list: List<String>?, teamKey: String?): List<String>? {
        return list?.map { decryptWithTeamKey(it, teamKey) ?: it }
    }
}

