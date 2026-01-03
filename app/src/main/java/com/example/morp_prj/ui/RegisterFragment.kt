package com.example.morp_prj.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.repository.AuthRepository
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private lateinit var authRepository: AuthRepository
    private lateinit var preferenceManager: PreferenceManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Ẩn bottom navigation bar
        hideBottomNavigation()

        authRepository = AuthRepository(requireContext())
        preferenceManager = PreferenceManager(requireContext())

        // Back button handler
        val btnBack = view.findViewById<View>(R.id.btn_back)
        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        val inputFirstName = view.findViewById<TextInputEditText>(R.id.input_first_name)
        val inputLastName = view.findViewById<TextInputEditText>(R.id.input_last_name)
        val inputUsername = view.findViewById<TextInputEditText>(R.id.input_username)
        val inputEmail = view.findViewById<TextInputEditText>(R.id.input_email)
        val inputPassword = view.findViewById<TextInputEditText>(R.id.input_password)
        val inputPhone = view.findViewById<TextInputEditText>(R.id.input_phone)
        val btnRegister = view.findViewById<MaterialButton>(R.id.btn_register)

        // Password requirement TextViews
        val txtRequirementLength = view.findViewById<TextView>(R.id.txt_requirement_length)
        val txtRequirementUppercase = view.findViewById<TextView>(R.id.txt_requirement_uppercase)
        val txtRequirementLowercase = view.findViewById<TextView>(R.id.txt_requirement_lowercase)
        val txtRequirementNumber = view.findViewById<TextView>(R.id.txt_requirement_number)
        val txtRequirementSpecial = view.findViewById<TextView>(R.id.txt_requirement_special)

        // Real-time password validation
        inputPassword.addTextChangedListener(object : TextWatcher {
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

        btnRegister.setOnClickListener {
            val firstName = inputFirstName.text.toString().trim()
            val lastName = inputLastName.text.toString().trim()
            val username = inputUsername.text.toString().trim()
            val email = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()
            val phone = inputPhone.text.toString().trim()

            if (validateInput(firstName, lastName, username, email, password)) {
                val displayName = "$firstName $lastName"
                performRegister(username, password, displayName, email, phone.ifEmpty { null })
            }
        }
    }

    private fun validateInput(
        firstName: String,
        lastName: String,
        username: String,
        email: String,
        password: String
    ): Boolean {
        if (firstName.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập tên", Toast.LENGTH_SHORT).show()
            return false
        }

        if (lastName.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập họ", Toast.LENGTH_SHORT).show()
            return false
        }

        if (username.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập username", Toast.LENGTH_SHORT).show()
            return false
        }

        if (username.length < 3) {
            Toast.makeText(requireContext(), "Username phải có ít nhất 3 ký tự", Toast.LENGTH_SHORT).show()
            return false
        }

        if (email.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập email", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), "Email không hợp lệ", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show()
            return false
        }

        // Check all password requirements at once
        if (password.length < 8 ||
            !password.any { it.isUpperCase() } ||
            !password.any { it.isLowerCase() } ||
            !password.any { it.isDigit() } ||
            !password.any { it in "!@#\$%^&*" }
        ) {
            Toast.makeText(requireContext(), "Mật khẩu không đáp ứng yêu cầu", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
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
        val hasSpecial = password.any { it in "!@#\$%^&*" }

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

    private fun performRegister(
        username: String,
        password: String,
        displayName: String,
        email: String,
        phone: String?
    ) {
        // Show loading
        view?.findViewById<MaterialButton>(R.id.btn_register)?.isEnabled = false

        lifecycleScope.launch {
            val result = authRepository.register(username, password, displayName, email, phone)

            // Hide loading
            view?.findViewById<MaterialButton>(R.id.btn_register)?.isEnabled = true

            result.onSuccess { response ->
                if (response.success && response.userId != null && response.email != null) {
                    // Navigate to OTP verification screen
                    Toast.makeText(requireContext(), response.message, Toast.LENGTH_SHORT).show()

                    val bundle = Bundle().apply {
                        putString("userId", response.userId)
                        putString("email", response.email)
                        putInt("expiresIn", response.expiresIn ?: (2 * 60))
                        putInt("resendAvailableIn", response.resendAvailableIn ?: 60)
                    }
                    findNavController().navigate(R.id.action_register_to_verifyOtp, bundle)
                } else {
                    Toast.makeText(requireContext(), response.message, Toast.LENGTH_LONG).show()
                }
            }.onFailure { error ->
                val errorMessage = error.message ?: "Không thể kết nối đến server"
                android.util.Log.e("RegisterFragment", "Register failed: $errorMessage", error)
                Toast.makeText(
                    requireContext(),
                    "Lỗi: $errorMessage",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideBottomNavigation()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // ✅ Chỉ hiển thị lại nếu user đã đăng nhập
        showBottomNavigationIfLoggedIn()
    }

    private fun hideBottomNavigation() {
        try {
            val bottomNav = activity?.findViewById<View>(R.id.bottom_nav_view)
            bottomNav?.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.e("RegisterFragment", "Error hiding bottom navigation", e)
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
            android.util.Log.e("RegisterFragment", "Error showing bottom navigation", e)
        }
    }
}
