package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
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

class LoginFragment : Fragment(R.layout.fragment_login) {

    private lateinit var authRepository: AuthRepository
    private lateinit var preferenceManager: PreferenceManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authRepository = AuthRepository()
        preferenceManager = PreferenceManager(requireContext())

        val inputEmailOrUsername = view.findViewById<TextInputEditText>(R.id.input_email_or_username)
        val inputPassword = view.findViewById<TextInputEditText>(R.id.input_password)
        val btnLogin = view.findViewById<MaterialButton>(R.id.btn_login)
        val txtRegisterLink = view.findViewById<TextView>(R.id.txt_register_link)

        btnLogin.setOnClickListener {
            val usernameOrEmail = inputEmailOrUsername.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (validateInput(usernameOrEmail, password)) {
                performLogin(usernameOrEmail, password)
            }
        }

        txtRegisterLink.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        view.findViewById<View?>(R.id.btn_back)?.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun validateInput(usernameOrEmail: String, password: String): Boolean {
        if (usernameOrEmail.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập email hoặc username", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun performLogin(usernameOrEmail: String, password: String) {
        // Show loading
        view?.findViewById<MaterialButton>(R.id.btn_login)?.isEnabled = false

        lifecycleScope.launch {
            val result = authRepository.login(usernameOrEmail, password)

            // Hide loading
            view?.findViewById<MaterialButton>(R.id.btn_login)?.isEnabled = true

            result.onSuccess { response ->
                if (response.success && response.user != null) {
                    // Save user data
                    preferenceManager.saveLoginData(
                        userId = response.user.id,
                        username = response.user.username,
                        displayName = response.user.displayName,
                        email = response.user.email,
                        token = response.token
                    )

                    Toast.makeText(requireContext(), "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_login_to_home)
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
