package com.example.morp_prj.data.repository

import android.content.Context
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.data.db.NotificationEntity
import com.example.morp_prj.notifications.NotificationChannels
import com.example.morp_prj.utils.PreferenceManager

class NotificationRepository(context: Context) {

    private val appContext: Context = context.applicationContext

    private val db = AppDatabase.getInstance(appContext)
    private val dao = db.notificationDao()
    private val prefs = PreferenceManager(appContext)

    fun currentUserIdOrGuest(): String = prefs.getCurrentUserIdOrGuest()

    private fun isGuest(): Boolean = prefs.isGuest()

    suspend fun syncFromServer(showDeviceNotifications: Boolean = true) {
        val userId = currentUserIdOrGuest()

        // Guest mode is local-only (like offline tasks). No backend sync.
        if (isGuest()) return

        val resp = try {
            RetrofitClient.notificationApiService.getNotifications()
        } catch (_: Throwable) {
            return
        }

        if (!resp.isSuccessful) return
        val body = resp.body() ?: return
        if (!body.success) return

        val existingKeys = try {
            dao.pageByUser(userId, limit = 200, offset = 0).map { it.dedupeKey }.toHashSet()
        } catch (_: Throwable) {
            hashSetOf<String>()
        }

        body.notifications.forEach { n ->
            val key = n.dedupeKey
            val isNewToDevice = !existingKeys.contains(key)

            try {
                dao.insert(
                    NotificationEntity(
                        userId = n.userId.ifBlank { userId },
                        channel = n.channel,
                        title = n.title,
                        message = n.message,
                        dedupeKey = key,
                        isNew = n.isNew,
                        createdAt = n.createdAt,
                    )
                )
            } catch (_: Throwable) {
            }

            if (showDeviceNotifications && isNewToDevice) {
                try {
                    val channelId = when (n.channel) {
                        "tasks" -> NotificationChannels.CHANNEL_TASKS
                        "teams" -> NotificationChannels.CHANNEL_TEAMS
                        else -> NotificationChannels.CHANNEL_TEAMS
                    }

                    val nid = (n.createdAt % Int.MAX_VALUE).toInt().coerceAtLeast(100)
                    com.example.morp_prj.notifications.DeviceNotificationHelper.show(
                        context = appContext,
                        channelId = channelId,
                        notificationId = nid,
                        title = n.title,
                        message = n.message,
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }

    suspend fun addTaskNotification(
        userId: String,
        title: String,
        message: String,
        dedupeKey: String,
        createdAt: Long = System.currentTimeMillis(),
        showDeviceNotification: Boolean = true,
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

        if (showDeviceNotification) {
            val nid = (createdAt % Int.MAX_VALUE).toInt().coerceAtLeast(100)
            com.example.morp_prj.notifications.DeviceNotificationHelper.show(
                context = appContext,
                channelId = NotificationChannels.CHANNEL_TASKS,
                notificationId = nid,
                title = title,
                message = message,
            )
        }
    }

    suspend fun addTeamNotification(
        userId: String,
        title: String,
        message: String,
        dedupeKey: String,
        createdAt: Long = System.currentTimeMillis(),
        showDeviceNotification: Boolean = true,
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

        if (showDeviceNotification) {
            val nid = (createdAt % Int.MAX_VALUE).toInt().coerceAtLeast(100)
            com.example.morp_prj.notifications.DeviceNotificationHelper.show(
                context = appContext,
                channelId = NotificationChannels.CHANNEL_TEAMS,
                notificationId = nid,
                title = title,
                message = message,
            )
        }
    }

    suspend fun page(limit: Int, offset: Int, userId: String = currentUserIdOrGuest()): List<NotificationEntity> {
        return dao.pageByUser(userId, limit, offset)
    }

    suspend fun markAllRead(userId: String = currentUserIdOrGuest()) {
        dao.markAllRead(userId)

        // In guest mode we don't have backend state.
        if (isGuest()) return

        // Optional: if later we store backend ids, we can call mark-read here.
    }

    suspend fun clear(userId: String = currentUserIdOrGuest()) {
        dao.clearForUser(userId)
    }

    suspend fun deleteOne(id: Long, dedupeKey: String, userId: String = currentUserIdOrGuest()): Int {
        // Guest mode is local-only.
        if (!isGuest()) {
            // Best-effort server delete (ignore failures; local delete still happens)
            try {
                RetrofitClient.notificationApiService.deleteNotification(
                    com.example.morp_prj.data.model.DeleteNotificationRequest(dedupeKey = dedupeKey)
                )
            } catch (_: Throwable) {
            }
        }

        return dao.deleteById(userId, id)
    }
}
