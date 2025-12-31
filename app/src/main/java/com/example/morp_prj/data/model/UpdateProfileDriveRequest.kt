package com.example.morp_prj.data.model

/**
 * Request model for updating profile with Google Drive avatar link
 */
data class UpdateProfileWithDriveLinkRequest(
    val displayName: String?,
    val phone: String?,
    val bio: String?,
    val avatarUrl: String?, // Google Drive shareable link
    val githubUrl: String?,
    val linkedinUrl: String?,
    val websiteUrl: String?
)

