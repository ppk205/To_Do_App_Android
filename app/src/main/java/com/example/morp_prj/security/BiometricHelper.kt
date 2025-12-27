package com.example.morp_prj.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * ========================================
 * BIOMETRIC HELPER - Biometric Authentication
 * ========================================
 *
 * Provides biometric authentication for sensitive operations
 * Uses AndroidX Biometric library for compatibility
 */
class BiometricHelper(private val activity: FragmentActivity) {

    /**
     * Check if biometric authentication is available
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Show biometric prompt for authentication
     */
    fun authenticate(
        title: String = "Xác thực sinh trắc học",
        subtitle: String = "Sử dụng sinh trắc học để tiếp tục",
        negativeButtonText: String = "Hủy",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorString: String) -> Unit,
        onFailed: () -> Unit
    ) {
        if (!isBiometricAvailable()) {
            onError(-1, "Thiết bị không hỗ trợ sinh trắc học")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Get access token with biometric authentication
     * Use this for sensitive operations like viewing profile, making payments, etc.
     */
    fun getAccessTokenWithBiometric(
        tokenStorage: SecureTokenStorage,
        onSuccess: (String?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isBiometricAvailable()) {
            // Fallback: directly get token without biometric
            onSuccess(tokenStorage.getAccessToken())
            return
        }

        authenticate(
            title = "Xác thực để tiếp tục",
            subtitle = "Sử dụng sinh trắc học để xác nhận",
            onSuccess = {
                val token = tokenStorage.getAccessToken()
                onSuccess(token)
            },
            onError = { _, errorString ->
                onError(errorString)
            },
            onFailed = {
                onError("Xác thực thất bại")
            }
        )
    }

    companion object {
        /**
         * Check if device supports biometric authentication
         */
        fun isDeviceSupported(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        }
    }
}

