package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

/**
 * Team chat message model
 */
data class ChatMessage(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("teamId")
    val teamId: String = "",

    @SerializedName("senderId")
    val senderId: String = "",

    @SerializedName("senderName")
    val senderName: String = "",

    @SerializedName("senderAvatar")
    val senderAvatar: String? = null,

    @SerializedName("content")
    val content: String = "",

    @SerializedName("type")
    val type: String = "text", // text, image, file

    @SerializedName("attachmentUrl")
    val attachmentUrl: String? = null,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("isEdited")
    val isEdited: Boolean = false,

    @SerializedName("replyToId")
    val replyToId: String? = null
)

