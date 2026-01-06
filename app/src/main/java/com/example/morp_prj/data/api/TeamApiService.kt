package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.*
import retrofit2.Call
import retrofit2.http.*

interface TeamApiService {
    @GET("api/team/user/{userId}")
    fun getMyTeams(@Path("userId") userId: String): Call<List<Team>>

    @GET("api/team/{teamId}/members")
    fun getTeamMembers(
        @Path("teamId") teamId: String,
        @Query("status") status: String = "active"
    ): Call<List<TeamMember>>

    @GET("api/team/{teamId}/messages")
    fun getTeamMessages(@Path("teamId") teamId: String): Call<List<ChatMessage>>

    @POST("api/team/create")
    fun createTeam(@Body request: CreateTeamRequest): Call<Team>

    @POST("api/team/pin")
    fun togglePinTeam(@Body request: PinTeamRequest): Call<Void>

    @POST("api/team/join")
    fun joinTeam(@Body request: JoinTeamRequest): Call<Void>

    @POST("api/team/request")
    fun handleJoinRequest(@Body request: HandleJoinRequest): Call<Void>

    @POST("api/team/remove-member")
    fun removeMember(@Body request: RemoveMemberRequest): Call<Void>

    @GET("api/team/{teamId}")
    fun getTeamDetail(@Path("teamId") teamId: String): Call<Team>

    @PUT("api/team/{teamId}")
    fun updateTeam(
        @Path("teamId") teamId: String,
        @Body request: UpdateTeamRequest
    ): Call<Team>

    @PUT("api/team/{teamId}/invite-code")
    fun regenerateInviteCode(@Path("teamId") teamId: String): Call<Team>

    @DELETE("api/team/{teamId}")
    fun deleteTeam(@Path("teamId") teamId: String): Call<Void>

    @POST("api/team/update-member-role")
    fun updateMemberRole(@Body request: UpdateMemberRoleRequest): Call<Void>
}