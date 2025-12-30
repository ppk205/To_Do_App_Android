package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.repository.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForgotPasswordFragment : Fragment(R.layout.fragment_forgot_password) {

    private lateinit var authRepository: AuthRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authRepository = AuthRepository(requireContext())

        val inputEmail = view.findViewById<TextInputEditText>(R.id.input_email)
        val layoutEmail = view.findViewById<TextInputLayout>(R.id.layout_email)
        val btnSendResetLink = view.findViewById<MaterialButton>(R.id.btn_send_reset_link)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val cardSuccess = view.findViewById<MaterialCardView>(R.id.card_success)
        val btnBack = view.findViewById<View>(R.id.btn_back)
        val txtBackToLogin = view.findViewById<View>(R.id.txt_back_to_login)

        // Back button - Navigate to Login
        btnBack.setOnClickListener {
            if (isAdded) {
                findNavController().navigateUp()
            }
        }

        // Back to Login link
        txtBackToLogin.setOnClickListener {
            if (isAdded) {
                findNavController().navigateUp()
            }
        }

        // Send Reset Link button
        btnSendResetLink.setOnClickListener {
            val email = inputEmail.text.toString().trim()

            // Validate email
            if (email.isEmpty()) {
                layoutEmail.error = "Please enter your email"
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                layoutEmail.error = "Please enter a valid email"
                return@setOnClickListener
            }

            // Clear error
            layoutEmail.error = null

            // Send reset link
            sendResetLink(email, btnSendResetLink, progressBar, cardSuccess, inputEmail, layoutEmail)
        }
    }

    private fun sendResetLink(
        email: String,
        btnSendResetLink: MaterialButton,
        progressBar: ProgressBar,
        cardSuccess: MaterialCardView,
        inputEmail: TextInputEditText,
        layoutEmail: TextInputLayout
    ) {
        // Show loading
        btnSendResetLink.isEnabled = false
        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("ForgotPasswordFragment", "Sending forgot password request for: $email")

                val result = withContext(Dispatchers.IO) {
                    authRepository.forgotPassword(email)
                }

                Log.d("ForgotPasswordFragment", "Result received - isSuccess: ${result.isSuccess}")
                Log.d("ForgotPasswordFragment", "Result body: ${result.getOrNull()}")
                Log.d("ForgotPasswordFragment", "Result success field: ${result.getOrNull()?.success}")
                Log.d("ForgotPasswordFragment", "Result message: ${result.getOrNull()?.message}")

                // Hide loading
                progressBar.visibility = View.GONE
                btnSendResetLink.isEnabled = true

                if (result.isSuccess && result.getOrNull()?.success == true) {
                    Log.d("ForgotPasswordFragment", "Success! Navigating to VerifyResetOTP")

                    // Navigate to Verify Reset OTP screen with email
                    val bundle = Bundle().apply {
                        putString("email", email)
                    }

                    if (isAdded) {
                        findNavController().navigate(
                            R.id.action_forgotPassword_to_verifyResetOTP,
                            bundle
                        )
                    }
                } else {
                    Log.e("ForgotPasswordFragment", "Failed - showing error")
                    val errorMessage = result.getOrNull()?.message
                        ?: "Failed to send reset link. Please try again."
                    Log.e("ForgotPasswordFragment", "Error message: $errorMessage")
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("ForgotPasswordFragment", "Exception caught!", e)
                Log.e("ForgotPasswordFragment", "Exception message: ${e.message}")
                Log.e("ForgotPasswordFragment", "Exception type: ${e.javaClass.simpleName}")

                progressBar.visibility = View.GONE
                btnSendResetLink.isEnabled = true

                val errorMsg = "Error: ${e.message ?: "Cannot connect to server"}"
                Log.e("ForgotPasswordFragment", "Showing error toast: $errorMsg")

                Toast.makeText(
                    requireContext(),
                    errorMsg,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showSuccessMessage(
        cardSuccess: MaterialCardView,
        inputEmail: TextInputEditText,
        layoutEmail: TextInputLayout,
        btnSendResetLink: MaterialButton
    ) {
        // Hide form elements
        inputEmail.visibility = View.GONE
        layoutEmail.visibility = View.GONE
        btnSendResetLink.visibility = View.GONE

        // Show success card
        cardSuccess.visibility = View.VISIBLE
    }
}

