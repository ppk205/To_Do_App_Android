package com.example.morp_prj.data.model

/** Matches backend /api/notifications payload */
data class NotificationDto(
    val id: Long,
    val userId: String,
    val channel: String,
    val title: String,
    val message: String,
    val dedupeKey: String,
    val isNew: Boolean,
    val createdAt: Long,
)

data class NotificationListResponse(
    val success: Boolean,
    val notifications: List<NotificationDto> = emptyList(),
)

data class MarkReadRequest(
    val ids: List<Long>,
)

data class MarkReadResponse(
    val success: Boolean,
    val updated: Int = 0,
)

