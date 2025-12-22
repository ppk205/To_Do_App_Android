package com.example.morp_prj.data.model

data class VerifyOTPRequest(
    val userId: String,
    val email: String,
    val otp: String,
    val purpose: String = "REGISTER"
)

