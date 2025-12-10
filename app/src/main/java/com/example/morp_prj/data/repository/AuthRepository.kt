package com.example.morp_prj.data.repository

import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.AuthResponse
import com.example.morp_prj.data.model.LoginRequest
import com.example.morp_prj.data.model.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    private val apiService = RetrofitClient.authApiService

    suspend fun register(
        username: String,
        password: String,
        displayName: String,
        email: String,
        phone: String? = null
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = RegisterRequest(
                username = username,
                password = password,
                displayName = displayName,
                email = email,
                phone = phone
            )

            val response = apiService.register(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.message() ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(
        usernameOrEmail: String,
        password: String
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = LoginRequest(
                usernameOrEmail = usernameOrEmail,
                password = password
            )

            val response = apiService.login(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.message() ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

