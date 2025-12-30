package com.example.morp_prj.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
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
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResetPasswordFragment : Fragment(R.layout.fragment_reset_password) {

    private lateinit var authRepository: AuthRepository
    private var email: String = ""
    private var otp: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authRepository = AuthRepository(requireContext())

        // Get email and OTP from arguments
        email = arguments?.getString("email") ?: ""
        otp = arguments?.getString("otp") ?: ""

        if (email.isEmpty() || otp.isEmpty()) {
            Toast.makeText(requireContext(), "Invalid reset session", Toast.LENGTH_SHORT).show()
            if (isAdded) {
                findNavController().navigateUp()
            }
            return
        }

        val inputNewPassword = view.findViewById<TextInputEditText>(R.id.input_new_password)
        val inputConfirmPassword = view.findViewById<TextInputEditText>(R.id.input_confirm_password)
        val layoutNewPassword = view.findViewById<TextInputLayout>(R.id.layout_new_password)
        val layoutConfirmPassword = view.findViewById<TextInputLayout>(R.id.layout_confirm_password)
        val btnResetPassword = view.findViewById<MaterialButton>(R.id.btn_reset_password)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val btnBack = view.findViewById<View>(R.id.btn_back)

        // Password requirement TextViews
        val txtRequirementLength = view.findViewById<TextView>(R.id.txt_requirement_length)
        val txtRequirementUppercase = view.findViewById<TextView>(R.id.txt_requirement_uppercase)
        val txtRequirementLowercase = view.findViewById<TextView>(R.id.txt_requirement_lowercase)
        val txtRequirementNumber = view.findViewById<TextView>(R.id.txt_requirement_number)
        val txtRequirementSpecial = view.findViewById<TextView>(R.id.txt_requirement_special)

        // Back button
        btnBack.setOnClickListener {
            if (isAdded) {
                findNavController().navigateUp()
            }
        }

        // Real-time password validation
        inputNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePasswordRequirements(
                    s.toString(),
                    txtRequirementLength,
                    txtRequirementUppercase,
                    txtRequirementLowercase,
                    txtRequirementNumber,
                    txtRequirementSpecial
                )
            }
        })

        // Reset Password button
        btnResetPassword.setOnClickListener {
            val newPassword = inputNewPassword.text.toString()
            val confirmPassword = inputConfirmPassword.text.toString()

            if (validatePasswords(newPassword, confirmPassword, layoutNewPassword, layoutConfirmPassword)) {
                resetPassword(
                    newPassword,
                    confirmPassword,
                    btnResetPassword,
                    progressBar
                )
            }
        }
    }

    private fun validatePasswordRequirements(
        password: String,
        txtLength: TextView,
        txtUppercase: TextView,
        txtLowercase: TextView,
        txtNumber: TextView,
        txtSpecial: TextView
    ) {
        val hasLength = password.length >= 8
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { it in "@\$!%*?&" }

        updateRequirement(txtLength, hasLength)
        updateRequirement(txtUppercase, hasUpper)
        updateRequirement(txtLowercase, hasLower)
        updateRequirement(txtNumber, hasDigit)
        updateRequirement(txtSpecial, hasSpecial)
    }

    private fun updateRequirement(textView: TextView, met: Boolean) {
        if (met) {
            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            textView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_check_circle, 0, 0, 0
            )
        } else {
            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            textView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_circle, 0, 0, 0
            )
        }
    }

    private fun validatePasswords(
        newPassword: String,
        confirmPassword: String,
        layoutNewPassword: TextInputLayout,
        layoutConfirmPassword: TextInputLayout
    ): Boolean {
        if (newPassword.isEmpty()) {
            layoutNewPassword.error = getString(R.string.reset_password_empty)
            return false
        }

        if (newPassword.length < 8 ||
            !newPassword.any { it.isUpperCase() } ||
            !newPassword.any { it.isLowerCase() } ||
            !newPassword.any { it.isDigit() } ||
            !newPassword.any { it in "@\$!%*?&" }
        ) {
            layoutNewPassword.error = getString(R.string.reset_password_weak)
            return false
        }

        if (newPassword != confirmPassword) {
            layoutConfirmPassword.error = getString(R.string.reset_password_mismatch)
            return false
        }

        layoutNewPassword.error = null
        layoutConfirmPassword.error = null
        return true
    }

    private fun resetPassword(
        newPassword: String,
        confirmPassword: String,
        btnResetPassword: MaterialButton,
        progressBar: ProgressBar
    ) {
        // Show loading
        btnResetPassword.isEnabled = false
        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    authRepository.resetPassword(email, otp, newPassword, confirmPassword)
                }

                // Hide loading
                progressBar.visibility = View.GONE
                btnResetPassword.isEnabled = true

                if (result.isSuccess && result.getOrNull()?.success == true) {
                    // Navigate to reset success fragment
                    if (isAdded) {
                        try {
                            findNavController().navigate(R.id.action_resetPassword_to_resetSuccess)
                        } catch (e: Exception) {
                            // fallback to login
                            findNavController().navigate(R.id.action_resetPassword_to_login)
                        }
                    }
                } else {
                    val errorMessage = result.getOrNull()?.message
                        ?: "Failed to reset password"
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("ResetPasswordFragment", "Error resetting password", e)
                progressBar.visibility = View.GONE
                btnResetPassword.isEnabled = true

                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message ?: "Cannot connect to server"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
