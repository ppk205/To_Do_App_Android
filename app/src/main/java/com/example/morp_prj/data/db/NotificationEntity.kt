package com.example.morp_prj.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["userId", "dedupeKey"], unique = true),
        Index(value = ["createdAt"]),
    ],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Owner userId. Use guest id for local-only if needed. */
    val userId: String,

    /** tasks | teams */
    val channel: String,

    val title: String,
    val message: String,

    /** a stable key to prevent duplicates (ex: task:SERVER_ID or team:joined:xxx) */
    val dedupeKey: String,

    /** 1 = new/unread */
    val isNew: Boolean = true,

    /** epoch millis */
    val createdAt: Long = System.currentTimeMillis(),
)
