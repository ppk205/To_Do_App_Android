package com.example.morp_prj.data.model

import com.example.morp_prj.security.CryptoManager

/**
 * Extension functions for encrypting/decrypting TeamTask
 */

/**
 * Encrypt sensitive fields in TeamTask using team key
 * @param teamKey Base64 encoded team encryption key
 */
fun TeamTask.encrypt(teamKey: String): TeamTask {
    return this.copy(
        title = CryptoManager.encryptWithTeamKey(title, teamKey) ?: title,
        description = CryptoManager.encryptWithTeamKey(description, teamKey),
        tags = CryptoManager.encryptList(tags, teamKey)
    )
}

/**
 * Decrypt sensitive fields in TeamTask using team key
 * @param teamKey Base64 encoded team encryption key
 */
fun TeamTask.decrypt(teamKey: String): TeamTask {
    return this.copy(
        title = CryptoManager.decryptWithTeamKey(title, teamKey) ?: title,
        description = CryptoManager.decryptWithTeamKey(description, teamKey),
        tags = CryptoManager.decryptList(tags, teamKey)
    )
}

/**
 * Encrypt list of TeamTasks
 */
fun List<TeamTask>.encrypt(teamKey: String): List<TeamTask> = map { it.encrypt(teamKey) }

/**
 * Decrypt list of TeamTasks
 */
fun List<TeamTask>.decrypt(teamKey: String): List<TeamTask> = map { it.decrypt(teamKey) }

/**
 * Check if TeamTask appears to be encrypted
 */
fun TeamTask.isEncrypted(): Boolean {
    // Encrypted format is "IV:EncryptedData"
    return title.contains(":") && title.split(":").size == 2
}

