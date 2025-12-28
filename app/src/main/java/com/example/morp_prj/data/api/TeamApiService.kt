package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.CreateTeamRequest
import com.example.morp_prj.data.model.JoinTeamRequest
import com.example.morp_prj.data.model.PinTeamRequest
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.data.model.TeamMember
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TeamApiService {
    @GET("team/user/{userId}")
    fun getMyTeams(@Path("userId") userId: String): Call<List<Team>>

    @GET("team/{teamId}/members")
    fun getTeamMembers(@Path("teamId") teamId: String): Call<List<TeamMember>>

    @POST("team/create")
    fun createTeam(@Body request: CreateTeamRequest): Call<Team>

    @POST("team/pin")
    fun togglePinTeam(@Body request: PinTeamRequest): Call<Void>

    @POST("team/join")
    fun joinTeam(@Body request: JoinTeamRequest): Call<Void>
}