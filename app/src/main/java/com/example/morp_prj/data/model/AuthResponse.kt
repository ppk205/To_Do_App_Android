package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("user")
    val user: User? = null,

    @SerializedName("token")
    val token: String? = null,

    @SerializedName("userId")
    val userId: String? = null,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("expiresIn")
    val expiresIn: Int? = null,

    @SerializedName("resendAvailableIn")
    val resendAvailableIn: Int? = null,

    @SerializedName("accessToken")
    val accessToken: String? = null,

    @SerializedName("refreshToken")
    val refreshToken: String? = null,

    @SerializedName("accessTTL")
    val accessTTL: Int? = null,

    @SerializedName("refreshTTL")
    val refreshTTL: Int? = null,

    @SerializedName("sessionId")
    val sessionId: String? = null
)
