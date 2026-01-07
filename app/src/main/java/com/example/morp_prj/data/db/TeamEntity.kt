package com.example.morp_prj.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey
    val id: String, // ID từ MySQL

    val name: String,
    val avatarUrl: String? = null,
    val description: String? = null,
    val createdBy: String,
    val createdAt: String? = null,
    val inviteCode: String? = null,
    
    // Cờ đánh dấu team này có được Pin không
    val isPinned: Boolean = false,
    
    // Timestamp cập nhật cache
    val lastSyncedAt: Long = System.currentTimeMillis()
)