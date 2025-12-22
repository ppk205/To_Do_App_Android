package com.example.morp_prj.ui

data class ToDoItem(
    val id: Long,
    val title: String,
    val timeLabel: String,
    val dueCategory: DueCategory,
    val priority: PriorityLevel,
    val tags: List<String>,
    var status: TaskStatus = TaskStatus.TODO
)
