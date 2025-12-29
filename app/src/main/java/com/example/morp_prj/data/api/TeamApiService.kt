package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.CreateTeamRequest
import com.example.morp_prj.data.model.HandleJoinRequest
import com.example.morp_prj.data.model.JoinTeamRequest
import com.example.morp_prj.data.model.PinTeamRequest
import com.example.morp_prj.data.model.RemoveMemberRequest
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.data.model.TeamMember
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TeamApiService {
    @GET("api/team/user/{userId}")
    fun getMyTeams(@Path("userId") userId: String): Call<List<Team>>

    @GET("api/team/{teamId}/members")
    fun getTeamMembers(
        @Path("teamId") teamId: String,
        @Query("status") status: String = "active"
    ): Call<List<TeamMember>>

    @POST("api/team/create")
    fun createTeam(@Body request: CreateTeamRequest): Call<Team>

    @POST("api/team/pin")
    fun togglePinTeam(@Body request: PinTeamRequest): Call<Void>

    @POST("api/team/join")
    fun joinTeam(@Body request: JoinTeamRequest): Call<Void>

    @POST("api/team/handle-request")
    fun handleJoinRequest(@Body request: HandleJoinRequest): Call<Void>

    @POST("api/team/remove")
    fun removeMember(@Body request: RemoveMemberRequest): Call<Void>
}