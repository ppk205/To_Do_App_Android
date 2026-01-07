package com.example.morp_prj.data.repository

import android.content.Context
import android.util.Log
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.*
import com.example.morp_prj.security.CryptoManager
import com.example.morp_prj.security.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing encryption keys with server synchronization
 * Supports multi-device key syncing via password-based encryption
 */
class EncryptionRepository(private val context: Context) {

    private val TAG = "EncryptionRepository"
    private val encryptionApi = RetrofitClient.encryptionApiService
    private val keyManager = KeyManager(context)

    /**
     * Initialize user encryption on first login
     * Generates and uploads salt for password-based key derivation
     */
    suspend fun initializeUserEncryption(userId: String): Result<UserEncryptionInfo> {
        return withContext(Dispatchers.IO) {
            try {
                // Generate salt for this user
                val salt = CryptoManager.generateSalt()

                // Send to server
                val request = InitializeEncryptionRequest(userId, salt)
                val response = encryptionApi.initializeUserEncryption(request)

                if (response.isSuccessful && response.body() != null) {
                    val userInfo = response.body()!!

                    // Store salt locally
                    keyManager.storeUserSalt(userId, salt)

                    Log.d(TAG, "✅ User encryption initialized for $userId")
                    Result.success(userInfo)
                } else {
                    Log.e(TAG, "Failed to initialize encryption: ${response.code()}")
                    Result.failure(Exception("Server error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing encryption", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get user's salt from server (for new device login)
     */
    suspend fun getUserSalt(userId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = encryptionApi.getUserSalt(userId)

                if (response.isSuccessful && response.body() != null) {
                    val salt = response.body()!!.salt

                    // Store locally
                    keyManager.storeUserSalt(userId, salt)

                    Log.d(TAG, "✅ Retrieved salt for user $userId")
                    Result.success(salt)
                } else {
                    Log.e(TAG, "Failed to get salt: ${response.code()}")
                    Result.failure(Exception("Server error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting salt", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Create and upload team encryption key (for team creator)
     *
     * @param teamId Team identifier
     * @param userId Creator's user ID
     * @param password User's password for encrypting the key
     */
    suspend fun createTeamKey(
        teamId: String,
        userId: String,
        password: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Generate random team key
                val teamKey = CryptoManager.generateTeamKey()
                Log.d(TAG, "🔑 Generated team key for $teamId")

                // 2. Get user's salt
                var salt = keyManager.getUserSalt(userId)
                if (salt == null) {
                    // Try to get from server
                    val saltResult = getUserSalt(userId)
                    if (saltResult.isFailure) {
                        // Initialize user encryption if not exists
                        val initResult = initializeUserEncryption(userId)
                        if (initResult.isFailure) {
                            return@withContext Result.failure(Exception("Failed to initialize encryption"))
                        }
                        salt = initResult.getOrNull()?.salt
                    } else {
                        salt = saltResult.getOrNull()
                    }
                }

                if (salt == null) {
                    return@withContext Result.failure(Exception("Cannot get user salt"))
                }

                // 3. Derive key from password
                val passwordDerivedKey = CryptoManager.deriveKeyFromPassword(password, salt)
                Log.d(TAG, "🔐 Derived password key")

                // 4. Encrypt team key with password-derived key
                val encryptedTeamKey = CryptoManager.encryptTeamKeyForStorage(
                    teamKey,
                    passwordDerivedKey
                )
                Log.d(TAG, "🔒 Encrypted team key")

                // 5. Upload to server
                val request = SetTeamKeyRequest(teamId, encryptedTeamKey, passwordDerivedKey)
                val response = encryptionApi.setTeamKey(request)

                if (response.isSuccessful) {
                    // 6. Cache locally
                    keyManager.cacheTeamKey(teamId, teamKey)

                    Log.d(TAG, "✅ Team key created and uploaded for $teamId")
                    Result.success(teamKey)
                } else {
                    Log.e(TAG, "Failed to upload team key: ${response.code()}")
                    Result.failure(Exception("Server error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating team key", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get team encryption key from server and decrypt it
     * Used when user joins a team or logs in on new device
     *
     * @param teamId Team identifier
     * @param userId User ID
     * @param password User's password to decrypt the key
     */
    suspend fun getTeamKey(
        teamId: String,
        userId: String,
        password: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                val cachedKey = keyManager.getCachedTeamKey(teamId)
                if (cachedKey != null) {
                    Log.d(TAG, "✅ Using cached team key for $teamId")
                    return@withContext Result.success(cachedKey)
                }

                // 1. Get user's salt
                var salt = keyManager.getUserSalt(userId)
                if (salt == null) {
                    val saltResult = getUserSalt(userId)
                    if (saltResult.isFailure) {
                        return@withContext Result.failure(saltResult.exceptionOrNull()!!)
                    }
                    salt = saltResult.getOrNull()!!
                }

                // 2. Derive password key
                val passwordDerivedKey = CryptoManager.deriveKeyFromPassword(password, salt)

                // 3. Request encrypted team key from server
                val request = GetTeamKeyRequest(teamId, passwordDerivedKey)
                val response = encryptionApi.getTeamKey(request)

                if (response.isSuccessful && response.body() != null) {
                    val encryptedTeamKey = response.body()!!.encryptedKey

                    // 4. Decrypt team key
                    val teamKey = CryptoManager.decryptTeamKeyFromStorage(
                        encryptedTeamKey,
                        passwordDerivedKey
                    )

                    // 5. Cache locally
                    keyManager.cacheTeamKey(teamId, teamKey)

                    Log.d(TAG, "✅ Retrieved and decrypted team key for $teamId")
                    Result.success(teamKey)
                } else {
                    Log.e(TAG, "Failed to get team key: ${response.code()}")
                    Result.failure(Exception("Server error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting team key", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Sync all team keys when logging in on a new device
     * Downloads all encrypted keys and decrypts them with user's password
     *
     * @param userId User ID
     * @param password User's password
     */
    suspend fun syncAllTeamKeys(
        userId: String,
        password: String
    ): Result<Map<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get user's salt
                var salt = keyManager.getUserSalt(userId)
                if (salt == null) {
                    val saltResult = getUserSalt(userId)
                    if (saltResult.isFailure) {
                        return@withContext Result.failure(saltResult.exceptionOrNull()!!)
                    }
                    salt = saltResult.getOrNull()!!
                }

                // 2. Derive password key
                val passwordDerivedKey = CryptoManager.deriveKeyFromPassword(password, salt)

                // 3. Request all team keys from server
                val request = mapOf("passwordDerivedKey" to passwordDerivedKey)
                val response = encryptionApi.syncTeamKeys(request)

                if (response.isSuccessful && response.body() != null) {
                    val encryptedTeamKeys = response.body()!!
                    val decryptedKeys = mutableMapOf<String, String>()

                    // 4. Decrypt each team key
                    encryptedTeamKeys.forEach { (teamId, encryptedKey) ->
                        try {
                            val teamKey = CryptoManager.decryptTeamKeyFromStorage(
                                encryptedKey,
                                passwordDerivedKey
                            )

                            // Cache locally
                            keyManager.cacheTeamKey(teamId, teamKey)
                            decryptedKeys[teamId] = teamKey

                            Log.d(TAG, "✅ Synced key for team $teamId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to decrypt key for team $teamId", e)
                        }
                    }

                    Log.d(TAG, "✅ Synced ${decryptedKeys.size} team keys")
                    Result.success(decryptedKeys)
                } else {
                    Log.e(TAG, "Failed to sync keys: ${response.code()}")
                    Result.failure(Exception("Server error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing team keys", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get or create team key with fallback to deterministic generation
     * This ensures encryption always works even if server sync fails
     */
    suspend fun getOrCreateTeamKey(
        teamId: String,
        userId: String,
        password: String?
    ): String {
        return withContext(Dispatchers.IO) {
            // 1. Check cache first
            var teamKey = keyManager.getCachedTeamKey(teamId)
            if (teamKey != null) {
                return@withContext teamKey
            }

            // 2. Try to get from server if password available
            if (password != null) {
                val result = getTeamKey(teamId, userId, password)
                if (result.isSuccess) {
                    return@withContext result.getOrNull()!!
                }
                Log.w(TAG, "Failed to get key from server, using fallback")
            }

            // 3. Fallback: Generate deterministic key
            Log.w(TAG, "⚠️ Using deterministic key generation for $teamId")
            teamKey = CryptoManager.generateDeterministicTeamKey(teamId)
            keyManager.cacheTeamKey(teamId, teamKey)

            teamKey
        }
    }
}

