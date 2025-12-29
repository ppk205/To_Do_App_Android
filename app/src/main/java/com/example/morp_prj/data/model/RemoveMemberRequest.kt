package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class RemoveMemberRequest(
    @SerializedName("teamId") val teamId: String,
    @SerializedName("userId") val userId: String
)