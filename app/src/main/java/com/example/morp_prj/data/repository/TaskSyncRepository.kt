package com.example.morp_prj.data.repository

import android.content.Context
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.data.db.SyncState
import com.example.morp_prj.data.db.TaskEntity
import com.example.morp_prj.data.model.TaskListResponse
import com.example.morp_prj.data.model.TaskSyncItem
import com.example.morp_prj.data.model.TaskSyncRequest
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskSyncRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val taskDao = db.taskDao()
    private val deletedTaskDao = db.deletedTaskDao()
    private val prefs = PreferenceManager(context)

    /**
     * Upload local tasks needing sync to backend.
     * @return Result with number of tasks synced.
     */
    suspend fun syncUp(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!prefs.isLoggedIn() || prefs.isGuest()) {
                // Guests don't sync
                return@withContext Result.success(0)
            }
            val userId = prefs.getUserId() ?: return@withContext Result.success(0)

            val needSync = taskDao.getNeedSyncForUser(userId)
            val tombstones = deletedTaskDao.getAllForUser(userId)
            val deletedServerIds = tombstones.mapNotNull { it.serverId }.distinct()

            if (needSync.isEmpty() && deletedServerIds.isEmpty()) {
                return@withContext Result.success(0)
            }

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
                },
                deletedServerIds = deletedServerIds,
            )

            val response = RetrofitClient.taskApiService.syncTasks(request)
            if (!response.isSuccessful || response.body()?.success != true) {
                // Mark upserts as ERROR so user can retry
                if (needSync.isNotEmpty()) {
                    taskDao.markSyncState(needSync.map { it.id }, SyncState.ERROR.name)
                }
                return@withContext Result.failure(Exception(response.body()?.message ?: response.message()))
            }

            val body = response.body()!!

            // Apply id mapping and mark as SYNCED
            body.idMap.forEach { map ->
                taskDao.markSynced(map.localId, map.serverId, SyncState.SYNCED.name)
            }

            // Clear tombstones if server accepted
            if (deletedServerIds.isNotEmpty()) {
                deletedTaskDao.clearForUser(userId)
            }

            Result.success(body.idMap.size + deletedServerIds.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download tasks from server (current logged-in user) and merge into local DB.
     * @return Result with number of tasks inserted/updated.
     */
    suspend fun syncDown(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!prefs.isLoggedIn() || prefs.isGuest()) return@withContext Result.success(0)
            val userId = prefs.getUserId() ?: return@withContext Result.success(0)

            val response = RetrofitClient.taskApiService.getTasks()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception(response.message()))
            }
            val body: TaskListResponse = response.body() ?: return@withContext Result.failure(Exception("Empty response"))
            if (body.success != true) {
                return@withContext Result.failure(Exception(body.message ?: "Sync down failed"))
            }

            val serverTasks = body.tasks ?: emptyList()
            var changed = 0

            for (t in serverTasks) {
                val serverId = t.id ?: continue

                val existing = taskDao.getByServerId(serverId)

                val entity = if (existing != null) {
                    // Merge rule: keep the newest by updatedAt (server wins if newer).
                    val serverUpdatedAt = t.updatedAt ?: 0L
                    val localUpdatedAt = existing.updatedAt

                    if (serverUpdatedAt >= localUpdatedAt) {
                        existing.copy(
                            userId = userId,
                            isFromServer = true,
                            syncState = SyncState.SYNCED,
                            title = t.title ?: existing.title,
                            description = t.description ?: "",
                            deadlineAt = t.deadlineAt,
                            priority = t.priority ?: existing.priority,
                            status = t.status ?: existing.status,
                            tagsCsv = t.tagsCsv ?: "",
                            createdAt = t.createdAt ?: existing.createdAt,
                            updatedAt = serverUpdatedAt
                        )
                    } else {
                        // Local is newer: keep local, but ensure it stays linked to serverId
                        existing.copy(
                            userId = userId,
                            isFromServer = existing.isFromServer || true
                        )
                    }
                } else {
                    TaskEntity(
                        // id auto-generate
                        serverId = serverId,
                        userId = userId,
                        isFromServer = true,
                        syncState = SyncState.SYNCED,
                        title = t.title ?: "",
                        description = t.description ?: "",
                        deadlineAt = t.deadlineAt,
                        priority = t.priority ?: "MEDIUM",
                        status = t.status ?: "TODO",
                        tagsCsv = t.tagsCsv ?: "",
                        createdAt = t.createdAt ?: System.currentTimeMillis(),
                        updatedAt = t.updatedAt ?: System.currentTimeMillis(),
                    )
                }

                // Persist (REPLACE). If existing -> keeps same local id.
                taskDao.insert(entity)
                changed++
            }

            Result.success(changed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Convenience: sync up then down. */
    suspend fun syncAll(): Result<Pair<Int, Int>> {
        val up = syncUp().getOrElse { return Result.failure(it) }
        val down = syncDown().getOrElse { return Result.failure(it) }
        return Result.success(up to down)
    }
}
