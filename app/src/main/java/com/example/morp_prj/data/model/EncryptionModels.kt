package com.example.morp_prj.data.model

/**
 * User's encryption salt stored on server
 */
data class UserEncryptionInfo(
    val userId: String,
    val salt: String, // Base64 encoded salt for PBKDF2
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Team encryption key stored on server (encrypted with user's password-derived key)
 */
data class TeamEncryptionKey(
    val teamId: String,
    val encryptedKey: String, // Team key encrypted with password-derived key
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Request to initialize user encryption on first login
 */
data class InitializeEncryptionRequest(
    val userId: String,
    val salt: String
)

/**
 * Request to get team encryption key
 */
data class GetTeamKeyRequest(
    val teamId: String,
    val passwordDerivedKey: String // Key derived from password on client
)

/**
 * Request to create/update team encryption key
 */
data class SetTeamKeyRequest(
    val teamId: String,
    val encryptedKey: String, // Team key encrypted with user's password-derived key
    val passwordDerivedKey: String // For verification
)

/**
 * Response containing team key
 */
data class TeamKeyResponse(
    val teamId: String,
    val encryptedKey: String,
    val success: Boolean = true
)

