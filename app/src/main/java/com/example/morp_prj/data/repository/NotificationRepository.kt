package com.example.morp_prj.data.repository

import android.content.Context
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.data.db.NotificationEntity
import com.example.morp_prj.notifications.NotificationChannels
import com.example.morp_prj.utils.PreferenceManager

class NotificationRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.notificationDao()
    private val prefs = PreferenceManager(context)

    fun currentUserIdOrGuest(): String = prefs.getCurrentUserIdOrGuest()

    suspend fun addTaskNotification(
        userId: String,
        title: String,
        message: String,
        dedupeKey: String,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        dao.insert(
            NotificationEntity(
                userId = userId,
                channel = NotificationChannels.CHANNEL_TASKS,
                title = title,
                message = message,
                dedupeKey = dedupeKey,
                isNew = true,
                createdAt = createdAt,
            )
        )
    }

    suspend fun addTeamNotification(
        userId: String,
        title: String,
        message: String,
        dedupeKey: String,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        dao.insert(
            NotificationEntity(
                userId = userId,
                channel = NotificationChannels.CHANNEL_TEAMS,
                title = title,
                message = message,
                dedupeKey = dedupeKey,
                isNew = true,
                createdAt = createdAt,
            )
        )
    }

    suspend fun page(limit: Int, offset: Int, userId: String = currentUserIdOrGuest()): List<NotificationEntity> {
        return dao.pageByUser(userId, limit, offset)
    }

    suspend fun markAllRead(userId: String = currentUserIdOrGuest()) {
        dao.markAllRead(userId)
    }

    suspend fun clear(userId: String = currentUserIdOrGuest()) {
        dao.clearForUser(userId)
    }
}

