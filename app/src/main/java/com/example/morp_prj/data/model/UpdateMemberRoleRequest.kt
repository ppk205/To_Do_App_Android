package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class UpdateMemberRoleRequest(
    @SerializedName("teamId") val teamId: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("newRole") val newRole: String
)

