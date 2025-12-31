package com.example.morp_prj.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.repository.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class VerifyResetOTPFragment : Fragment(R.layout.fragment_verify_reset_otp) {

    private lateinit var authRepository: AuthRepository
    private var email: String = ""
    private var countDownTimer: CountDownTimer? = null
    private var resendCooldownTimer: CountDownTimer? = null
    private var otpAttempts = 0

    private lateinit var otpInputs: List<TextInputEditText>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authRepository = AuthRepository(requireContext())

        // Get email from arguments
        email = arguments?.getString("email") ?: ""

        if (email.isEmpty()) {
            Toast.makeText(requireContext(), "Email is missing", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        val tvEmail = view.findViewById<TextView>(R.id.tvEmail)
        val btnBack = view.findViewById<View>(R.id.btn_back)
        val btnVerify = view.findViewById<MaterialButton>(R.id.btnVerify)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val tvTimer = view.findViewById<TextView>(R.id.tvTimer)
        val btnResend = view.findViewById<TextView>(R.id.btnResend)
        val txtResendCooldown = view.findViewById<TextView>(R.id.txt_resend_cooldown)
        val btnPaste = view.findViewById<TextView>(R.id.btnPaste)
        val tvAttempts = view.findViewById<TextView>(R.id.tvAttempts)

        // OTP inputs
        otpInputs = listOf(
            view.findViewById(R.id.etOtp1),
            view.findViewById(R.id.etOtp2),
            view.findViewById(R.id.etOtp3),
            view.findViewById(R.id.etOtp4),
            view.findViewById(R.id.etOtp5),
            view.findViewById(R.id.etOtp6)
        )

        // Display email
        tvEmail.text = email

        // Setup OTP inputs
        setupOTPInputs()

        // Start countdown timer (10 minutes = 600 seconds)
        startCountdownTimer(tvTimer, 600)

        // Initialize attempts display
        tvAttempts.text = getString(R.string.attempts_format, otpAttempts)

        // Back button
        btnBack.setOnClickListener {
            if (isAdded) {
                findNavController().navigateUp()
            }
        }

        // Verify button
        btnVerify.setOnClickListener {
            val otp = getOTPCode()
            if (otp.length == 6) {
                verifyOTP(otp, btnVerify, progressBar, tvAttempts)
            } else {
                Toast.makeText(requireContext(), "Please enter 6-digit OTP", Toast.LENGTH_SHORT).show()
            }
        }

        // Paste button
        btnPaste.setOnClickListener {
            pasteFromClipboard()
        }

        // Resend OTP
        btnResend.setOnClickListener {
            if (btnResend.isEnabled) {
                resendOTP(btnResend, txtResendCooldown, tvTimer)
            }
        }
    }

    private fun setupOTPInputs() {
        otpInputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) {
                        // Move to next input
                        if (index < otpInputs.size - 1) {
                            otpInputs[index + 1].requestFocus()
                        } else {
                            // Hide keyboard when all inputs filled
                            editText.clearFocus()
                            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(editText.windowToken, 0)
                        }
                    } else if (s?.isEmpty() == true) {
                        // Move to previous input on backspace
                        if (index > 0) {
                            otpInputs[index - 1].requestFocus()
                        }
                    }
                    // Update verify button state
                    updateVerifyButtonState()
                }
            })

            // Disable long press to paste
            editText.setOnLongClickListener { true }
        }

        // Focus first input
        otpInputs[0].requestFocus()
        otpInputs[0].postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(otpInputs[0], InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun updateVerifyButtonState() {
        val btnVerify = view?.findViewById<MaterialButton>(R.id.btnVerify)
        val filled = otpInputs.all { it.text?.length == 1 }
        btnVerify?.isEnabled = filled
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
                    otpInputs.forEachIndexed { index, editText ->
                        editText.setText(otp[index].toString())
                    }

                    // Hide keyboard
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(requireView().windowToken, 0)

                    updateVerifyButtonState()
                    Toast.makeText(requireContext(), "OTP pasted from clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.clipboard_invalid), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("VerifyResetOTP", "Paste error", e)
            Toast.makeText(requireContext(), "Failed to paste from clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getOTPCode(): String {
        return otpInputs.joinToString("") { it.text.toString() }
    }

    private fun startCountdownTimer(tvTimer: TextView, seconds: Int) {
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val secs = (millisUntilFinished / 1000) % 60
                tvTimer.text = String.format(Locale.getDefault(), "OTP expires in %02d:%02d", minutes, secs)
            }

            override fun onFinish() {
                tvTimer.text = "OTP expired"
                tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
                // Enable resend when OTP expires
                view?.findViewById<TextView>(R.id.btnResend)?.isEnabled = true
            }
        }.start()
    }

    private fun verifyOTP(otp: String, btnVerify: MaterialButton, progressBar: ProgressBar, tvAttempts: TextView) {
        otpAttempts++
        tvAttempts.text = getString(R.string.attempts_format, otpAttempts)

        if (otpAttempts >= 5) {
            tvAttempts.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
        }

        btnVerify.isEnabled = false
        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    authRepository.verifyResetOTP(email, otp)
                }

                progressBar.visibility = View.GONE
                btnVerify.isEnabled = true

                if (result.isSuccess && result.getOrNull()?.success == true) {
                    // Navigate to Reset Password screen
                    val bundle = Bundle().apply {
                        putString("email", email)
                        putString("otp", otp)
                    }

                    if (isAdded) {
                        findNavController().navigate(
                            R.id.action_verifyResetOTP_to_resetPassword,
                            bundle
                        )
                    }
                } else {
                    val errorMessage = result.getOrNull()?.message ?: "Invalid OTP"
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()

                    // Clear OTP inputs on error
                    otpInputs.forEach { it.text?.clear() }
                    otpInputs[0].requestFocus()
                }

            } catch (e: Exception) {
                Log.e("VerifyResetOTP", "Error verifying OTP", e)
                progressBar.visibility = View.GONE
                btnVerify.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message ?: "Cannot connect to server"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun resendOTP(btnResend: TextView, txtResendCooldown: TextView, tvTimer: TextView) {
        btnResend.isEnabled = false
        txtResendCooldown.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    authRepository.resendResetOTP(email)
                }

                if (result.isSuccess && result.getOrNull()?.success == true) {
                    Toast.makeText(requireContext(), "OTP resent successfully", Toast.LENGTH_SHORT).show()

                    // Reset attempts
                    otpAttempts = 0
                    view?.findViewById<TextView>(R.id.tvAttempts)?.let { tvAttempts ->
                        tvAttempts.text = getString(R.string.attempts_format, 0)
                        tvAttempts.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    }

                    // Clear OTP inputs
                    otpInputs.forEach { it.text?.clear() }
                    otpInputs[0].requestFocus()

                    // Restart countdown
                    tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    startCountdownTimer(tvTimer, 600)

                    // Start 60s cooldown
                    startResendCooldown(btnResend, txtResendCooldown, 60)
                } else {
                    val errorMessage = result.getOrNull()?.message ?: "Failed to resend OTP"
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                    btnResend.isEnabled = true
                    txtResendCooldown.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("VerifyResetOTP", "Error resending OTP", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnResend.isEnabled = true
                txtResendCooldown.visibility = View.GONE
            }
        }
    }

    private fun startResendCooldown(btnResend: TextView, txtResendCooldown: TextView, seconds: Int) {
        resendCooldownTimer?.cancel()

        resendCooldownTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000).toInt()
                txtResendCooldown.text = "Resend available in ${secs}s"
            }

            override fun onFinish() {
                btnResend.isEnabled = true
                txtResendCooldown.visibility = View.GONE
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        resendCooldownTimer?.cancel()
    }
}

