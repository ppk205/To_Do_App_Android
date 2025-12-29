package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.repository.AuthRepository
import com.example.morp_prj.data.repository.SessionTaskManager
import com.example.morp_prj.data.repository.TaskSyncRepository
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginFragment : Fragment(R.layout.fragment_login) {

    private lateinit var authRepository: AuthRepository
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var sessionTaskManager: SessionTaskManager
    private lateinit var taskSyncRepository: TaskSyncRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authRepository = AuthRepository(requireContext())
        preferenceManager = PreferenceManager(requireContext())
        sessionTaskManager = SessionTaskManager(requireContext())
        taskSyncRepository = TaskSyncRepository(requireContext())

        val inputEmailOrUsername = view.findViewById<TextInputEditText>(R.id.input_email_or_username)
        val inputPassword = view.findViewById<TextInputEditText>(R.id.input_password)
        val btnLogin = view.findViewById<MaterialButton>(R.id.btn_login)
        val txtRegisterLink = view.findViewById<TextView>(R.id.txt_register_link)

        btnLogin.setOnClickListener {
            // Safe read of text (avoid NPE if .text is null)
            val usernameOrEmail = inputEmailOrUsername?.text?.toString()?.trim().orEmpty()
            val password = inputPassword?.text?.toString()?.trim().orEmpty()

            if (!validateInput(usernameOrEmail, password)) return@setOnClickListener

            performLogin(usernameOrEmail, password, btnLogin)
        }

        txtRegisterLink.setOnClickListener {
            if (isAdded) {
                findNavController().navigate(R.id.action_login_to_register)
            }
        }

        view.findViewById<View?>(R.id.btn_back)?.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.action_login_to_onboarding)
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

    private fun performLogin(usernameOrEmail: String, password: String, btnLogin: MaterialButton?) {
        // Show loading
        try {
            btnLogin?.isEnabled = false
        } catch (e: Exception) {
            // ignore UI update errors
        }

        val handler = CoroutineExceptionHandler { _, throwable ->
            Log.e("LoginFragment", "Unhandled login coroutine error", throwable)
            try {
                Toast.makeText(requireContext(), "Lỗi nội bộ: ${throwable.message}", Toast.LENGTH_LONG).show()
            } catch (ex: Exception) {
                // ignore
            }
        }

        // Use viewLifecycleOwner scope so coroutine cancels when view destroyed
        viewLifecycleOwner.lifecycleScope.launch(handler) {
            val result = try {
                withContext(Dispatchers.IO) {
                    authRepository.login(usernameOrEmail, password)
                }
            } catch (e: Exception) {
                Log.e("LoginFragment", "Login request failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lỗi: ${e.message ?: "Không thể kết nối đến server"}", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // Hide loading
            try {
                btnLogin?.isEnabled = true
            } catch (e: Exception) {
                // ignore
            }

            result.onSuccess { response ->
                try {
                    if (response.success && response.user != null) {
                        // Save user data - guard nullability
                        val user = response.user
                        try {
                            preferenceManager.saveLoginData(
                                userId = user.id,
                                username = user.username,
                                displayName = user.displayName,
                                email = user.email,
                                token = response.token
                            )

                            // Apply session task rules (show this user's tasks; later can trigger sync-down)
                            try {
                                sessionTaskManager.onLoginSuccess(user.id)
                            } catch (t: Throwable) {
                                Log.w("LoginFragment", "SessionTaskManager.onLoginSuccess failed", t)
                            }

                            // ✅ Auto refresh đúng 1 lần sau login để hiển thị task ngay
                            try {
                                withContext(Dispatchers.IO) {
                                    taskSyncRepository.syncDown()
                                }
                            } catch (t: Throwable) {
                                Log.w("LoginFragment", "Auto syncDown after login failed", t)
                            }
                        } catch (e: Exception) {
                            Log.e("LoginFragment", "Failed to save login data", e)
                        }

                        try {
                            Toast.makeText(requireContext(), "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                        }

                        // Ensure fragment still added before navigating
                        if (isAdded) {
                            findNavController().navigate(R.id.action_login_to_home)
                        }
                    } else {
                        try {
                            Toast.makeText(requireContext(), response.message, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LoginFragment", "Error handling login result", e)
                    try {
                        Toast.makeText(requireContext(), "Lỗi xử lý dữ liệu đăng nhập", Toast.LENGTH_LONG).show()
                    } catch (ex: Exception) {
                        // ignore
                    }
                }
            }.onFailure { error ->
                Log.e("LoginFragment", "Login failed", error)
                try {
                    Toast.makeText(
                        requireContext(),
                        "Lỗi: ${error.message ?: "Không thể kết nối đến server"}",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }
}
