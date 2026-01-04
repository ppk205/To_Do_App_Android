package com.example.morp_prj.data.model

import com.google.gson.annotations.SerializedName

data class ClearNotificationsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("deleted") val deleted: Int? = null,
    @SerializedName("message") val message: String? = null,
)

