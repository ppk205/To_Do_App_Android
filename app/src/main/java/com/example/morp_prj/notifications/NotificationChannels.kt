package com.example.morp_prj.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    const val CHANNEL_TASKS = "tasks"
    const val CHANNEL_TEAMS = "teams"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val taskChannel = NotificationChannel(
            CHANNEL_TASKS,
            "Task reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for tasks due soon"
        }

        val teamChannel = NotificationChannel(
            CHANNEL_TEAMS,
            "Team updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications related to teams"
        }

        nm.createNotificationChannel(taskChannel)
        nm.createNotificationChannel(teamChannel)
    }
}
