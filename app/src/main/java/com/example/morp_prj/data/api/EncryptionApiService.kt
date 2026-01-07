package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * API service for encryption key management
 */
interface EncryptionApiService {

    /**
     * Initialize user encryption (first time setup)
     * Creates salt for user's password-based key derivation
     */
    @POST("encryption/initialize")
    suspend fun initializeUserEncryption(
        @Body request: InitializeEncryptionRequest
    ): Response<UserEncryptionInfo>

    /**
     * Get user's salt for password-based key derivation
     */
    @GET("encryption/salt/{userId}")
    suspend fun getUserSalt(
        @Path("userId") userId: String
    ): Response<UserEncryptionInfo>

    /**
     * Create or update team encryption key
     * Manager/Co-manager only
     */
    @POST("encryption/team-key")
    suspend fun setTeamKey(
        @Body request: SetTeamKeyRequest
    ): Response<TeamKeyResponse>

    /**
     * Get team encryption key (encrypted with user's password-derived key)
     */
    @POST("encryption/team-key/get")
    suspend fun getTeamKey(
        @Body request: GetTeamKeyRequest
    ): Response<TeamKeyResponse>

    /**
     * Get all team keys for user (when syncing to new device)
     */
    @POST("encryption/team-keys/sync")
    suspend fun syncTeamKeys(
        @Body request: Map<String, String> // { "passwordDerivedKey": "..." }
    ): Response<Map<String, String>> // { "teamId": "encryptedKey", ... }

    /**
     * Rotate team encryption key (manager only)
     * Re-encrypts all team data with new key
     */
    @POST("encryption/team-key/rotate")
    suspend fun rotateTeamKey(
        @Body request: Map<String, String> // { "teamId": "...", "newEncryptedKey": "..." }
    ): Response<TeamKeyResponse>
}

