package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class PinTeamRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("teamId") val teamId: String,
    @SerializedName("isPinned") val isPinned: Boolean
)