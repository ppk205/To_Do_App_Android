package com.example.morp_prj.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.ResendOTPRequest
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class OtpResendRequiredFragment : Fragment(R.layout.fragment_otp_resend_required) {

    private var userId: String? = null
    private var email: String? = null

    private lateinit var txtEmail: TextView
    private lateinit var txtCountdown: TextView
    private lateinit var btnResendOtp: MaterialButton
    private lateinit var btnCancel: TextView
    private var countDownTimer: CountDownTimer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Ẩn bottom navigation bar
        hideBottomNavigation()

        // Get arguments
        userId = arguments?.getString("userId")
        email = arguments?.getString("email")

        if (userId == null || email == null) {
            Toast.makeText(requireContext(), getString(R.string.missing_user_info), Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }

        initViews(view)
        setupListeners()
        startCooldown()
    }

    private fun initViews(view: View) {
        txtEmail = view.findViewById(R.id.txt_email)
        txtCountdown = view.findViewById(R.id.txt_countdown)
        btnResendOtp = view.findViewById(R.id.btn_resend_otp)
        btnCancel = view.findViewById(R.id.btn_cancel)

        txtEmail.text = email
    }

    private fun startCooldown() {
        btnResendOtp.isEnabled = false
        btnResendOtp.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.medium_gray))
        btnResendOtp.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                txtCountdown.text = getString(R.string.resend_countdown_format, seconds)
            }

            override fun onFinish() {
                txtCountdown.text = getString(R.string.resend_ready)
                btnResendOtp.isEnabled = true
                btnResendOtp.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_blue))
                btnResendOtp.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            }
        }.start()
    }

    private fun setupListeners() {
        btnResendOtp.setOnClickListener {
            resendOTP()
        }

        btnCancel.setOnClickListener {
            // Navigate back to register
            try {
                findNavController().navigate(R.id.action_otpResendRequired_to_register)
            } catch (e: Exception) {
                android.util.Log.e("OtpResendRequiredFragment", "Navigation error", e)
                findNavController().navigateUp()
            }
        }
    }

    private fun resendOTP() {
        btnResendOtp.isEnabled = false
        btnResendOtp.text = getString(R.string.verifying)
        txtCountdown.text = ""

        lifecycleScope.launch {
            try {
                val request = ResendOTPRequest(
                    userId = userId!!,
                    email = email!!,
                    purpose = "REGISTER"
                )

                val response = RetrofitClient.authApiService.resendOTP(request)

                btnResendOtp.isEnabled = true
                btnResendOtp.text = getString(R.string.resend_otp_button)

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(requireContext(), getString(R.string.resend_success), Toast.LENGTH_LONG).show()
                    startCooldown()

                    // Navigate back to verify OTP with fresh state
                    try {
                        val bundle = bundleOf(
                            "userId" to userId,
                            "email" to email
                        )
                        findNavController().navigate(R.id.action_otpResendRequired_to_verifyOtp, bundle)
                    } catch (e: Exception) {
                        android.util.Log.e("OtpResendRequiredFragment", "Navigation error", e)
                        findNavController().navigateUp()
                    }
                } else {
                    btnResendOtp.isEnabled = true
                    btnResendOtp.text = getString(R.string.resend_otp_button)

                    val errorMsg = try {
                        val errorBody = response.errorBody()?.string()
                        val jsonError = org.json.JSONObject(errorBody ?: "{}")
                        jsonError.optString("message", getString(R.string.resend_failed_default))
                    } catch (e: Exception) {
                        response.body()?.message ?: getString(R.string.resend_failed_default)
                    }
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnResendOtp.isEnabled = true
                btnResendOtp.text = getString(R.string.resend_otp_button)

                val errorMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        getString(R.string.network_timeout)
                    e.message?.contains("unable to resolve host", ignoreCase = true) == true ->
                        getString(R.string.network_unreachable)
                    else -> getString(R.string.generic_error, e.message ?: "")
                }

                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideBottomNavigation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()

        // ✅ Chỉ hiện bottom navigation nếu đã đăng nhập
        showBottomNavigationIfLoggedIn()
    }

    private fun hideBottomNavigation() {
        try {
            val bottomNav = activity?.findViewById<View>(R.id.bottom_nav_view)
            bottomNav?.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.e("OtpResendRequiredFragment", "Error hiding bottom navigation", e)
        }
    }

    private fun showBottomNavigationIfLoggedIn() {
        try {
            val prefs = com.example.morp_prj.utils.PreferenceManager(requireContext())
            val tokenStorage = com.example.morp_prj.security.SecureTokenStorage(requireContext())
            val shouldShow = prefs.isLoggedIn() || tokenStorage.hasValidRefreshToken()
            val bottomNav = activity?.findViewById<View>(R.id.bottom_nav_view)
            bottomNav?.visibility = if (shouldShow) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            android.util.Log.e("OtpResendRequiredFragment", "Error showing bottom navigation", e)
        }
    }
}
