package com.example.morp_prj.data.model
data class UpdateTeamRequest(
    val name: String,
    val description: String?,
    val tags: List<String>?
)