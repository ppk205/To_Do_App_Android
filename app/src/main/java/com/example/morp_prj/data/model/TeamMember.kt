package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class TeamMember(
    @SerializedName("id")
    val id: String,
    @SerializedName("displayName")
    val displayName: String,
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("email")
    val email: String,
    @SerializedName("avatarUrl")
    val avatarUrl: String?,
    @SerializedName("role")
    val role: String,
    @SerializedName("status")
    val status: String,
    @SerializedName("joinedAt")
val joinedAt: String? = null
)