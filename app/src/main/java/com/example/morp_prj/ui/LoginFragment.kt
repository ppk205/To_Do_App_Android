package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.repository.AuthRepository
import com.example.morp_prj.data.repository.NotificationRepository
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

        // ✅ Ẩn bottom navigation bar
        hideBottomNavigation()

        view.findViewById<MotionLayout?>(R.id.login_motion_layout)?.transitionToEnd()

        authRepository = AuthRepository(requireContext())
        preferenceManager = PreferenceManager(requireContext())
        sessionTaskManager = SessionTaskManager(requireContext())
        taskSyncRepository = TaskSyncRepository(requireContext())

        val inputEmailOrUsername = view.findViewById<TextInputEditText>(R.id.input_email_or_username)
        val inputPassword = view.findViewById<TextInputEditText>(R.id.input_password)
        val btnLogin = view.findViewById<MaterialButton>(R.id.btn_login)
        val txtRegisterLink = view.findViewById<TextView>(R.id.txt_register_link)
        val txtForgotPassword = view.findViewById<TextView>(R.id.txt_forgot_password)

        // Clear error when user starts typing + real-time email validation
        inputEmailOrUsername.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val layout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_email_or_username_layout)
                layout?.error = null

                // Real-time email format validation (only if it looks like an email)
                val text = s?.toString()?.trim() ?: ""
                if (text.isNotEmpty() && text.contains("@") && !android.util.Patterns.EMAIL_ADDRESS.matcher(text).matches()) {
                    layout?.error = "Invalid email format"
                }
            }
        })

        inputPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_password_layout)?.error = null
            }
        })

        // Trigger login when user presses Done on keyboard
        inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                btnLogin.performClick()
                true
            } else {
                false
            }
        }

        btnLogin.setOnClickListener {
            // Safe read of text (avoid NPE if .text is null)
            val usernameOrEmail = inputEmailOrUsername?.text?.toString()?.trim().orEmpty()
            val password = inputPassword?.text?.toString()?.trim().orEmpty()

            // Clear previous errors
            view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_email_or_username_layout)?.error = null
            view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_password_layout)?.error = null

            if (!validateInput(usernameOrEmail, password, view)) return@setOnClickListener

            performLogin(usernameOrEmail, password, btnLogin)
        }

        txtRegisterLink.setOnClickListener {
            if (isAdded) {
                findNavController().navigate(R.id.action_login_to_register)
            }
        }

        txtForgotPassword.setOnClickListener {
            if (isAdded) {
                findNavController().navigate(R.id.action_login_to_forgotPassword)
            }
        }

        view.findViewById<View?>(R.id.btn_back)?.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.action_login_to_onboarding)
        }
    }

    private fun validateInput(usernameOrEmail: String, password: String, view: View): Boolean {
        var isValid = true
        var firstErrorField: View? = null

        val emailLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_email_or_username_layout)
        val passwordLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_password_layout)
        val emailInput = view.findViewById<TextInputEditText>(R.id.input_email_or_username)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.input_password)

        if (usernameOrEmail.isEmpty()) {
            emailLayout?.error = "Required"
            if (firstErrorField == null) firstErrorField = emailInput
            isValid = false
        }

        if (password.isEmpty()) {
            passwordLayout?.error = "Required"
            if (firstErrorField == null) firstErrorField = passwordInput
            isValid = false
        }

        if (!isValid) {
            // Haptic feedback
            try {
                firstErrorField?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            } catch (_: Exception) {}

            // Focus on first error field
            firstErrorField?.requestFocus()

            // Scroll to first error field
            try {
                view.findViewById<ScrollView>(R.id.scroll_content)?.smoothScrollTo(0, firstErrorField?.top ?: 0)
            } catch (_: Exception) {}

            Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
        }

        return isValid
    }

    private fun performLogin(usernameOrEmail: String, password: String, btnLogin: MaterialButton?) {
        // Show loading
        try {
            btnLogin?.isEnabled = false
        } catch (_: Exception) {
            // ignore UI update errors
        }

        val handler = CoroutineExceptionHandler { _, throwable ->
            Log.e("LoginFragment", "Unhandled login coroutine error", throwable)
            try {
                Toast.makeText(requireContext(), "Internal error: ${throwable.message}", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
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
                    val errorMessage = when {
                        e.message?.contains("Unauthorized", ignoreCase = true) == true ||
                        e.message?.contains("401", ignoreCase = true) == true ->
                            "Username or password is incorrect. Please try again."
                        else -> "Error: ${e.message ?: "Cannot connect to server"}"
                    }
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // Hide loading
            try {
                btnLogin?.isEnabled = true
            } catch (_: Exception) {
                // ignore
            }

            result.onSuccess { response ->
                try {
                    if (response.success && response.user != null) {
                        val user = response.user
                        try {
                            preferenceManager.saveLoginData(
                                userId = user.id,
                                username = user.username,
                                displayName = user.displayName,
                                email = user.email,
                                token = response.token
                            )

                            preferenceManager.setHasSeenOnboarding(true)

                            try {
                                sessionTaskManager.onLoginSuccess(user.id)
                            } catch (t: Throwable) {
                                Log.w("LoginFragment", "SessionTaskManager.onLoginSuccess failed", t)
                            }

                            // Migrate guest tasks to real user, then best-effort syncUp
                            try {
                                withContext(Dispatchers.IO) {
                                    sessionTaskManager.migrateGuestData(user.id)
                                    runCatching { taskSyncRepository.syncUp() }
                                        .onFailure { Log.w("LoginFragment", "syncUp after migration failed", it) }
                                }
                            } catch (t: Throwable) {
                                Log.w("LoginFragment", "Guest migration block failed", t)
                            }

                            try {
                                withContext(Dispatchers.IO) {
                                    NotificationRepository(requireContext()).syncFromServer(showDeviceNotifications = false)
                                }
                            } catch (t: Throwable) {
                                Log.w("LoginFragment", "Auto notifications sync after login failed", t)
                            }

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
                            Toast.makeText(requireContext(), "Login successful", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                        }

                        if (isAdded) {
                            findNavController().navigate(R.id.action_login_to_home)
                        }
                    } else {
                        try {
                            Toast.makeText(requireContext(), "Username or password is incorrect. Please try again.", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            // ignore
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LoginFragment", "Error handling login result", e)
                    try {
                        Toast.makeText(requireContext(), "Error processing login data", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }.onFailure { error ->
                Log.e("LoginFragment", "Login failed", error)
                try {
                    val errorMessage = when {
                        error.message?.contains("Unauthorized", ignoreCase = true) == true ||
                        error.message?.contains("401", ignoreCase = true) == true ||
                        error.message?.contains("Invalid credentials", ignoreCase = true) == true ->
                            "Username or password is incorrect. Please try again."
                        else -> "Error: ${error.message ?: "Cannot connect to server"}"
                    }
                    Toast.makeText(
                        requireContext(),
                        errorMessage,
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        hideBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        hideBottomNavigation()
        view?.findViewById<MotionLayout?>(R.id.login_motion_layout)?.transitionToEnd()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // ✅ Hiện lại bottom navigation khi thoát nếu đã đăng nhập
        showBottomNavigationIfLoggedIn()
    }

    private fun hideBottomNavigation() {
        try {
            val bottomNav = activity?.findViewById<View>(R.id.bottom_nav_view)
            bottomNav?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("LoginFragment", "Error hiding bottom navigation", e)
        }
    }

    private fun showBottomNavigationIfLoggedIn() {
        try {
            val prefs = PreferenceManager(requireContext())
            val tokenStorage = com.example.morp_prj.security.SecureTokenStorage(requireContext())
            val shouldShow = prefs.isLoggedIn() || tokenStorage.hasValidRefreshToken()
            val bottomNav = activity?.findViewById<View>(R.id.bottom_nav_view)
            bottomNav?.visibility = if (shouldShow) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            Log.e("LoginFragment", "Error showing bottom navigation", e)
        }
    }
}
