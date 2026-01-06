package com.example.morp_prj.utils

import android.content.Context
import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.morp_prj.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper class for uploading images to Cloudinary
 *
 * Setup:
 * 1. Tạo tài khoản miễn phí tại https://cloudinary.com
 * 2. Lấy thông tin từ Dashboard và thêm vào local.properties:
 *    cloudinary.cloud_name=YOUR_CLOUD_NAME
 *    cloudinary.api_key=YOUR_API_KEY
 *    cloudinary.upload_preset=YOUR_UNSIGNED_PRESET
 * 3. Sync Gradle và Build project
 *
 * Xem hướng dẫn chi tiết trong file CLOUDINARY_SETUP.md
 */
object CloudinaryHelper {
    private var isInitialized = false

    /**
     * Initialize Cloudinary with your credentials from BuildConfig
     * Call this once in Application onCreate or Fragment onViewCreated
     */
    fun init(context: Context) {
        if (!isInitialized) {
            val config = hashMapOf<String, String>(
                "cloud_name" to getCloudName(),
                "api_key" to getApiKey()
            )

            try {
                MediaManager.init(context, config)
                isInitialized = true
                android.util.Log.d("CloudinaryHelper", "Cloudinary initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("CloudinaryHelper", "Failed to initialize Cloudinary", e)
            }
        }
    }

    /**
     * Get cloud name from BuildConfig or default
     */
    private fun getCloudName(): String {
        return try {
            BuildConfig.CLOUDINARY_CLOUD_NAME
        } catch (e: Exception) {
            "dxohngowm" // Default fallback
        }
    }

    /**
     * Get API key from BuildConfig or default
     */
    private fun getApiKey(): String {
        return try {
            BuildConfig.CLOUDINARY_API_KEY
        } catch (e: Exception) {
            "687411225619873" // Default fallback
        }
    }

    /**
     * Get upload preset from BuildConfig or default
     */
    private fun getUploadPreset(): String {
        return try {
            BuildConfig.CLOUDINARY_UPLOAD_PRESET
        } catch (e: Exception) {
            "upload_project" // Default fallback
        }
    }

    /**
     * Upload image to Cloudinary and return secure URL
     *
     * @param uri Image URI from gallery or camera
     * @return Result<String> - Success with secure URL or Failure with error
     */
    suspend fun uploadImage(uri: Uri): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            MediaManager.get().upload(uri)
                .unsigned(getUploadPreset())
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        android.util.Log.d("CloudinaryHelper", "Upload started: $requestId")
                    }

                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        val progress = (bytes * 100 / totalBytes).toInt()
                        android.util.Log.d("CloudinaryHelper", "Upload progress: $progress%")
                    }

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        android.util.Log.d("CloudinaryHelper", "Upload success: $url")

                        if (url != null) {
                            continuation.resume(Result.success(url))
                        } else {
                            continuation.resume(Result.failure(Exception("URL not found in response")))
                        }
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        android.util.Log.e("CloudinaryHelper", "Upload error: ${error.description}")
                        continuation.resume(Result.failure(Exception(error.description)))
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        android.util.Log.w("CloudinaryHelper", "Upload rescheduled: ${error.description}")
                    }
                }).dispatch()
        } catch (e: Exception) {
            android.util.Log.e("CloudinaryHelper", "Exception during upload", e)
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Check if Cloudinary is initialized
     */
    fun isInitialized(): Boolean = isInitialized
}