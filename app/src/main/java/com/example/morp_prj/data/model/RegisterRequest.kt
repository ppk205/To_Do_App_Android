package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("username")
    val username: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("displayName")
    val displayName: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("phone")
    val phone: String? = null
)

