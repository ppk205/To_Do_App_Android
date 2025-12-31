package com.example.morp_prj.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    fun scheduleTaskDueSoon(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // Periodic work: Android minimum is 15 minutes
        val req = PeriodicWorkRequestBuilder<TaskDueSoonWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                TaskDueSoonWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
    }
}

