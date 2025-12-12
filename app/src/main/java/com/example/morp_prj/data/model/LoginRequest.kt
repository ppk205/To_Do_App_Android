package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("usernameOrEmail")
    val usernameOrEmail: String,

    @SerializedName("password")
    val password: String
)

