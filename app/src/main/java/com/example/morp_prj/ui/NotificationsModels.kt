package com.example.morp_prj.ui

import com.example.morp_prj.data.db.NotificationEntity

/**
 * Simple UI-only notification model for mock/demo.
 */
data class UiNotification(
    val id: Long,
    val title: String,
    val message: String,
    val time: String,
    val isNew: Boolean,
    val dedupeKey: String,
)

/**
 * Script dữ liệu theo đúng nội dung trong ảnh.
 */
object NotificationUiMapper {

    fun fromEntity(e: NotificationEntity): UiNotification {
        return UiNotification(
            id = e.id,
            title = e.title,
            message = e.message,
            time = RelativeTime.format(e.createdAt),
            isNew = e.isNew,
            dedupeKey = e.dedupeKey,
        )
    }
}

object RelativeTime {
    fun format(whenMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - whenMillis).coerceAtLeast(0L)
        val mins = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L

        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins minutes ago"
            hours < 24 -> "$hours hours ago"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> "${days / 7} weeks ago"
        }
    }
}
