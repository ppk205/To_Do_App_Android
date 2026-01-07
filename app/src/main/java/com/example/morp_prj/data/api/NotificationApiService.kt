package com.example.morp_prj.data.api

import com.example.morp_prj.data.model.NotificationListResponse
import com.example.morp_prj.data.model.MarkReadRequest
import com.example.morp_prj.data.model.MarkReadResponse
import com.example.morp_prj.data.model.DeleteNotificationRequest
import com.example.morp_prj.data.model.DeleteNotificationResponse
import com.example.morp_prj.data.model.ClearNotificationsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface NotificationApiService {

    @GET("api/notifications")
    suspend fun getNotifications(): Response<NotificationListResponse>

    @POST("api/notifications/mark-read")
    suspend fun markRead(@Body request: MarkReadRequest): Response<MarkReadResponse>

    @POST("api/notifications/delete")
    suspend fun deleteNotification(@Body request: DeleteNotificationRequest): Response<DeleteNotificationResponse>

    @POST("api/notifications/clear")
    suspend fun clearAll(): Response<ClearNotificationsResponse>
}
