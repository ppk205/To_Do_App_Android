package com.example.morp_prj.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// 1. Thêm @Entity để Room biết đây là một bảng
@Entity(tableName = "users")
data class User(
    // 2. Thêm @PrimaryKey để xác định khóa chính
    @PrimaryKey
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
    val updatedAt: String? = null,

    @SerializedName("githubUrl")
    val githubUrl: String? = null,

    @SerializedName("linkedinUrl")
    val linkedinUrl: String? = null,

    @SerializedName("websiteUrl")
    val websiteUrl: String? = null
)
