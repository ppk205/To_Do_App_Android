package com.example.morp_prj.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationEntity): Long

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun pageByUser(userId: String, limit: Int, offset: Int): List<NotificationEntity>

    @Query("UPDATE notifications SET isNew = 0 WHERE userId = :userId")
    suspend fun markAllRead(userId: String): Int

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun clearForUser(userId: String): Int
}

