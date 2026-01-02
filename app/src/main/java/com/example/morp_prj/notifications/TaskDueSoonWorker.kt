package com.example.morp_prj.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.morp_prj.R
import com.example.morp_prj.constants.AppFlags
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.data.repository.NotificationRepository
import com.example.morp_prj.utils.PreferenceManager

/**
 * Background worker that shows notifications for tasks due soon.
 *
 * Rules:
 * - Logged-in users always get reminders
 * - Guest users get reminders when [AppFlags.ENABLE_GUEST_TASK_REMINDERS] is true
 * - Due within next [WINDOW_MINUTES]
 * - Status != DONE
 *
 * Note: This is local-only notification. It doesn't require backend.
 */
class TaskDueSoonWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val prefs = PreferenceManager(applicationContext)
            if (prefs.isGuest() && !AppFlags.ENABLE_GUEST_TASK_REMINDERS) return Result.success()

            val userId = prefs.getCurrentUserIdOrGuest()

            val notifRepo = NotificationRepository(applicationContext)

            NotificationChannels.ensureCreated(applicationContext)

            // View permission on Android 13+
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return Result.success()
            }

            val now = System.currentTimeMillis()
            val windowEnd = now + WINDOW_MINUTES * 60_000L

            val dao = AppDatabase.getInstance(applicationContext).taskDao()
            // Query due soon tasks (deadline not null, in window). We filter by status in-memory to avoid schema updates.
            val dueSoon = dao.getDueSoonForUser(userId, now, windowEnd)
                .filter { it.status != "DONE" }

            if (dueSoon.isEmpty()) return Result.success()

            // Show up to 3 notifications to avoid spam
            dueSoon.take(3).forEachIndexed { idx, task ->
                val title = "Task due soon"
                val msg = "${task.title} is due soon"

                // Save to local notifications DB (dedupe by serverId if available, else local id)
                val key = "task_due:${task.serverId ?: "local_${task.id}"}"
                notifRepo.addTaskNotification(
                    userId = userId,
                    title = task.title,
                    message = "Due within 1 hour",
                    dedupeKey = key,
                )

                val intent = Intent(applicationContext, com.example.morp_prj.ui.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    2000 + idx,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_TASKS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(msg)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                NotificationManagerCompat.from(applicationContext)
                    .notify(9000 + idx, notification)
            }

            return Result.success()
        } catch (_: Throwable) {
            return Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "task_due_soon_worker"
        private const val WINDOW_MINUTES = 60L
    }
}
