package com.example.morp_prj.ui

data class ToDoItem(
    val id: Long,
    val title: String,
    val description: String,
    val timeLabel: String,
    val dueCategory: DueCategory,
    val deadlineAt: Long?,
    val priority: PriorityLevel,
    val tags: List<String>,
    val status: TaskStatus,
)
