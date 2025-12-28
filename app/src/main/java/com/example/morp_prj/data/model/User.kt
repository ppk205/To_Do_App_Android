package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("id")
    val id: String,

    @SerializedName("username")
    val username: String,

    // added hashedPassword (DB: varchar(255) NOT NULL)
    @SerializedName("hashedPassword")
    val hashedPassword: String,

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

    // added verified (DB: tinyint(1) NOT NULL default 0)
    @SerializedName("verified")
    val verified: Boolean = false,

    @SerializedName("createdAt")
    val createdAt: String? = null,

    @SerializedName("updatedAt")
    val updatedAt: String? = null
)
