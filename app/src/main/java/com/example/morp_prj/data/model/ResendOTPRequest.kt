package com.example.morp_prj.data.model

data class ResendOTPRequest(
    val userId: String,
    val email: String,
    val purpose: String = "REGISTER"
)

