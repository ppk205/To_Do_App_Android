package com.example.morp_prj

import android.app.Application
import android.content.Intent
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.notifications.NotificationChannels
import com.example.morp_prj.notifications.WorkScheduler
import com.example.morp_prj.security.SecurityChecker
import com.example.morp_prj.security.SecureTokenStorage
import com.example.morp_prj.utils.PreferenceManager
import kotlin.system.exitProcess

class MyApplication : Application() {

    companion object {
        const val ACTION_SESSION_EXPIRED = "com.example.morp_prj.ACTION_SESSION_EXPIRED"
    }

    override fun onCreate() {
        super.onCreate()

        // Enforce security check at startup
        val securityChecker = SecurityChecker(this)
        try {
            securityChecker.validateDeviceSecurity()
        } catch (e: SecurityException) {
            android.util.Log.e("Security", e.message ?: "Device compromised")
            // Optionally show a user dialog (not possible here in Application) and exit
            exitProcess(1)
        }

        // Wire AuthInterceptor into Retrofit client so that token refresh is automatic
        try {
            RetrofitClient.setAuthInterceptor(this) {
                // onRefreshFailed: clear tokens, prefs, and notify UI to navigate to login
                try {
                    val tokenStorage = SecureTokenStorage(this)
                    tokenStorage.clearTokens()
                } catch (t: Throwable) {
                    android.util.Log.w("MyApplication", "Failed clearing secure tokens", t)
                }

                try {
                    val prefs = PreferenceManager(this)
                    prefs.clearLoginData()
                } catch (t: Throwable) {
                    android.util.Log.w("MyApplication", "Failed clearing prefs", t)
                }

                // Broadcast session expired so UI can react and navigate to login
                try {
                    val intent = Intent(ACTION_SESSION_EXPIRED)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                    android.util.Log.w("MyApplication", "Broadcasted session expired")
                } catch (t: Throwable) {
                    android.util.Log.w("MyApplication", "Failed to broadcast session expired", t)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MyApplication", "Failed to set AuthInterceptor", e)
        }

        // Notifications: ensure channels + schedule periodic reminders
        try {
            NotificationChannels.ensureCreated(this)
            WorkScheduler.scheduleTaskDueSoon(this)
        } catch (t: Throwable) {
            android.util.Log.w("MyApplication", "Failed to init notifications", t)
        }
    }
}
