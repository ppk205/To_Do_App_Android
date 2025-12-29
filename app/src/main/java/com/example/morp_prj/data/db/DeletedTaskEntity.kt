package com.example.morp_prj.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tombstone table to sync deletions to server.
 *
 * When a user deletes a task while logged in, we insert a row here (instead of losing info).
 * Next syncUp will push serverIds to backend, then we can clear tombstones.
 */
@Entity(tableName = "deleted_tasks")
data class DeletedTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val userId: String,

    /** Remote task id to delete on server. Null means task was never synced. */
    val serverId: String? = null,

    /** Local task id (for debugging/tracking). */
    val localTaskId: Long? = null,

    val deletedAt: Long = System.currentTimeMillis(),
)

