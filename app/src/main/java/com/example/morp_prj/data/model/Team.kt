package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class Team(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("createdBy") val createdBy: String,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("inviteCode") val inviteCode: String?,
    @SerializedName("memberCount") val memberCount: Long = 1,
    @SerializedName("role") val role: String? = "member",
    @SerializedName("isPinned") var isPinned: Boolean = false,
    @SerializedName("status") val status: String? = "active" // Thêm trường status
)