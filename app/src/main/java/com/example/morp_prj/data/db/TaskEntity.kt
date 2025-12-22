package com.example.morp_prj.data.db

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Room entity backing the local SQLite table `tasks`.
 *
 * Notes:
 * - We store enums (priority/status) as Strings to keep schema simple.
 * - We store tags as a CSV string (tagsCsv) to avoid an additional join table for now.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val title: String,
    val description: String = "",

    /** Epoch millis; null means no deadline. */
    val deadlineAt: Long? = null,

    /** "LOW" | "MEDIUM" | "HIGH" (stored as String for simplicity). */
    val priority: String = "MEDIUM",

    /** "TODO" | "IN_PROGRESS" | "DONE" (stored as String for simplicity). */
    val status: String = "TODO",

    /** Comma-separated tags. Example: "Work,Backend" */
    val tagsCsv: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {

    /** Parsed tags list from [tagsCsv]. */
    @Ignore
    fun tags(): List<String> {
        if (tagsCsv.isBlank()) return emptyList()
        return tagsCsv
            .split(',')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}
