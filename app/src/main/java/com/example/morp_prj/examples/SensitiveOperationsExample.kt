package com.example.morp_prj.examples

import androidx.fragment.app.FragmentActivity
import com.example.morp_prj.security.BiometricHelper
import com.example.morp_prj.security.SecureTokenStorage

/**
 * Example showing how to use BiometricHelper for sensitive operations
 */
class SensitiveOperationsExample(private val activity: FragmentActivity) {

    private val biometricHelper = BiometricHelper(activity)
    private val tokenStorage = SecureTokenStorage(activity)

    fun transferMoney(amount: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        biometricHelper.getAccessTokenWithBiometric(
            tokenStorage = tokenStorage,
            onSuccess = { token ->
                if (token != null) {
                    // Proceed with transfer using token
                    // performTransfer(token, amount)
                    onSuccess()
                } else {
                    onError("No access token available")
                }
            },
            onError = { err ->
                onError(err)
            }
        )
    }
}

