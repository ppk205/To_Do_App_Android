package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class TeamTask(
    @SerializedName("id")
    val id: String,

    @SerializedName("teamId")
    val teamId: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("dueDate")
    val dueDate: Long? = null,

    @SerializedName("priority")
    val priority: String = "MEDIUM",

    @SerializedName("status")
    val status: String = "TODO",

    @SerializedName("assignees")
    val assignees: List<User> = emptyList()
)