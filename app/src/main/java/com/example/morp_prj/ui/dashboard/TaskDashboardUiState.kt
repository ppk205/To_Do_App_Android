package com.example.morp_prj.ui.dashboard

import com.example.morp_prj.data.db.TaskEntity

data class TaskDashboardUiState(
    val assignees: List<AssigneeItem> = emptyList(),
    val selectedAssigneeId: String? = null,
    val timeRange: TimeRange = TimeRange.DAY,
    val total: Int = 0,
    val todo: Int = 0,
    val done: Int = 0,
    val inProgress: Int = 0,
    val overdue: Int = 0,
    val recent: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class TimeRange { DAY, MONTH, YEAR }

data class AssigneeItem(val id: String, val name: String)
