package com.example.morp_prj.security

import android.content.Context
import android.os.Build
import java.io.File

/**
 * ========================================
 * SECURITY CHECKER - Device Security Validation
 * ========================================
 *
 * Checks for:
 * - Root detection
 * - Emulator detection
 * - Debug mode
 */
class SecurityChecker(private val context: Context) {

    /**
     * Check if device is secure (not rooted and not emulator)
     */
    fun isDeviceSecure(): Boolean {
        return !isRooted() && !isEmulator()
    }

    /**
     * Validate device security and throw SecurityException if not secure.
     * Skips enforcement in debug builds to allow development.
     */
    fun validateDeviceSecurity(allowInDebug: Boolean = true) {
        if (allowInDebug && isDebugMode()) return

        if (isRooted()) {
            throw SecurityException("Device appears to be rooted — refusing to run")
        }

        if (isEmulator()) {
            throw SecurityException("Running on emulator — refusing to run")
        }
    }

    /**
     * Check if device is rooted
     */
    fun isRooted(): Boolean {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
    }

    /**
     * Check for common root binaries
     */
    private fun checkRootMethod1(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    /**
     * Check for root via build tags
     */
    private fun checkRootMethod2(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    /**
     * Check for common root management apps
     */
    private fun checkRootMethod3(): Boolean {
        val packages = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk"
        )

        return packages.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Check if running on emulator
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    /**
     * Check if app is in debug mode
     */
    fun isDebugMode(): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Get security warning message
     */
    fun getSecurityWarning(): String {
        return when {
            isRooted() -> "Thiết bị đã root. Ứng dụng có thể không hoạt động đúng."
            isEmulator() -> "Đang chạy trên emulator."
            else -> ""
        }
    }
}
