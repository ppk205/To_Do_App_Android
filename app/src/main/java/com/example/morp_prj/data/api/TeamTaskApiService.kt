package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.CreateTeamTaskRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface TeamTaskApiService {
    @POST("api/team-tasks")
    fun createTeamTask(@Body request: CreateTeamTaskRequest): Call<Void>
}