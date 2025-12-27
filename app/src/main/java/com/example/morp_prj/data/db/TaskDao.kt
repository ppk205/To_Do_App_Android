package com.example.morp_prj.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

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

    @Query("SELECT * FROM tasks WHERE syncState != :syncedState ORDER BY updatedAt DESC")
    suspend fun getNeedSync(syncedState: String = "SYNCED"): List<TaskEntity>

    @Query("UPDATE tasks SET serverId = :serverId, syncState = :syncState, updatedAt = :updatedAt WHERE id = :localId")
    suspend fun markSynced(localId: Long, serverId: String, syncState: String = "SYNCED", updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE tasks SET syncState = :syncState WHERE id IN (:localIds)")
    suspend fun markSyncState(localIds: List<Long>, syncState: String): Int
}
