package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.CreateTeamTaskRequest
import com.example.morp_prj.data.model.TeamTask
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TeamTaskApiService {
    @POST("api/team-tasks")
    fun createTeamTask(@Body request: CreateTeamTaskRequest): Call<Void>

    @GET("api/team-tasks/team/{teamId}")
    fun getTeamTasks(@Path("teamId") teamId: String): Call<List<TeamTask>>
}