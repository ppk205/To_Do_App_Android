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
        private const val KEY_PHONE = "phone"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_AVATAR_ID = "avatar_id"
        private const val KEY_BIO = "bio"
        private const val KEY_VERIFIED = "verified"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_GUEST = "is_guest"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_JUST_LOGGED_OUT = "just_logged_out"
        private const val KEY_PASSWORD_FAIL_COUNT = "password_fail_count"

        const val GUEST_USER_ID = "guest_local"

        // Helper that constructs a User from stored preferences.
        // Returns null when no user id is stored (not logged in / guest).
        fun getUser(context: Context): User? {
            val prefs = PreferenceManager(context)
            val id = prefs.getUserId() ?: return null
            val username = prefs.getUsername() ?: ""
            val displayName = prefs.getDisplayName() ?: ""
            val email = prefs.getEmail() ?: ""
            val phone = prefs.getPhone()
            val avatarUrl = prefs.getAvatarUrl()
            val avatarId = prefs.getAvatarId()
            val bio = prefs.getBio()
            val verified = prefs.getVerified()

            // The User data class requires several non-null fields like hashedPassword.
            // We don't persist all fields in preferences, so fill missing values with sensible defaults.
            return User(
                id = id,
                username = username,
                hashedPassword = "",
                displayName = displayName,
                email = email,
                avatarUrl = avatarUrl,
                avatarId = avatarId,
                bio = bio,
                phone = phone,
                verified = verified,
                createdAt = null,
                updatedAt = null
            )
        }

        // Persist the minimal user info to preferences used by the app
        fun saveUser(context: Context, user: User) {
            val prefs = PreferenceManager(context)
            prefs.saveFullUserData(user)
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

    // Save full user data including phone, avatarUrl, bio, etc.
    fun saveFullUserData(user: User) {
        sharedPreferences.edit().apply {
            putString(KEY_USER_ID, user.id)
            putString(KEY_USERNAME, user.username)
            putString(KEY_DISPLAY_NAME, user.displayName)
            putString(KEY_EMAIL, user.email)
            putString(KEY_PHONE, user.phone)
            putString(KEY_AVATAR_URL, user.avatarUrl)
            putString(KEY_AVATAR_ID, user.avatarId)
            putString(KEY_BIO, user.bio)
            putBoolean(KEY_VERIFIED, user.verified)
            putBoolean(KEY_IS_LOGGED_IN, true)
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

    // Flag to indicate user just logged out (to show login instead of onboarding)
    fun isJustLoggedOut(): Boolean {
        return sharedPreferences.getBoolean(KEY_JUST_LOGGED_OUT, false)
    }

    fun setJustLoggedOut(value: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_JUST_LOGGED_OUT, value).apply()
    }

    fun clearJustLoggedOut() {
        sharedPreferences.edit().remove(KEY_JUST_LOGGED_OUT).apply()
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

    fun getPhone(): String? {
        return sharedPreferences.getString(KEY_PHONE, null)
    }

    fun getAvatarUrl(): String? {
        return sharedPreferences.getString(KEY_AVATAR_URL, null)
    }

    fun getAvatarId(): String? {
        return sharedPreferences.getString(KEY_AVATAR_ID, null)
    }

    fun getBio(): String? {
        return sharedPreferences.getString(KEY_BIO, null)
    }

    fun getVerified(): Boolean {
        return sharedPreferences.getBoolean(KEY_VERIFIED, false)
    }

    fun getToken(): String? {
        return sharedPreferences.getString(KEY_TOKEN, null)
    }

    fun getPasswordFailCount(): Int {
        return sharedPreferences.getInt(KEY_PASSWORD_FAIL_COUNT, 0)
    }

    fun setPasswordFailCount(count: Int) {
        sharedPreferences.edit().putInt(KEY_PASSWORD_FAIL_COUNT, count).apply()
    }

    fun incrementPasswordFailCount() {
        val current = getPasswordFailCount()
        setPasswordFailCount(current + 1)
    }

    fun resetPasswordFailCount() {
        sharedPreferences.edit().remove(KEY_PASSWORD_FAIL_COUNT).apply()
    }

    fun clearLoginData() {
        // Clear all login data completely
        // After logout and app restart, user will see onboarding (fresh start)
        // hasSeenOnboarding will be false (default) after clear
        sharedPreferences.edit().clear().apply()
    }
}