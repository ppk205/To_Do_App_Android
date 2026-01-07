package com.example.morp_prj.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages user passwords securely for encryption key derivation
 * Uses Android KeyStore for encryption
 *
 * NOTE: Passwords are only cached for current session by default
 * For persistent storage, user must opt-in (e.g., via biometric unlock)
 */
class PasswordManager(context: Context) {

    private val TAG = "PasswordManager"

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "password_cache_secure",
        Context.MODE_PRIVATE
    )

    // In-memory password cache (cleared on app restart)
    private val sessionCache = mutableMapOf<String, String>()

    companion object {
        private const val KEYSTORE_ALIAS = "PasswordEncryptionKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    /**
     * Get or create encryption key from Android KeyStore
     */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false) // Can be set to true for biometric
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Cache password in current session (memory only)
     * Cleared when app is killed
     */
    fun cachePasswordForSession(userId: String, password: String) {
        sessionCache[userId] = password
        Log.d(TAG, "Password cached for session (user: $userId)")
    }

    /**
     * Get password from session cache
     */
    fun getPasswordFromSession(userId: String): String? {
        return sessionCache[userId]
    }

    /**
     * Store password persistently (encrypted with Android KeyStore)
     * User must explicitly opt-in for this
     */
    fun storePasswordPersistent(userId: String, password: String): Boolean {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

            val iv = cipher.iv
            val encryptedPassword = cipher.doFinal(password.toByteArray(Charsets.UTF_8))

            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val passwordBase64 = Base64.encodeToString(encryptedPassword, Base64.NO_WRAP)

            prefs.edit()
                .putString("${userId}_password", "$ivBase64:$passwordBase64")
                .apply()

            Log.d(TAG, "Password stored persistently (encrypted) for user: $userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store password", e)
            false
        }
    }

    /**
     * Retrieve persistently stored password
     */
    fun getPasswordPersistent(userId: String): String? {
        return try {
            val encryptedData = prefs.getString("${userId}_password", null) ?: return null

            val parts = encryptedData.split(":")
            if (parts.size != 2) return null

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedPassword = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

            val decryptedPassword = cipher.doFinal(encryptedPassword)
            String(decryptedPassword, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve password", e)
            null
        }
    }

    /**
     * Clear password for user
     */
    fun clearPassword(userId: String) {
        sessionCache.remove(userId)
        prefs.edit().remove("${userId}_password").apply()
        Log.d(TAG, "Password cleared for user: $userId")
    }

    /**
     * Clear all passwords (on logout)
     */
    fun clearAllPasswords() {
        sessionCache.clear()
        prefs.edit().clear().apply()
        Log.d(TAG, "All passwords cleared")
    }

    /**
     * Check if password is available (session or persistent)
     */
    fun hasPassword(userId: String): Boolean {
        return sessionCache.containsKey(userId) ||
               prefs.contains("${userId}_password")
    }

    /**
     * Get password from any available source
     */
    fun getPassword(userId: String): String? {
        // Try session cache first
        var password = getPasswordFromSession(userId)
        if (password != null) return password

        // Try persistent storage
        password = getPasswordPersistent(userId)
        if (password != null) {
            // Also cache in session for faster access
            cachePasswordForSession(userId, password)
            return password
        }

        return null
    }
}

