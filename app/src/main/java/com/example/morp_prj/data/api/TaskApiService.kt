package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.TaskListResponse
import com.example.morp_prj.data.model.TaskSyncRequest
import com.example.morp_prj.data.model.TaskSyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface TaskApiService {

    @POST("api/tasks/sync")
    suspend fun syncTasks(@Body request: TaskSyncRequest): Response<TaskSyncResponse>

    @GET("api/tasks")
    suspend fun getTasks(): Response<TaskListResponse>
}
