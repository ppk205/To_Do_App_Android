package com.example.morp_prj.data.model

data class ChatMessage(
    val id: String,
    val teamId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val createdAt: String,
    val type: String = "text"
)