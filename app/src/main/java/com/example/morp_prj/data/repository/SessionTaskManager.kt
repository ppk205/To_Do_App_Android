@file:Suppress("unused")
package com.example.morp_prj.data.repository

import android.content.Context
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applies task persistence rules around login/logout.
 *
 * Contract:
 * - Guest/local tasks are stored under userId = PreferenceManager.GUEST_USER_ID
 * - Real-user tasks are stored under userId = real user id
 * - On logout: delete only server-cached tasks (isFromServer=1) of that real user.
 *   Keep guest/local tasks and also keep any unsynced offline tasks that user created locally.
 */
class SessionTaskManager(@Suppress("UNUSED_PARAMETER") private val context: Context) {

    private val prefs = PreferenceManager(context)
    private val taskDao = AppDatabase.getInstance(context).taskDao()

    suspend fun onLoginSuccess(userId: String) = withContext(Dispatchers.IO) {
        // If user logs in again, clear previous server cache for that user, then UI can sync-down.
        // We keep any offline tasks the user created locally (isFromServer=0).
        taskDao.clearServerCacheForUser(userId)
    }

    suspend fun onLogout() = withContext(Dispatchers.IO) {
        val userId = prefs.getUserId() ?: return@withContext
        if (userId == PreferenceManager.GUEST_USER_ID) return@withContext

        // Delete only server-cached tasks for this user.
        taskDao.deleteServerTasksForUser(userId)
    }

    /**
     * Migrate all guest tasks to the real user id and mark them pending for sync-up.
     * Returns the number of rows updated. Errors are swallowed with a warning log.
     */
    suspend fun migrateGuestData(realUserId: String): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val count = taskDao.migrateGuestTasks(newUserId = realUserId)
            android.util.Log.i("SessionTaskManager", "migrateGuestData migrated $count tasks to $realUserId")
            count
        } catch (t: Throwable) {
            android.util.Log.w("SessionTaskManager", "migrateGuestData failed", t)
            0
        }
    }
}
