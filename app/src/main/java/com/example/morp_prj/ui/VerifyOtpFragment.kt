package com.example.morp_prj.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.ResendOTPRequest
import com.example.morp_prj.data.model.VerifyOTPRequest
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.launch
import java.util.Locale

class VerifyOtpFragment : Fragment(R.layout.fragment_verify_otp) {

    private lateinit var preferenceManager: PreferenceManager
    private var userId: String? = null
    private var email: String? = null
    private var username: String? = null
    private var countDownTimer: CountDownTimer? = null
    private var initialExpiresInSeconds: Int = 120
    private var resendAvailableInSeconds: Int = 60
    private var otpAttempts = 0

    private lateinit var btnBack: ImageView
    private lateinit var tvEmail: TextView
    private lateinit var tvTimer: TextView
    private lateinit var etOtp1: TextInputEditText
    private lateinit var etOtp2: TextInputEditText
    private lateinit var etOtp3: TextInputEditText
    private lateinit var etOtp4: TextInputEditText
    private lateinit var etOtp5: TextInputEditText
    private lateinit var etOtp6: TextInputEditText
    private lateinit var btnVerify: MaterialButton
    private lateinit var btnResend: TextView
    private lateinit var btnPaste: TextView
    private lateinit var tvAttempts: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        // Get arguments
        userId = arguments?.getString("userId")
        email = arguments?.getString("email")
        username = arguments?.getString("username")
        // Read TTL values passed from register response (seconds)
        initialExpiresInSeconds = arguments?.getInt("expiresIn") ?: initialExpiresInSeconds
        resendAvailableInSeconds = arguments?.getInt("resendAvailableIn") ?: resendAvailableInSeconds

        android.util.Log.d("VerifyOtpFragment", "userId: $userId, email: $email, username: $username, expiresIn=$initialExpiresInSeconds, resendIn=$resendAvailableInSeconds")

        if (userId == null || email == null) {
            Toast.makeText(requireContext(), getString(R.string.missing_user_info), Toast.LENGTH_LONG).show()
            android.util.Log.e("VerifyOtpFragment", "Missing arguments: userId=$userId, email=$email")
            findNavController().navigateUp()
            return
        }

        try {
            initViews(view)
            setupOtpInputs()
            startTimer()

            tvEmail.text = email

            btnVerify.setOnClickListener { verifyOTP() }
            btnResend.setOnClickListener { resendOTP() }
            btnBack.setOnClickListener { findNavController().navigateUp() }

            btnPaste.setOnClickListener { pasteFromClipboard() }

            updateVerifyButtonState()
        } catch (e: Exception) {
            android.util.Log.e("VerifyOtpFragment", "Error in onViewCreated", e)
            Toast.makeText(requireContext(), getString(R.string.init_error_format, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews(view: View) {
         btnBack = view.findViewById(R.id.btn_back)
         tvEmail = view.findViewById(R.id.tvEmail)
         tvTimer = view.findViewById(R.id.tvTimer)
         etOtp1 = view.findViewById(R.id.etOtp1)
         etOtp2 = view.findViewById(R.id.etOtp2)
         etOtp3 = view.findViewById(R.id.etOtp3)
         etOtp4 = view.findViewById(R.id.etOtp4)
         etOtp5 = view.findViewById(R.id.etOtp5)
         etOtp6 = view.findViewById(R.id.etOtp6)
         btnVerify = view.findViewById(R.id.btnVerify)
         btnResend = view.findViewById(R.id.btnResend)
         btnPaste = view.findViewById(R.id.btnPaste)
         tvAttempts = view.findViewById(R.id.tvAttempts)
    }

    private fun setupOtpInputs() {
        val otpInputs = listOf(etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6)

        otpInputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                private var isDeleting = false

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    isDeleting = count > after
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Nếu nhập ký tự mới (không phải xóa)
                    if (!isDeleting && s?.length == 1) {
                        // Tự động nhảy sang ô tiếp theo
                        if (index < otpInputs.size - 1) {
                            otpInputs[index + 1].requestFocus()
                        } else {
                            // Đã nhập đủ 6 số, ẩn bàn phím
                            editText.clearFocus()
                            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(editText.windowToken, 0)
                        }
                    }

                    // Update verify button state mỗi lần có thay đổi
                    updateVerifyButtonState()
                }

                override fun afterTextChanged(s: Editable?) {
                    // Giới hạn chỉ 1 ký tự
                    if (s != null && s.length > 1) {
                        s.delete(1, s.length)
                    }
                }
            })

            editText.setOnKeyListener { _, keyCode, event ->
                // Xử lý phím Delete/Backspace
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (editText.text.isNullOrEmpty() && index > 0) {
                        // Ô hiện tại rỗng, nhảy về ô trước và xóa
                        otpInputs[index - 1].text?.clear()
                        otpInputs[index - 1].requestFocus()
                        updateVerifyButtonState()
                        return@setOnKeyListener true
                    }
                }
                false
            }

            // Disable paste by long click to avoid accidental paste; we provide a dedicated Paste action
            editText.setOnLongClickListener { true }
        }

        // Focus và show keyboard cho ô đầu tiên
        etOtp1.requestFocus()
        etOtp1.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etOtp1, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun updateVerifyButtonState() {
        val filled = listOf(etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6).all { it.text?.length == 1 }
        btnVerify.isEnabled = filled
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(requireContext()).toString()
                // Extract digits only
                val digits = text.filter { it.isDigit() }
                if (digits.length >= 6) {
                    val otp = digits.substring(0, 6)
                    etOtp1.setText(otp[0].toString())
                    etOtp2.setText(otp[1].toString())
                    etOtp3.setText(otp[2].toString())
                    etOtp4.setText(otp[3].toString())
                    etOtp5.setText(otp[4].toString())
                    etOtp6.setText(otp[5].toString())

                    // Hide keyboard
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(requireView().windowToken, 0)

                    updateVerifyButtonState()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.clipboard_invalid), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("VerifyOtpFragment", "Paste error", e)
            Toast.makeText(requireContext(), getString(R.string.resend_failed_default), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTimer() {
        btnResend.isEnabled = false
        countDownTimer?.cancel()

        val millis = initialExpiresInSeconds * 1000L
        countDownTimer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                tvTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                tvTimer.text = "00:00"
                tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
                btnResend.isEnabled = true
                Toast.makeText(requireContext(), getString(R.string.otp_expired), Toast.LENGTH_SHORT).show()
            }
        }.start()

        // Disable resend for resendAvailableInSeconds
        btnResend.isEnabled = false
        if (resendAvailableInSeconds > 0) {
            object : CountDownTimer(resendAvailableInSeconds * 1000L, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() { btnResend.isEnabled = true }
            }.start()
        } else {
            btnResend.isEnabled = true
        }
    }

    private fun verifyOTP() {
        val otp = "${etOtp1.text}${etOtp2.text}${etOtp3.text}" +
                "${etOtp4.text}${etOtp5.text}${etOtp6.text}"

        android.util.Log.d("VerifyOtpFragment", "Attempting to verify OTP: $otp")

        if (otp.length != 6) {
            Toast.makeText(requireContext(), getString(R.string.otp_incomplete), Toast.LENGTH_SHORT).show()
            return
        }

        otpAttempts++
        tvAttempts.text = getString(R.string.attempts_format, otpAttempts)

        if (otpAttempts >= 5) {
            tvAttempts.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
        }

        btnVerify.isEnabled = false
        btnVerify.text = getString(R.string.verifying)

        lifecycleScope.launch {
            try {
                val request = VerifyOTPRequest(
                    userId = userId!!,
                    email = email!!,
                    otp = otp,
                    purpose = "REGISTER"
                )

                android.util.Log.d("VerifyOtpFragment", "Sending verify request: userId=$userId, email=$email, purpose=REGISTER")

                val response = RetrofitClient.authApiService.verifyOTP(request)

                btnVerify.isEnabled = true
                btnVerify.text = getString(R.string.verify)

                android.util.Log.d("VerifyOtpFragment", "Response code: ${response.code()}")
                android.util.Log.d("VerifyOtpFragment", "Response success: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val authResponse = response.body()
                    android.util.Log.d("VerifyOtpFragment", "Response body: $authResponse")

                    if (authResponse?.success == true) {
                        android.util.Log.d("VerifyOtpFragment", "OTP verification successful")

                        // Save user data and token
                        val savedUserId = authResponse.user?.id ?: authResponse.userId ?: userId!!
                        val savedUsername = authResponse.user?.username ?: username ?: ""
                        val savedDisplayName = authResponse.user?.displayName ?: ""
                        val savedEmail = authResponse.user?.email ?: email!!
                        val savedToken = authResponse.token ?: ""

                        android.util.Log.d("VerifyOtpFragment", "Saving user data: userId=$savedUserId, username=$savedUsername, token=${savedToken.take(20)}...")

                        preferenceManager.saveLoginData(
                            userId = savedUserId,
                            username = savedUsername,
                            displayName = savedDisplayName,
                            email = savedEmail,
                            token = savedToken
                        )

                        // Also save tokens securely (if provided) so session persists across app restarts
                        try {
                            val tokenStorage = com.example.morp_prj.security.SecureTokenStorage(requireContext())
                            // Prefer accessToken/refreshToken fields if present
                            authResponse.accessToken?.let { at ->
                                tokenStorage.saveAccessToken(at, authResponse.accessTTL ?: 1800)
                            }
                            authResponse.refreshToken?.let { rt ->
                                tokenStorage.saveRefreshToken(rt, authResponse.refreshTTL ?: 2592000)
                            }
                            // Save session metadata when available
                            if (!authResponse.sessionId.isNullOrEmpty()) {
                                tokenStorage.saveSessionMetadata(authResponse.sessionId!!, savedUserId)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VerifyOtpFragment", "Failed to save secure tokens", e)
                        }

                        // Navigate to register success fragment
                        try {
                            val bundle = bundleOf(
                                "username" to savedUsername,
                                "email" to savedEmail
                            )
                            findNavController().navigate(R.id.action_verifyOtp_to_registerSuccess, bundle)
                        } catch (e: Exception) {
                            android.util.Log.e("VerifyOtpFragment", "Navigation error", e)
                            // Fallback: show toast and navigate to home
                            Toast.makeText(requireContext(), getString(R.string.verify_success_welcome, savedDisplayName), Toast.LENGTH_LONG).show()
                            findNavController().navigate(R.id.action_verifyOtp_to_home)
                        }
                    } else {
                        val errorMsg = authResponse?.message ?: "Xác thực thất bại"
                        android.util.Log.e("VerifyOtpFragment", "Verification failed: $errorMsg")
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                        clearOtpInputs()
                    }
                } else {
                    val errorMsg = try {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("VerifyOtpFragment", "Error response: $errorBody")

                        // Try parse error JSON
                        val jsonError = org.json.JSONObject(errorBody ?: "{}")
                        jsonError.optString("message", getString(R.string.otp_expired))
                    } catch (_: Exception) {
                        getString(R.string.otp_expired)
                    }

                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                    clearOtpInputs()

                    if (otpAttempts >= 5) {
                        // Navigate to OTP resend required fragment
                        try {
                            val bundle = bundleOf(
                                "userId" to userId,
                                "email" to email
                            )
                            findNavController().navigate(R.id.action_verifyOtp_to_otpResendRequired, bundle)
                        } catch (e: Exception) {
                            android.util.Log.e("VerifyOtpFragment", "Navigation to resend required error", e)
                            btnResend.isEnabled = true
                            Toast.makeText(requireContext(), getString(R.string.attempts_limit_message), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // allow resend after a failed attempt when not exceeding limit
                        btnResend.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                btnVerify.isEnabled = true
                btnVerify.text = getString(R.string.verify)
                android.util.Log.e("VerifyOtpFragment", "Exception during verification", e)

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

    private fun clearOtpInputs() {
        etOtp1.text?.clear()
        etOtp2.text?.clear()
        etOtp3.text?.clear()
        etOtp4.text?.clear()
        etOtp5.text?.clear()
        etOtp6.text?.clear()
        etOtp1.requestFocus()
    }

    private fun resendOTP() {
        btnResend.isEnabled = false
        android.util.Log.d("VerifyOtpFragment", "Resending OTP for userId=$userId, email=$email")

        lifecycleScope.launch {
            try {
                val request = ResendOTPRequest(
                    userId = userId!!,
                    email = email!!,
                    purpose = "REGISTER"
                )

                val response = RetrofitClient.authApiService.resendOTP(request)

                android.util.Log.d("VerifyOtpFragment", "Resend response code: ${response.code()}")
                android.util.Log.d("VerifyOtpFragment", "Resend response body: ${response.body()}")

                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(requireContext(), getString(R.string.resend_success), Toast.LENGTH_LONG).show()

                    // Reset attempts and timer
                    otpAttempts = 0
                    tvAttempts.text = getString(R.string.attempts_format, 0)
                    tvAttempts.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))

                    // Clear OTP inputs
                    clearOtpInputs()

                    // Update TTLs from server response (seconds)
                    val respBody = response.body()
                    respBody?.expiresIn?.let { initialExpiresInSeconds = it }
                    respBody?.resendAvailableIn?.let { resendAvailableInSeconds = it }

                    // Restart timer
                    startTimer()

                    android.util.Log.d("VerifyOtpFragment", "OTP resent successfully")
                } else {
                    btnResend.isEnabled = true
                    val errorMsg = try {
                         val errorBody = response.errorBody()?.string()
                         android.util.Log.e("VerifyOtpFragment", "Resend error response: $errorBody")
                         val jsonError = org.json.JSONObject(errorBody ?: "{}")
                        jsonError.optString("message", getString(R.string.resend_failed_default))
                     } catch (e: Exception) {
                        response.body()?.message ?: getString(R.string.resend_failed_default)
                     }
                     Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                    btnResend.isEnabled = true
                }
            } catch (e: Exception) {
                btnResend.isEnabled = true
                android.util.Log.e("VerifyOtpFragment", "Exception during resend", e)

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

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
    }
}
