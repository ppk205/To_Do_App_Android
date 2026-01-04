package com.example.morp_prj.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.morp_prj.data.remote.SocketManager
import org.json.JSONObject

/**
 * Listens to Socket.IO `notification` events and persists them into the local Room DB
 * via [NotificationRepository], optionally showing device notifications.
 *
 * This makes notifications realtime without Firebase.
 */
class NotificationRealtimeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val notificationRepository = NotificationRepository(appContext)
    private val socket = SocketManager.getSocket()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Call once after socket connected (e.g. on login/app start).
     */
    fun start(showDeviceNotifications: Boolean = true) {
        socket?.off("notification")
        socket?.on("notification") { args ->
            try {
                if (args.isEmpty()) return@on

                val json = when (val first = args[0]) {
                    is JSONObject -> first
                    else -> JSONObject(first.toString())
                }

                val userId = json.optString("userId")
                val channel = json.optString("channel").trim().lowercase()
                val title = json.optString("title")
                val message = json.optString("message")
                val dedupeKey = json.optString("dedupeKey")
                val createdAt = json.optLong("createdAt", System.currentTimeMillis())

                scope.launch {
                    val resolvedUserId = if (userId.isBlank()) notificationRepository.currentUserIdOrGuest() else userId

                    when (channel) {
                        "tasks" -> notificationRepository.addTaskNotification(
                            userId = resolvedUserId,
                            title = title,
                            message = message,
                            dedupeKey = dedupeKey,
                            createdAt = createdAt,
                            showDeviceNotification = showDeviceNotifications,
                        )

                        // default to teams
                        else -> notificationRepository.addTeamNotification(
                            userId = resolvedUserId,
                            title = title,
                            message = message,
                            dedupeKey = dedupeKey,
                            createdAt = createdAt,
                            showDeviceNotification = showDeviceNotifications,
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e("NotifRealtimeRepo", "Failed to handle realtime notification", t)
            }
        }

        // Optional: handle deletion/clearing to stay in sync across devices.
        socket?.off("notification:deleted")
        socket?.on("notification:deleted") { _ ->
            // For now, rely on periodic sync. Implement local delete by dedupeKey later.
        }

        socket?.off("notification:cleared")
        socket?.on("notification:cleared") { _ ->
            // For now, rely on periodic sync. Implement local clear later.
        }
    }
}
