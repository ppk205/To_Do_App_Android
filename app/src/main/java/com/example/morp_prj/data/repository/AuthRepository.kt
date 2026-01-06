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

            android.util.Log.d("AuthRepository", "Register request: $request")

            val response = apiService.register(request)

            android.util.Log.d("AuthRepository", "Register response code: ${response.code()}")

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // Try to parse error body
                val errorBody = response.errorBody()?.string()
                android.util.Log.e("AuthRepository", "Register error: $errorBody")

                val errorMessage = try {
                    val jsonObject = org.json.JSONObject(errorBody ?: "{}")
                    jsonObject.optString("message", response.message())
                } catch (e: Exception) {
                    response.message()
                }

                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Register exception", e)
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
                // Extract error message from response body, prioritizing server message
                val errorMessage = when {
                    response.body()?.message?.isNotBlank() == true -> response.body()!!.message
                    response.code() == 401 -> "Incorrect password. Please try again."
                    response.code() == 404 -> "User not found. Please check your username or email."
                    !response.isSuccessful -> "Login failed: ${response.message()}"
                    else -> "Login failed. Please try again."
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send forgot password request (OTP-based flow)
     */
    suspend fun forgotPassword(email: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = mapOf("email" to email)
            val response = apiService.forgotPassword(requestBody)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message() ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verify OTP for password reset
     */
    suspend fun verifyResetOTP(email: String, otp: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = mapOf("email" to email, "otp" to otp)
            val response = apiService.verifyResetOTP(requestBody)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message() ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resend OTP for password reset
     */
    suspend fun resendResetOTP(email: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = mapOf("email" to email)
            val response = apiService.resendResetOTP(requestBody)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message() ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reset password using OTP
     */
    suspend fun resetPassword(
        email: String,
        otp: String,
        newPassword: String,
        confirmPassword: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = mapOf(
                "email" to email,
                "otp" to otp,
                "newPassword" to newPassword,
                "confirmPassword" to confirmPassword
            )
            val response = apiService.resetPassword(requestBody)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
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
        avatarUrl: String?,
        githubUrl: String? = null,
        linkedinUrl: String? = null,
        websiteUrl: String? = null
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = com.example.morp_prj.data.model.UpdateProfileWithDriveLinkRequest(
                displayName = displayName,
                phone = phone,
                bio = bio,
                avatarUrl = avatarUrl,
                githubUrl = githubUrl,
                linkedinUrl = linkedinUrl,
                websiteUrl = websiteUrl
            )

            val response = apiService.updateProfile(request)

            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                val authResponse = AuthResponse(
                    success = true,
                    message = "Profile updated successfully",
                    user = user
                )
                Result.success(authResponse)
            } else {
                Result.failure(Exception(response.message() ?: "Update failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Change password (requires old password verification)
     */
    suspend fun changePassword(
        oldPassword: String,
        newPassword: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val token = tokenStorage.getAccessToken()
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No access token"))
            }

            val requestBody = mapOf(
                "oldPassword" to oldPassword,
                "newPassword" to newPassword
            )
            val response = apiService.changePassword("Bearer $token", requestBody)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                // Parse error message from response body
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    org.json.JSONObject(errorBody ?: "").optString("message", "Change password failed")
                } catch (e: Exception) {
                    response.message() ?: "Change password failed"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
