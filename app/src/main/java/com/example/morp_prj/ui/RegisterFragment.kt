package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
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

        authRepository = AuthRepository()
        preferenceManager = PreferenceManager(requireContext())

        val inputFirstName = view.findViewById<TextInputEditText>(R.id.input_first_name)
        val inputLastName = view.findViewById<TextInputEditText>(R.id.input_last_name)
        val inputUsername = view.findViewById<TextInputEditText>(R.id.input_username)
        val inputEmail = view.findViewById<TextInputEditText>(R.id.input_email)
        val inputPassword = view.findViewById<TextInputEditText>(R.id.input_password)
        val inputPhone = view.findViewById<TextInputEditText>(R.id.input_phone)
        val btnRegister = view.findViewById<MaterialButton>(R.id.btn_register)

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

        if (password.length < 6) {
            Toast.makeText(requireContext(), "Mật khẩu phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
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
                    }
                    findNavController().navigate(R.id.action_register_to_verifyOtp, bundle)
                } else {
                    Toast.makeText(requireContext(), response.message, Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Lỗi: ${error.message ?: "Không thể kết nối đến server"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
