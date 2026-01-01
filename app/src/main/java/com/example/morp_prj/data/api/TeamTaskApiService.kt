package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.CreateTeamTaskRequest
import com.example.morp_prj.data.model.TeamTask
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.Date

interface TeamTaskApiService {
    @PATCH("api/team-tasks/{taskId}/status")
    fun updateTaskStatus(@Path("taskId") taskId: String, @Body body: Map<String, String>): Call<Void>

    @POST("api/team-tasks")
    fun createTeamTask(@Body request: CreateTeamTaskRequest): Call<Void>

    @GET("api/team-tasks/team/{teamId}")
    fun getTeamTasks(@Path("teamId") teamId: String): Call<List<TeamTask>>
}