package com.example.morp_prj.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.morp_prj.data.model.User

class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "mopr_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_GUEST = "is_guest"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"

        const val GUEST_USER_ID = "guest_local"

        // Helper that constructs a User from stored preferences.
        // Returns null when no user id is stored (not logged in / guest).
        fun getUser(context: Context): User? {
            val prefs = PreferenceManager(context)
            val id = prefs.getUserId() ?: return null
            val username = prefs.getUsername() ?: ""
            val displayName = prefs.getDisplayName() ?: ""
            val email = prefs.getEmail() ?: ""

            // The User data class requires several non-null fields like hashedPassword.
            // We don't persist all fields in preferences, so fill missing values with sensible defaults.
            return User(
                id = id,
                username = username,
                hashedPassword = "",
                displayName = displayName,
                email = email,
                avatarUrl = null,
                avatarId = null,
                bio = null,
                phone = null,
                verified = false,
                createdAt = null,
                updatedAt = null
            )
        }

        // Persist the minimal user info to preferences used by the app
        fun saveUser(context: Context, user: User) {
            val prefs = PreferenceManager(context)
            prefs.saveLoginData(
                userId = user.id,
                username = user.username,
                displayName = user.displayName,
                email = user.email,
                token = null
            )
        }

        // Clear stored login/user data
        fun clear(context: Context) {
            val prefs = PreferenceManager(context)
            prefs.clearLoginData()
        }

        // Convenience static check for guest mode
        fun isGuest(context: Context): Boolean {
            return PreferenceManager(context).isGuest()
        }
    }

    fun saveLoginData(
        userId: String,
        username: String,
        displayName: String,
        email: String,
        token: String? = null
    ) {
        sharedPreferences.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putString(KEY_DISPLAY_NAME, displayName)
            putString(KEY_EMAIL, email)
            putString(KEY_TOKEN, token)
            putBoolean(KEY_IS_LOGGED_IN, true)
            // clear guest flag when a real user logs in
            putBoolean(KEY_IS_GUEST, false)
            apply()
        }
    }

    fun saveGuestMode() {
        sharedPreferences.edit().apply {
            putBoolean(KEY_IS_GUEST, true)
            putBoolean(KEY_IS_LOGGED_IN, false)
            // assign deterministic guest user id so offline tasks can be stored
            putString(KEY_USER_ID, GUEST_USER_ID)
            apply()
        }
    }

    // Convenience to always return an id (guest when not logged in)
    fun getCurrentUserIdOrGuest(): String = getUserId() ?: GUEST_USER_ID

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun isGuest(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_GUEST, false)
    }

    fun hasSeenOnboarding(): Boolean {
        return sharedPreferences.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }

    fun setHasSeenOnboarding(seen: Boolean = true) {
        sharedPreferences.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, seen).apply()
    }

    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }

    fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }

    fun getDisplayName(): String? {
        return sharedPreferences.getString(KEY_DISPLAY_NAME, null)
    }

    fun getEmail(): String? {
        return sharedPreferences.getString(KEY_EMAIL, null)
    }

    fun getToken(): String? {
        return sharedPreferences.getString(KEY_TOKEN, null)
    }

    fun clearLoginData() {
        // Giữ lại cờ hasSeenOnboarding khi logout
        val hasSeenOnboarding = sharedPreferences.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        sharedPreferences.edit().clear().apply()
        if (hasSeenOnboarding) {
            sharedPreferences.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
        }
    }
}