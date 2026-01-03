package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class CreateTeamRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("createdBy")
    val createdBy: String,
    @SerializedName("tags")
    var tags: List<String> = emptyList()
)