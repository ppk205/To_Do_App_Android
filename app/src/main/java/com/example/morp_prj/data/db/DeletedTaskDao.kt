package com.example.morp_prj.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedTaskDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DeletedTaskEntity): Long

    @Query("SELECT * FROM deleted_tasks WHERE userId = :userId ORDER BY deletedAt ASC")
    suspend fun getAllForUser(userId: String): List<DeletedTaskEntity>

    @Query("DELETE FROM deleted_tasks WHERE userId = :userId")
    suspend fun clearForUser(userId: String): Int
}

