package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

/**
 * REQ-UPLOAD-01: Response model cho Cloudinary Signed Upload
 * Chứa các thông tin cần thiết để thực hiện signed upload lên Cloudinary
 */
data class CloudinarySignatureResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("signature")
    val signature: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("publicId")
    val publicId: String,

    @SerializedName("cloudName")
    val cloudName: String,

    @SerializedName("apiKey")
    val apiKey: String,

    @SerializedName("folder")
    val folder: String
)

