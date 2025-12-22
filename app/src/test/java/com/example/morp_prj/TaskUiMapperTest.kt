package com.example.morp_prj

import com.example.morp_prj.data.TaskUiMapper
import com.example.morp_prj.data.db.TaskEntity
import com.example.morp_prj.ui.DueCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskUiMapperTest {

    @Test
    fun `deadline categorization today`() {
        val now = System.currentTimeMillis()
        val entity = TaskEntity(
            id = 1,
            title = "Test",
            deadlineAt = now + 60_000, // within today
            priority = "LOW",
            status = "TODO",
            tagsCsv = "Work",
            createdAt = now,
            updatedAt = now,
        )

        val item = with(TaskUiMapper) { entity.toUiItem(now = now) }
        assertEquals(DueCategory.TODAY, item.dueCategory)
    }
}

