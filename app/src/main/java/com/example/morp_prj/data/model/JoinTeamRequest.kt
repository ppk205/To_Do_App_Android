package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class JoinTeamRequest(
    @SerializedName("userId")
    val userId: String,
    @SerializedName("inviteCode")
    val inviteCode: String
)