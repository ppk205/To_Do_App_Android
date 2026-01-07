package com.example.morp_prj.data.model

data class CalendarDate(
    val date: Long,
    val dayNumber: String,
    val dayOfWeek: String,
    var isSelected: Boolean = false
)