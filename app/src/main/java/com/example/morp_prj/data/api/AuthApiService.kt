package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface   AuthApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/verify-otp")
    suspend fun verifyOTP(@Body request: VerifyOTPRequest): Response<AuthResponse>

    @POST("api/auth/resend-otp")
    suspend fun resendOTP(@Body request: ResendOTPRequest): Response<AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<AuthResponse>

    @GET("api/auth/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<User>

    @GET("api/auth/user/{userId}")
    suspend fun getUserById(@Path("userId") userId: String): Response<User>

    @POST("api/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<AuthResponse>

    @GET("api/auth/sessions")
    suspend fun getSessions(): Response<SessionsResponse>

    @POST("api/auth/sessions/revoke")
    suspend fun revokeSession(@Body request: RevokeSessionRequest): Response<AuthResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: Map<String, String>): Response<AuthResponse>

    @POST("api/auth/verify-reset-otp")
    suspend fun verifyResetOTP(@Body request: Map<String, String>): Response<AuthResponse>

    @POST("api/auth/resend-reset-otp")
    suspend fun resendResetOTP(@Body request: Map<String, String>): Response<AuthResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: Map<String, String>): Response<AuthResponse>

    @PUT("api/auth/profile")
    suspend fun updateProfile(
        @Body request: UpdateProfileWithDriveLinkRequest
    ): Response<User>

    @POST("api/auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body request: Map<String, String>
    ): Response<AuthResponse>
}
