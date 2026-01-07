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

    /**
     * Check if app is in debug mode
     */
    private fun isDebugBuild(): Boolean {
        return try {
            Class.forName("com.example.morp_prj.BuildConfig")
                .getDeclaredField("DEBUG")
                .getBoolean(null)
        } catch (e: Exception) {
            // Fallback: assume debug if we can't determine
            true
        }
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for public endpoints
        if (isPublicEndpoint(originalRequest.url.encodedPath)) {
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
        if (response.code == 401) {
            response.close()

            // Attempt refresh
            val newAccessToken = synchronized(refreshLock) {
                // Double-check: another thread may have already refreshed
                val token = tokenStorage.getAccessToken()
                if (token != null && token != accessToken) {
                    token // Use the already refreshed token
                } else {
                    refreshTokenSync()
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
     * Check if endpoint is public (no auth required)
     */
    private fun isPublicEndpoint(path: String): Boolean {
        return path.contains("/auth/login") ||
                path.contains("/auth/register") ||
                path.contains("/auth/refresh") ||
                path.contains("/auth/verify-otp") ||
                path.contains("/auth/resend-otp") ||
                path.contains("/auth/forgot-password") ||
                path.contains("/auth/verify-reset-otp") ||
                path.contains("/auth/resend-reset-otp") ||
                path.contains("/auth/reset-password")
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
                refreshTokenSync()
            }
        }
    }

    /**
     * Refresh access token using refresh token (synchronous but thread-safe)
     * @return new access token or null if refresh failed
     */
    private fun refreshTokenSync(): String? {
        val refreshToken = tokenStorage.getRefreshToken()
            ?: return null

        return try {
            // ✅ Using runBlocking in synchronized block - safe for OkHttp interceptor
            runBlocking {
                val response = authApiService.refreshToken(RefreshTokenRequest(refreshToken))

                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!

                    // Save new access token
                    body.accessToken?.let { newToken ->
                        tokenStorage.saveAccessToken(newToken, body.accessTTL ?: 1800)
                    }

                    // Save new refresh token if rotated
                    body.refreshToken?.let { newRefreshToken ->
                        tokenStorage.saveRefreshToken(
                            newRefreshToken,
                            body.refreshTTL ?: 2592000
                        )
                    }

                    body.accessToken
                } else {
                    // Refresh failed, clear tokens
                    tokenStorage.clearTokens()
                    null
                }
            }
        } catch (e: Exception) {
            // ✅ Only log in debug builds to avoid leaking info
            if (isDebugBuild()) {
                android.util.Log.e("AuthInterceptor", "Refresh failed", e)
            }
            tokenStorage.clearTokens()
            null
        }
    }
}
