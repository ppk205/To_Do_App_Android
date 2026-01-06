package com.example.morp_prj.utils

import android.content.Context
import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.morp_prj.BuildConfig
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.CloudinarySignatureResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Helper class for uploading images to Cloudinary
 * REQ-UPLOAD-01: Sử dụng Signed Uploads để bảo mật
 *
 * Flow:
 * 1. App gọi server để lấy signature (getCloudinarySignature)
 * 2. App sử dụng signature để upload ảnh lên Cloudinary
 * 3. Server validate signature -> chỉ user đã xác thực mới upload được
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
     * REQ-UPLOAD-01: Lấy signature từ server trước khi upload
     * @return Result<CloudinarySignatureResponse> - Signature data hoặc lỗi
     */
    private suspend fun getSignatureFromServer(): Result<CloudinarySignatureResponse> = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.authApiService.getCloudinarySignature()

            if (response.isSuccessful && response.body() != null) {
                val signatureData = response.body()!!
                android.util.Log.d("CloudinaryHelper", "Got signature from server: publicId=${signatureData.publicId}")
                Result.success(signatureData)
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Failed to get signature"
                android.util.Log.e("CloudinaryHelper", "Signature error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("CloudinaryHelper", "Exception getting signature", e)
            Result.failure(e)
        }
    }

    /**
     * REQ-UPLOAD-01: Upload image to Cloudinary using SIGNED upload
     * Bảo mật hơn unsigned preset - yêu cầu server-side signature
     *
     * @param uri Image URI from gallery or camera
     * @return Result<String> - Success with secure URL or Failure with error
     */
    suspend fun uploadImage(uri: Uri): Result<String> {
        // Bước 1: Lấy signature từ server
        val signatureResult = getSignatureFromServer()

        if (signatureResult.isFailure) {
            return Result.failure(signatureResult.exceptionOrNull()
                ?: Exception("Không thể lấy signature từ server"))
        }

        val signatureData = signatureResult.getOrNull()!!

        // Bước 2: Upload với signature
        return uploadWithSignature(uri, signatureData)
    }

    /**
     * REQ-UPLOAD-01: Upload ảnh với signature đã lấy từ server
     */
    private suspend fun uploadWithSignature(
        uri: Uri,
        signatureData: CloudinarySignatureResponse
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            android.util.Log.d("CloudinaryHelper", "Starting signed upload: publicId=${signatureData.publicId}")

            // REQ-UPLOAD-01: Signed upload với Cloudinary
            // Không có method .signed() - thay vào đó set các options
            MediaManager.get().upload(uri)
                .option("signature", signatureData.signature)
                .option("timestamp", signatureData.timestamp)
                .option("public_id", signatureData.publicId)
                .option("folder", signatureData.folder)
                .option("api_key", signatureData.apiKey)
                .option("resource_type", "image") // Chỉ định resource type
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        android.util.Log.d("CloudinaryHelper", "Signed upload started: $requestId")
                    }

                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        val progress = (bytes * 100 / totalBytes).toInt()
                        android.util.Log.d("CloudinaryHelper", "Upload progress: $progress%")
                    }

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        android.util.Log.d("CloudinaryHelper", "Signed upload success: $url")

                        if (url != null) {
                            continuation.resume(Result.success(url))
                        } else {
                            continuation.resume(Result.failure(Exception("URL not found in response")))
                        }
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        android.util.Log.e("CloudinaryHelper", "Signed upload error: ${error.description}")
                        continuation.resume(Result.failure(Exception(error.description)))
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        android.util.Log.w("CloudinaryHelper", "Upload rescheduled: ${error.description}")
                    }
                }).dispatch()
        } catch (e: Exception) {
            android.util.Log.e("CloudinaryHelper", "Exception during signed upload", e)
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Check if Cloudinary is initialized
     */
    fun isInitialized(): Boolean = isInitialized
}