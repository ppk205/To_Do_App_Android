package com.example.morp_prj.data.repository

import android.content.Context
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.data.db.DeletedTaskEntity
import com.example.morp_prj.utils.PreferenceManager

/**
 * Single place to mutate tasks so we can record tombstones for deletions.
 */
class TaskMutationRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val taskDao = db.taskDao()
    private val deletedDao = db.deletedTaskDao()
    private val prefs = PreferenceManager(context)

    suspend fun deleteTask(localId: Long) {
        val userId = prefs.getCurrentUserIdOrGuest()

        // Snapshot serverId before delete
        val entity = taskDao.getById(localId)

        // Delete local row
        taskDao.deleteById(localId)

        // If logged in real user and task already has serverId -> add tombstone
        if (prefs.isLoggedIn() && !prefs.isGuest() && entity?.serverId != null) {
            deletedDao.insert(
                DeletedTaskEntity(
                    userId = userId,
                    serverId = entity.serverId,
                    localTaskId = localId,
                )
            )
        }
    }

    suspend fun deleteTasks(localIds: List<Long>) {
        localIds.forEach { deleteTask(it) }
    }
}

