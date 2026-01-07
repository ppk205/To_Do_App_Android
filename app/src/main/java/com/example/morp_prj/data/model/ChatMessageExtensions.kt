package com.example.morp_prj.data.model

import com.example.morp_prj.security.CryptoManager

/**
 * Extension functions for encrypting/decrypting ChatMessage
 */

/**
 * Encrypt sensitive fields in ChatMessage using team key
 * @param teamKey Base64 encoded team encryption key
 */
fun ChatMessage.encrypt(teamKey: String): ChatMessage {
    return this.copy(
        content = CryptoManager.encryptWithTeamKey(content, teamKey) ?: content,
        attachmentUrl = CryptoManager.encryptWithTeamKey(attachmentUrl, teamKey)
    )
}

/**
 * Decrypt sensitive fields in ChatMessage using team key
 * @param teamKey Base64 encoded team encryption key
 */
fun ChatMessage.decrypt(teamKey: String): ChatMessage {
    return this.copy(
        content = CryptoManager.decryptWithTeamKey(content, teamKey) ?: content,
        attachmentUrl = CryptoManager.decryptWithTeamKey(attachmentUrl, teamKey)
    )
}

/**
 * Encrypt list of ChatMessages
 */
fun List<ChatMessage>.encrypt(teamKey: String): List<ChatMessage> = map { it.encrypt(teamKey) }

/**
 * Decrypt list of ChatMessages
 */
fun List<ChatMessage>.decrypt(teamKey: String): List<ChatMessage> = map { it.decrypt(teamKey) }

/**
 * Check if ChatMessage appears to be encrypted
 */
fun ChatMessage.isEncrypted(): Boolean {
    // Encrypted format is "IV:EncryptedData"
    return content.contains(":") && content.split(":").size == 2
}

