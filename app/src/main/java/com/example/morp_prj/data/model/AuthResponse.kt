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
    val email: String? = null
)

