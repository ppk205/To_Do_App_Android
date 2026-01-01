package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class CreateTeamTaskRequest(
    @SerializedName("teamId")
    val teamId: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("dueDate")
    val dueDate: Long?,
    @SerializedName("priority")
    val priority: String,
    @SerializedName("assignees")
    val assignees: List<String>,
    @SerializedName("tagsCsv")
    val tagsCsv: String?,
    @SerializedName("createdBy")
    val createdBy: String
)