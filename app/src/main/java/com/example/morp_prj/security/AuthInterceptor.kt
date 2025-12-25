package com.example.morp_prj.security

import android.content.Context
import com.example.morp_prj.data.api.AuthApiService
import com.example.morp_prj.data.model.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * ========================================
 * AUTH INTERCEPTOR - OWASP MASTG Compliant
 * ========================================
 *
 * Automatically handles:
 * - Adding access token to requests
 * - Refreshing expired access tokens
 * - Forcing re-login on refresh failure
 *
 * Thread-safe token refresh with synchronized block
 */
class AuthInterceptor(
    private val context: Context,
    private val authApiService: AuthApiService,
    private val onRefreshFailed: () -> Unit
) : Interceptor {

    private val tokenStorage = SecureTokenStorage(context)
    private val refreshLock = Any()

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for public endpoints
        if (originalRequest.url().encodedPath().contains("/auth/login") ||
            originalRequest.url().encodedPath().contains("/auth/register") ||
            originalRequest.url().encodedPath().contains("/auth/refresh") ||
            originalRequest.url().encodedPath().contains("/auth/verify-otp")
        ) {
            return chain.proceed(originalRequest)
        }

        // Get or refresh access token
        val accessToken = getValidAccessToken()
            ?: run {
                // No valid token, trigger re-login
                onRefreshFailed()
                throw IOException("Authentication required")
            }

        // Add access token to request
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()

        val response = chain.proceed(authenticatedRequest)

        // If 401 Unauthorized, try to refresh once
        if (response.code() == 401) {
            response.close()

            // Attempt refresh
            val newAccessToken = synchronized(refreshLock) {
                // Double-check: another thread may have already refreshed
                val token = tokenStorage.getAccessToken()
                if (token != null && token != accessToken) {
                    token // Use the already refreshed token
                } else {
                    refreshAccessToken()
                }
            }

            if (newAccessToken != null) {
                // Retry original request with new token
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
                return chain.proceed(retryRequest)
            } else {
                // Refresh failed, trigger re-login
                onRefreshFailed()
                throw IOException("Session expired. Please login again.")
            }
        }

        return response
    }

    /**
     * Get valid access token or null if expired/missing
     */
    private fun getValidAccessToken(): String? {
        // If access token is still valid, return it
        if (!tokenStorage.isAccessTokenExpired()) {
            return tokenStorage.getAccessToken()
        }

        // Access token expired, try refresh
        return synchronized(refreshLock) {
            // Double-check after acquiring lock
            if (!tokenStorage.isAccessTokenExpired()) {
                tokenStorage.getAccessToken()
            } else {
                refreshAccessToken()
            }
        }
    }

    /**
     * Refresh access token using refresh token
     * @return new access token or null if refresh failed
     */
    private fun refreshAccessToken(): String? {
        val refreshToken = tokenStorage.getRefreshToken()
            ?: return null

        return try {
            runBlocking {
                val response = authApiService.refreshToken(RefreshTokenRequest(refreshToken))

                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!

                    // Save new tokens
                    tokenStorage.saveAccessToken(
                        body.accessToken ?: return@runBlocking null,
                        body.accessTTL ?: 1800
                    )

                    body.refreshToken?.let { newRefreshToken ->
                        tokenStorage.saveRefreshToken(
                            newRefreshToken,
                            body.refreshTTL ?: 2592000
                        )
                    }

                    body.accessToken
                } else {
                    // Refresh failed
                    tokenStorage.clearTokens()
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthInterceptor", "Refresh failed", e)
            tokenStorage.clearTokens()
            null
        }
    }
}
