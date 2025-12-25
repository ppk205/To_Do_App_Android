package com.example.morp_prj.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * ========================================
 * SECURE TOKEN STORAGE - OWASP MASTG Compliant
 * ========================================
 *
 * Implements secure storage for authentication tokens using:
 * - EncryptedSharedPreferences (AndroidX Security)
 * - Android Keystore backed encryption
 * - AES256-GCM encryption scheme
 *
 * NEVER store tokens in plain SharedPreferences or log them
 */
class SecureTokenStorage(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "mopr_auth_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRY = "access_expiry"
        private const val KEY_REFRESH_EXPIRY = "refresh_expiry"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_USER_ID = "user_id"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Save access token securely
     */
    fun saveAccessToken(token: String, expiresInSeconds: Int) {
        val expiryTime = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        encryptedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, token)
            putLong(KEY_ACCESS_EXPIRY, expiryTime)
            apply()
        }
    }

    /**
     * Save refresh token securely
     */
    fun saveRefreshToken(token: String, expiresInSeconds: Int) {
        val expiryTime = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        encryptedPrefs.edit().apply {
            putString(KEY_REFRESH_TOKEN, token)
            putLong(KEY_REFRESH_EXPIRY, expiryTime)
            apply()
        }
    }

    /**
     * Save session metadata
     */
    fun saveSessionMetadata(sessionId: String, userId: String) {
        encryptedPrefs.edit().apply {
            putString(KEY_SESSION_ID, sessionId)
            putString(KEY_USER_ID, userId)
            apply()
        }
    }

    /**
     * Get access token if not expired
     */
    fun getAccessToken(): String? {
        val token = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val expiry = encryptedPrefs.getLong(KEY_ACCESS_EXPIRY, 0L)

        // Check if token is expired (with 30s buffer for clock skew)
        if (token != null && System.currentTimeMillis() < expiry - 30000) {
            return token
        }
        return null
    }

    /**
     * Get refresh token
     */
    fun getRefreshToken(): String? {
        return encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Check if access token is expired or about to expire (within 1 min)
     */
    fun isAccessTokenExpired(): Boolean {
        val expiry = encryptedPrefs.getLong(KEY_ACCESS_EXPIRY, 0L)
        return System.currentTimeMillis() >= expiry - 60000 // 1 min buffer
    }

    /**
     * Check if refresh token exists and not expired
     */
    fun hasValidRefreshToken(): Boolean {
        val token = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        val expiry = encryptedPrefs.getLong(KEY_REFRESH_EXPIRY, 0L)
        return token != null && System.currentTimeMillis() < expiry
    }

    /**
     * Get session ID
     */
    fun getSessionId(): String? {
        return encryptedPrefs.getString(KEY_SESSION_ID, null)
    }

    /**
     * Get user ID
     */
    fun getUserId(): String? {
        return encryptedPrefs.getString(KEY_USER_ID, null)
    }

    /**
     * Clear all tokens (logout)
     */
    fun clearTokens() {
        encryptedPrefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_ACCESS_EXPIRY)
            remove(KEY_REFRESH_EXPIRY)
            remove(KEY_SESSION_ID)
            remove(KEY_USER_ID)
            apply()
        }
    }

    /**
     * Clear everything
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }
}

