package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

/**
 * Used for sync-down.
 */
data class TaskDto(
    @SerializedName("id")
    val id: String,

    @SerializedName("userId")
    val userId: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("deadlineAt")
    val deadlineAt: Long? = null,

    @SerializedName("priority")
    val priority: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("tagsCsv")
    val tagsCsv: String? = null,

    @SerializedName("createdAt")
    val createdAt: Long? = null,

    @SerializedName("updatedAt")
    val updatedAt: Long? = null,
)

data class TaskListResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("tasks")
    val tasks: List<TaskDto> = emptyList(),
)

