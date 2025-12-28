package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.CreateTeamRequest
import com.example.morp_prj.data.model.Team
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TeamApiService {
    @GET("team/user/{userId}")
    fun getMyTeams(@Path("userId") userId: String): Call<List<Team>>

    @GET("team/pinned/{userId}")
    fun getPinnedTeams(@Path("userId") userId: String): Call<List<Team>>

    @POST("team/create")
    fun createTeam(@Body request: CreateTeamRequest): Call<Team>
}