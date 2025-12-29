package com.example.morp_prj.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // =====================
    // Observe (UI)
    // =====================

    /** Observe all tasks for a specific userId. */
    @Query("SELECT * FROM tasks WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAllByUser(userId: String): Flow<List<TaskEntity>>

    /** Observe guest/local tasks only. */
    @Query("SELECT * FROM tasks WHERE userId = :guestUserId ORDER BY createdAt DESC")
    fun observeAllLocal(guestUserId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deadlineAt >= :startOfDay AND deadlineAt < :endOfDay ORDER BY deadlineAt ASC")
    fun observeByDateRange(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND deadlineAt >= :startOfDay AND deadlineAt < :endOfDay ORDER BY deadlineAt ASC")
    fun observeByDateRangeForUser(userId: String, startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND status = :status ORDER BY createdAt DESC")
    fun observeByStatusForUser(userId: String, status: String): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE userId = :userId AND status = :status")
    fun observeCountByStatusForUser(userId: String, status: String): Flow<Int>

    // =====================
    // CRUD
    // =====================

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM tasks WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String): Int

    @Query("DELETE FROM tasks")
    suspend fun deleteAll(): Int

    // =====================
    // Sync helpers
    // =====================

    @Query("SELECT * FROM tasks WHERE userId = :userId AND syncState != :syncedState ORDER BY updatedAt DESC")
    suspend fun getNeedSyncForUser(userId: String, syncedState: String = "SYNCED"): List<TaskEntity>

    @Query("UPDATE tasks SET serverId = :serverId, syncState = :syncState, updatedAt = :updatedAt WHERE id = :localId")
    suspend fun markSynced(
        localId: Long,
        serverId: String,
        syncState: String = "SYNCED",
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE tasks SET syncState = :syncState WHERE id IN (:localIds)")
    suspend fun markSyncState(localIds: List<Long>, syncState: String): Int

    /** Find a local row by serverId (for merge on sync-down). */
    @Query("SELECT * FROM tasks WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): TaskEntity?

    // =====================
    // Auth/session helpers
    // =====================

    /** Delete only tasks that came from server cache for this user (keep guest/local tasks). */
    @Query("DELETE FROM tasks WHERE userId = :userId AND isFromServer = 1")
    suspend fun deleteServerTasksForUser(userId: String): Int

    /** When a user logs in again, clear old server cache and re-sync down. */
    @Query("DELETE FROM tasks WHERE userId = :userId AND isFromServer = 1")
    suspend fun clearServerCacheForUser(userId: String): Int
}
