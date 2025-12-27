package com.example.morp_prj.data.repository

import android.content.Context
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.data.db.SyncState
import com.example.morp_prj.data.model.TaskSyncItem
import com.example.morp_prj.data.model.TaskSyncRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskSyncRepository(context: Context) {

    private val taskDao = AppDatabase.getInstance(context).taskDao()

    /**
     * Upload local tasks needing sync to backend.
     * @return Result with number of tasks synced.
     */
    suspend fun syncUp(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val needSync = taskDao.getNeedSync()
            if (needSync.isEmpty()) return@withContext Result.success(0)

            val request = TaskSyncRequest(
                tasks = needSync.map {
                    TaskSyncItem(
                        localId = it.id,
                        serverId = it.serverId,
                        title = it.title,
                        description = it.description,
                        deadlineAt = it.deadlineAt,
                        priority = it.priority,
                        status = it.status,
                        tagsCsv = it.tagsCsv,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                }
            )

            val response = RetrofitClient.taskApiService.syncTasks(request)
            if (!response.isSuccessful || response.body()?.success != true) {
                // Mark as ERROR so user can retry
                taskDao.markSyncState(needSync.map { it.id }, SyncState.ERROR.name)
                return@withContext Result.failure(Exception(response.body()?.message ?: response.message()))
            }

            val body = response.body()!!
            // Apply id mapping and mark as SYNCED
            body.idMap.forEach { map ->
                taskDao.markSynced(map.localId, map.serverId, SyncState.SYNCED.name)
            }

            Result.success(body.idMap.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

