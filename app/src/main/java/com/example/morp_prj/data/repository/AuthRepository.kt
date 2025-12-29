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
}
