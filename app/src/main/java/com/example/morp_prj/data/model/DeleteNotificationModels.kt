package com.example.morp_prj.data.model

data class DeleteNotificationRequest(
    val dedupeKey: String,
)

data class DeleteNotificationResponse(
    val success: Boolean,
    val deleted: Int = 0,
)

