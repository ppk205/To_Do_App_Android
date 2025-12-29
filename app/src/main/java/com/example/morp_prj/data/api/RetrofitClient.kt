package com.example.morp_prj.data.api

import android.content.Context
import com.example.morp_prj.security.AuthInterceptor
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.google.gson.GsonBuilder

/**
 * ========================================
 * RETROFIT CLIENT - Network Configuration
 * ========================================
 *
 * Features:
 * - Certificate pinning for production
 * - Debug logging (debug builds only)
 * - Connection timeouts
 */
object RetrofitClient {

    // Base URL - Use your actual domain in production
    private const val BASE_URL = "http://192.168.2.247:3001/"

    // ✅ Production domain for certificate pinning
    // Replace with your actual domain when deploying to production
    private const val PRODUCTION_DOMAIN = "yourdomain.com"

    // Build a Gson instance that tolerates numeric 0/1 for booleans
    private val gson = GsonBuilder()
        .registerTypeAdapter(Boolean::class.java, LenientBooleanDeserializer())
        .registerTypeAdapter(Boolean::class.javaPrimitiveType, LenientBooleanDeserializer())
        .create()

    /**
     * Check if app is in debug mode
     */
    private fun isDebugBuild(): Boolean {
        return try {
            // Try to access BuildConfig.DEBUG (will be available after gradle sync)
            Class.forName("com.example.morp_prj.BuildConfig")
                .getDeclaredField("DEBUG")
                .getBoolean(null)
        } catch (_: Exception) {
            // Fallback: assume debug if we can't determine
            true
        }
    }

    /**
     * Certificate Pinning - Protects against MITM attacks
     *
     * IMPORTANT: Replace the sample pin with your real certificate SHA-256 pins.
     */
    private val certificatePinner = CertificatePinner.Builder()
        // Example pins (replace these with your actual production pins)
        .add(PRODUCTION_DOMAIN, "sha256/YOUR_PRIMARY_PIN_BASE64=")
        .add(PRODUCTION_DOMAIN, "sha256/YOUR_BACKUP_PIN_BASE64=")
        .build()

    /**
     * Logging interceptor - Only enabled in debug builds
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (isDebugBuild()) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE // ✅ No logging in production
        }
    }

    // Backing retrofit instance which can be rebuilt when auth interceptor is set
    @Volatile
    private var retrofit: Retrofit = buildRetrofit(builder = null)

    // Public API service (recreated when retrofit rebuilt)
    @Volatile
    var authApiService: AuthApiService = retrofit.create(AuthApiService::class.java)
        private set

    @Volatile
    var taskApiService: TaskApiService = retrofit.create(TaskApiService::class.java)
        private set

    @Volatile
    var teamApiService: TeamApiService = retrofit.create(TeamApiService::class.java)
        private set

    var teamTaskApiService: TeamTaskApiService = retrofit.create(TeamTaskApiService::class.java)
        private set
    private fun buildRetrofit(builder: OkHttpClient.Builder?): Retrofit {
        val clientBuilder = builder ?: OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (!isDebugBuild()) {
            clientBuilder.certificatePinner(certificatePinner)
        }

        val client = clientBuilder.build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * Allows wiring an AuthInterceptor that requires a real Context and an authApiService without interceptor
     * Rebuilds Retrofit and the exposed `authApiService` to include the interceptor
     */
    fun setAuthInterceptor(context: Context, onRefreshFailed: () -> Unit = {}) {
        try {
            // Create authApiService without interceptor to avoid refresh loops
            val authApiServiceWithoutInterceptor = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(AuthApiService::class.java)

            val authInterceptor = AuthInterceptor(
                context = context,
                authApiService = authApiServiceWithoutInterceptor,
                onRefreshFailed = onRefreshFailed
            )

            val builder = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)

            if (!isDebugBuild()) {
                builder.certificatePinner(certificatePinner)
            }

            // Rebuild retrofit and public services
            retrofit = buildRetrofit(builder)
            authApiService = retrofit.create(AuthApiService::class.java)
            taskApiService = retrofit.create(TaskApiService::class.java)
            teamApiService = retrofit.create(TeamApiService::class.java)
            teamTaskApiService = retrofit.create(TeamTaskApiService::class.java)
        } catch (e: Exception) {
            // Fail gracefully - keep existing retrofit without auth interceptor
            if (isDebugBuild()) android.util.Log.e("RetrofitClient", "Failed to set auth interceptor", e)
        }
    }
}
