package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApiService {

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

    @POST("api/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<AuthResponse>

    @GET("api/auth/sessions")
    suspend fun getSessions(): Response<SessionsResponse>

    @POST("api/auth/sessions/revoke")
    suspend fun revokeSession(@Body request: RevokeSessionRequest): Response<AuthResponse>
}
