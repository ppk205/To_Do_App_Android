package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class RefreshTokenRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)

data class LogoutRequest(
    @SerializedName("refreshToken")
    val refreshToken: String
)

data class RevokeSessionRequest(
    @SerializedName("sessionId")
    val sessionId: String
)

data class SessionInfo(
    @SerializedName("id")
    val id: String,

    @SerializedName("device_id")
    val deviceId: String?,

    @SerializedName("device_name")
    val deviceName: String?,

    @SerializedName("issued_at")
    val issuedAt: String,

    @SerializedName("last_seen_at")
    val lastSeenAt: String?,

    @SerializedName("ip_address")
    val ipAddress: String?,

    @SerializedName("user_agent")
    val userAgent: String?,

    @SerializedName("refresh_expires_at")
    val refreshExpiresAt: String
)

data class SessionsResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("sessions")
    val sessions: List<SessionInfo>?
)

