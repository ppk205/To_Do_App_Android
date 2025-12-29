package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class TaskSyncRequest(
    @SerializedName("tasks")
    val tasks: List<TaskSyncItem>,

    @SerializedName("deletedServerIds")
    val deletedServerIds: List<String> = emptyList(),
)

data class TaskSyncItem(
    @SerializedName("localId")
    val localId: Long,

    @SerializedName("serverId")
    val serverId: String? = null,

    @SerializedName("title")
    val title: String,

    @SerializedName("description")
    val description: String = "",

    @SerializedName("deadlineAt")
    val deadlineAt: Long? = null,

    @SerializedName("priority")
    val priority: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("tagsCsv")
    val tagsCsv: String = "",

    @SerializedName("createdAt")
    val createdAt: Long,

    @SerializedName("updatedAt")
    val updatedAt: Long
)

data class TaskSyncResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("syncedAt")
    val syncedAt: Long? = null,

    @SerializedName("idMap")
    val idMap: List<TaskIdMap> = emptyList()
)

data class TaskIdMap(
    @SerializedName("localId")
    val localId: Long,

    @SerializedName("serverId")
    val serverId: String
)

