package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("id")
    val id: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("displayName")
    val displayName: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("avatarUrl")
    val avatarUrl: String? = null,

    @SerializedName("avatarId")
    val avatarId: String? = null,

    @SerializedName("bio")
    val bio: String? = null,

    @SerializedName("phone")
    val phone: String? = null,

    @SerializedName("createdAt")
    val createdAt: String? = null,

    @SerializedName("updatedAt")
    val updatedAt: String? = null
)


