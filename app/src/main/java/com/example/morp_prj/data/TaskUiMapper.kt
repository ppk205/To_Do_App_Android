package com.example.morp_prj.data

import com.example.morp_prj.data.db.TaskEntity
import com.example.morp_prj.ui.DueCategory
import com.example.morp_prj.ui.PriorityLevel
import com.example.morp_prj.ui.TaskStatus
import com.example.morp_prj.ui.ToDoItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TaskUiMapper {

    fun TaskEntity.toUiItem(now: Long = System.currentTimeMillis()): ToDoItem {
        val priorityLevel = when (priority.uppercase(Locale.getDefault())) {
            "LOW" -> PriorityLevel.LOW
            "HIGH" -> PriorityLevel.HIGH
            else -> PriorityLevel.MEDIUM
        }

        val statusEnum = when (status.uppercase(Locale.getDefault())) {
            "IN_PROGRESS" -> TaskStatus.IN_PROGRESS
            "DONE" -> TaskStatus.DONE
            else -> TaskStatus.TODO
        }

        val dueCategory = deadlineAt?.let { categorizeDeadline(it, now) } ?: DueCategory.NONE
        val timeLabel = deadlineAt?.let { formatDeadlineLabel(it, now) } ?: "No deadline"

        return ToDoItem(
            id = id,
            title = title,
            description = description,
            timeLabel = timeLabel,
            dueCategory = dueCategory,
            deadlineAt = deadlineAt,
            priority = priorityLevel,
            tags = tags(),
            status = statusEnum,
        )
    }

    private fun categorizeDeadline(deadlineAt: Long, now: Long): DueCategory {
        val calNow = Calendar.getInstance().apply { timeInMillis = now }
        val calDeadline = Calendar.getInstance().apply { timeInMillis = deadlineAt }

        fun isSameDay(a: Calendar, b: Calendar): Boolean {
            return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        }

        if (isSameDay(calNow, calDeadline)) return DueCategory.TODAY

        val calTomorrow = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
        }
        if (isSameDay(calTomorrow, calDeadline)) return DueCategory.TOMORROW

        // This week (until end of week)
        val endOfWeek = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            add(Calendar.WEEK_OF_YEAR, 1)
            add(Calendar.MILLISECOND, -1)
        }

        return if (deadlineAt in (now + 1)..endOfWeek.timeInMillis) DueCategory.THIS_WEEK else DueCategory.NONE
    }

    private fun formatDeadlineLabel(deadlineAt: Long, now: Long): String {
        val prefix = when (categorizeDeadline(deadlineAt, now)) {
            DueCategory.TODAY -> "Today"
            DueCategory.TOMORROW -> "Tomorrow"
            DueCategory.THIS_WEEK, DueCategory.NONE ->
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(deadlineAt))
        }

        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(deadlineAt))
        return "$prefix, $time"
    }
}
