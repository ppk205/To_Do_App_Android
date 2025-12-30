package com.example.morp_prj.data.repository

import android.content.Context
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.AuthResponse
import com.example.morp_prj.data.model.LoginRequest
import com.example.morp_prj.data.model.RegisterRequest
import com.example.morp_prj.security.SecureTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val context: Context) {

    private val apiService = RetrofitClient.authApiService
    private val tokenStorage = SecureTokenStorage(context)

    suspend fun register(
        username: String,
        password: String,
        displayName: String,
        email: String,
        phone: String? = null
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = RegisterRequest(
                username = username,
                password = password,
                displayName = displayName,
                email = email,
                phone = phone
            )

            val response = apiService.register(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.message() ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(
        usernameOrEmail: String,
        password: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = LoginRequest(
                usernameOrEmail = usernameOrEmail,
                password = password,
                deviceId = getDeviceId(),
                deviceName = android.os.Build.MODEL
            )

            val response = apiService.login(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!

                // ✅ Save tokens to secure storage
                body.accessToken?.let { token ->
                    tokenStorage.saveAccessToken(token, body.accessTTL ?: 1800)
                }

                body.refreshToken?.let { token ->
                    tokenStorage.saveRefreshToken(token, body.refreshTTL ?: 2592000)
                }

                // Save session metadata
                body.sessionId?.let { sessionId ->
                    body.user?.id?.let { userId ->
                        tokenStorage.saveSessionMetadata(sessionId, userId)
                    }
                }

                Result.success(body)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message() ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate or retrieve device ID
     */
    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    /**
     * Fetch user profile from server
     */
    suspend fun fetchProfile(): Result<com.example.morp_prj.data.model.User> = withContext(Dispatchers.IO) {
        try {
            val token = tokenStorage.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No access token"))
            }

            val response = apiService.getProfile("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.message() ?: "Failed to fetch profile"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user profile with Cloudinary avatar URL only
     * @param avatarUrl Cloudinary HTTPS URL (uploaded from app)
     */
    suspend fun updateProfile(
        displayName: String?,
        phone: String?,
        bio: String?,
        avatarUrl: String?
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = com.example.morp_prj.data.model.UpdateProfileWithDriveLinkRequest(
                displayName = displayName,
                phone = phone,
                bio = bio,
                avatarUrl = avatarUrl
            )

            val response = apiService.updateProfileWithDriveLink(request)

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Update failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

