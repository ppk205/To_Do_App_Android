package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class HandleJoinRequest(
    @SerializedName("teamId") val teamId: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("action") val action: String // "approve" or "reject"
)