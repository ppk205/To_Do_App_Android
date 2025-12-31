package com.example.morp_prj.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.morp_prj.R
import com.example.morp_prj.data.repository.NotificationRepository
import com.example.morp_prj.utils.PreferenceManager

/**
 * Demo: create a few team-related notifications like screenshot.
 * Call this after login or from debug button if needed.
 */
object TeamMockNotificationScript {

    fun pushDemo(context: Context) {
        NotificationChannels.ensureCreated(context)

        val prefs = PreferenceManager(context)
        val userId = prefs.getCurrentUserIdOrGuest()
        val repo = NotificationRepository(context)

        val intent = Intent(context, com.example.morp_prj.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pi = PendingIntent.getActivity(
            context,
            3000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val payloads = listOf(
            "Team Synergy" to "New member, David Chen, joined your team",
            "Project Alpha" to "New files uploaded to shared folder",
            "Team Lead" to "You have been added to Team \"Growth Initiative\".",
        )

        payloads.forEachIndexed { idx, (title, msg) ->
            // Save to DB
            val key = "team:${title}:${msg}".take(120)
            try {
                kotlinx.coroutines.runBlocking {
                    repo.addTeamNotification(
                        userId = userId,
                        title = title,
                        message = msg,
                        dedupeKey = key,
                    )
                }
            } catch (_: Throwable) {
            }

            val n = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_TEAMS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(msg)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()

            NotificationManagerCompat.from(context).notify(9100 + idx, n)
        }
    }
}
