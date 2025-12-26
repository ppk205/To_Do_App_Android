package com.example.morp_prj.examples

import android.content.Context
import com.example.morp_prj.data.api.AuthApiService
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.LoginRequest
import com.example.morp_prj.data.model.LogoutRequest
import com.example.morp_prj.security.SecureTokenStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ========================================
 * AUTH USAGE EXAMPLES
 * ========================================
 *
 * Examples of how to use the secure authentication system
 * in your app with OWASP MASTG compliance
 */

class AuthUsageExample(private val context: Context) {

    private val tokenStorage = SecureTokenStorage(context)
    private val authApi: AuthApiService = RetrofitClient.authApiService

    /**
     * Example 1: Login and save tokens
     */
    fun loginExample(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = authApi.login(
                    LoginRequest(
                        usernameOrEmail = username,
                        password = password,
                        deviceId = getDeviceId(), // Generate unique device ID
                        deviceName = android.os.Build.MODEL
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!

                    // Save tokens securely
                    body.accessToken?.let {
                        tokenStorage.saveAccessToken(it, body.accessTTL ?: 1800)
                    }

                    body.refreshToken?.let {
                        tokenStorage.saveRefreshToken(it, body.refreshTTL ?: 2592000)
                    }

                    // Save session metadata
                    body.sessionId?.let { sid ->
                        body.user?.id?.let { uid ->
                            tokenStorage.saveSessionMetadata(sid, uid)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        // Navigate to home screen
                        println("Login successful!")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        println("Login failed: ${response.body()?.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    println("Login error: ${e.message}")
                }
            }
        }
    }

    /**
     * Example 2: Logout and clear tokens
     */
    fun logoutExample() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val refreshToken = tokenStorage.getRefreshToken()

                if (refreshToken != null) {
                    // Call logout endpoint to revoke session
                    authApi.logout(LogoutRequest(refreshToken))
                }

                // Clear tokens from device (ALWAYS do this even if API call fails)
                tokenStorage.clearTokens()

                withContext(Dispatchers.Main) {
                    // Navigate to login screen
                    println("Logged out successfully")
                }
            } catch (e: Exception) {
                // Still clear tokens even if network fails
                tokenStorage.clearTokens()
                withContext(Dispatchers.Main) {
                    println("Logged out (offline)")
                }
            }
        }
    }

    /**
     * Example 3: Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return tokenStorage.hasValidRefreshToken()
    }

    /**
     * Example 4: Get access token for manual API calls
     * (Usually handled automatically by AuthInterceptor)
     */
    suspend fun getAccessToken(): String? {
        return withContext(Dispatchers.IO) {
            if (!tokenStorage.isAccessTokenExpired()) {
                tokenStorage.getAccessToken()
            } else {
                // Access token expired, refresh it
                refreshAccessTokenManually()
            }
        }
    }

    /**
     * Example 5: Manual refresh (normally handled by AuthInterceptor)
     */
    private suspend fun refreshAccessTokenManually(): String? {
        val refreshToken = tokenStorage.getRefreshToken() ?: return null

        return try {
            val response = authApi.refreshToken(
                com.example.morp_prj.data.model.RefreshTokenRequest(refreshToken)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!

                // Save new tokens
                body.accessToken?.let {
                    tokenStorage.saveAccessToken(it, body.accessTTL ?: 1800)
                }

                body.refreshToken?.let {
                    tokenStorage.saveRefreshToken(it, body.refreshTTL ?: 2592000)
                }

                body.accessToken
            } else {
                // Refresh failed, clear tokens
                tokenStorage.clearTokens()
                null
            }
        } catch (e: Exception) {
            tokenStorage.clearTokens()
            null
        }
    }

    /**
     * Example 6: View active sessions
     */
    fun viewSessionsExample() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = authApi.getSessions()

                if (response.isSuccessful && response.body()?.success == true) {
                    val sessions = response.body()!!.sessions ?: emptyList()

                    withContext(Dispatchers.Main) {
                        sessions.forEach { session ->
                            println("Device: ${session.deviceName}, Last seen: ${session.lastSeenAt}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error fetching sessions: ${e.message}")
            }
        }
    }

    /**
     * Example 7: Revoke specific session (remote logout)
     */
    fun revokeSessionExample(sessionId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = authApi.revokeSession(
                    com.example.morp_prj.data.model.RevokeSessionRequest(sessionId)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    withContext(Dispatchers.Main) {
                        println("Session revoked successfully")
                    }
                }
            } catch (e: Exception) {
                println("Error revoking session: ${e.message}")
            }
        }
    }

    /**
     * Helper: Generate device ID (implement based on your requirements)
     */
    private fun getDeviceId(): String {
        // Option 1: Use Android ID (changes on factory reset)
        // return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        // Option 2: Generate and persist UUID (survives app uninstall if using backup)
        val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }
}

/**
 * ========================================
 * SETUP AUTH INTERCEPTOR IN APPLICATION
 * ========================================
 */

/*
// In your RetrofitClient.kt or similar:

import com.example.morp_prj.security.AuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "http://your-server.com/"

    private val httpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Create auth interceptor with refresh callback
        val authInterceptor = AuthInterceptor(
            context = MyApplication.instance,
            authApiService = authApiServiceWithoutInterceptor,
            onRefreshFailed = {
                // Navigate to login screen
                // You can use a global event bus or LiveData for this
                MyApplication.instance.navigateToLogin()
            }
        )

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor) // Add AFTER logging, BEFORE network
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Auth API without interceptor (to avoid infinite loop on refresh)
    private val authApiServiceWithoutInterceptor: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }

    // Main API with interceptor
    val authApiService: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}
*/

