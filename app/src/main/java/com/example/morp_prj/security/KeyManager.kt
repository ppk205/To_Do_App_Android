package com.example.morp_prj.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages encryption keys for teams
 * Handles key caching and synchronization across devices
 */
class KeyManager(context: Context) {
    private val TAG = "KeyManager"

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "team_keys_secure",
        Context.MODE_PRIVATE
    )

    // Cache for team keys in memory (cleared on app restart)
    private val keyCache = mutableMapOf<String, String>()

    /**
     * Cache team key locally (encrypted with device master key)
     *
     * @param teamId Team identifier
     * @param teamKey Base64 encoded team key
     */
    fun cacheTeamKey(teamId: String, teamKey: String) {
        try {
            // Store in memory cache
            keyCache[teamId] = teamKey

            // Store encrypted in SharedPreferences
            val encryptedKey = CryptoManager.encryptForLocalStorage(teamKey)
            prefs.edit().putString("team_key_$teamId", encryptedKey).apply()

            Log.d(TAG, "Team key cached for team: $teamId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache team key", e)
        }
    }

    /**
     * Get cached team key
     *
     * @param teamId Team identifier
     * @return Team key or null if not cached
     */
    fun getCachedTeamKey(teamId: String): String? {
        // Check memory cache first
        keyCache[teamId]?.let { return it }

        // Try to load from SharedPreferences
        val encryptedKey = prefs.getString("team_key_$teamId", null) ?: return null

        return try {
            val decryptedKey = CryptoManager.decryptFromLocalStorage(encryptedKey)
            keyCache[teamId] = decryptedKey // Update memory cache
            decryptedKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt cached team key", e)
            null
        }
    }

    /**
     * Remove team key from cache
     */
    fun removeTeamKey(teamId: String) {
        keyCache.remove(teamId)
        prefs.edit().remove("team_key_$teamId").apply()
    }

    /**
     * Clear all cached keys (on logout)
     */
    fun clearAllKeys() {
        keyCache.clear()
        prefs.edit().clear().apply()
        Log.d(TAG, "All team keys cleared")
    }

    /**
     * Check if team key is available
     */
    fun hasTeamKey(teamId: String): Boolean {
        return keyCache.containsKey(teamId) || prefs.contains("team_key_$teamId")
    }

    /**
     * Store user's salt for password-based key derivation
     */
    fun storeUserSalt(userId: String, salt: String) {
        val encryptedSalt = CryptoManager.encryptForLocalStorage(salt)
        prefs.edit().putString("user_salt_$userId", encryptedSalt).apply()
    }

    /**
     * Get user's salt
     */
    fun getUserSalt(userId: String): String? {
        val encryptedSalt = prefs.getString("user_salt_$userId", null) ?: return null
        return try {
            CryptoManager.decryptFromLocalStorage(encryptedSalt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt user salt", e)
            null
        }
    }
}

