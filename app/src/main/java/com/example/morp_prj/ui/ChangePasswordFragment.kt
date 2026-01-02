package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.databinding.FragmentChangePasswordBinding
import com.example.morp_prj.data.repository.AuthRepository
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.launch

class ChangePasswordFragment : Fragment() {

    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PreferenceManager
    private lateinit var authRepository: AuthRepository
    private var userId: String? = null
    private var failCount: Int = 0

    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangePasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = PreferenceManager(requireContext())
        authRepository = AuthRepository(requireContext())
        userId = prefs.getUserId()

        // Check if user is guest
        if (userId == null || prefs.isGuest()) {
            Toast.makeText(context, "Please login to change password", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        // Load fail count from persistent storage
        failCount = prefs.getPasswordFailCount()

        // Update UI state based on fail count
        updateUIState()

        setupListeners()
    }

    private fun setupListeners() {
        // Clear error on text change
        binding.etOldPassword.addTextChangedListener {
            binding.tilOldPassword.error = null
        }
        binding.etNewPassword.addTextChangedListener {
            binding.tilNewPassword.error = null
        }
        binding.etConfirmPassword.addTextChangedListener {
            binding.tilConfirmPassword.error = null
        }

        // Save button click
        binding.btnSave.setOnClickListener {
            handleSavePassword()
        }

        // Forgot password button
        binding.btnGoToForgot.setOnClickListener {
            // Navigate to forgot password flow
            findNavController().navigate(R.id.action_changePassword_to_forgotPassword)
        }

        // Back to profile button
        binding.btnBackToProfile.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun updateUIState() {
        when {
            // Fail count < 5: Normal mode
            failCount < 5 -> {
                binding.layoutLockout.visibility = View.GONE
                enableInputs(true)
                removeBackButtonInterception()
            }

            // Fail count 5-9: Soft lock - show lockout, but allow back
            failCount in 5..9 -> {
                binding.layoutLockout.visibility = View.VISIBLE
                binding.btnBackToProfile.visibility = View.VISIBLE
                binding.tvLockoutMessage.text = "Too many failed attempts ($failCount/10). Please reset your password or go back to profile."
                enableInputs(false)
                removeBackButtonInterception()
            }

            // Fail count >= 10: Hard lock - force forgot password
            failCount >= 10 -> {
                binding.layoutLockout.visibility = View.VISIBLE
                binding.btnBackToProfile.visibility = View.GONE
                binding.tvLockoutMessage.text = "Account locked due to too many failed attempts. You must reset your password."
                enableInputs(false)
                interceptBackButton()
            }
        }
    }

    private fun enableInputs(enabled: Boolean) {
        binding.etOldPassword.isEnabled = enabled
        binding.etNewPassword.isEnabled = enabled
        binding.etConfirmPassword.isEnabled = enabled
        binding.btnSave.isEnabled = enabled
    }

    private fun interceptBackButton() {
        // Remove existing callback if any
        backPressedCallback?.remove()

        // Create new callback that blocks back navigation
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    context,
                    "You must reset your password to continue",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback!!
        )
    }

    private fun removeBackButtonInterception() {
        backPressedCallback?.remove()
        backPressedCallback = null
    }

    private fun handleSavePassword() {
        // Clear previous errors
        binding.tilOldPassword.error = null
        binding.tilNewPassword.error = null
        binding.tilConfirmPassword.error = null

        // Get input values
        val oldPassword = binding.etOldPassword.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        // Validation
        if (oldPassword.isEmpty()) {
            binding.tilOldPassword.error = "Old password is required"
            return
        }

        if (newPassword.isEmpty()) {
            binding.tilNewPassword.error = "New password is required"
            return
        }

        if (newPassword.length < 8) {
            binding.tilNewPassword.error = "Password must be at least 8 characters"
            return
        }

        if (confirmPassword != newPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            return
        }

        if (oldPassword == newPassword) {
            binding.tilNewPassword.error = "New password must be different from old password"
            return
        }

        // Process password change via API
        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            binding.btnSave.text = "Saving..."

            try {
                val result = authRepository.changePassword(oldPassword, newPassword)

                result.onSuccess {
                    // Success: Reset fail count and navigate back
                    prefs.resetPasswordFailCount()
                    Toast.makeText(
                        context,
                        "Password changed successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().popBackStack()
                }.onFailure { error ->
                    // Check if error is due to incorrect old password
                    val errorMessage = error.message ?: "Change password failed"

                    if (errorMessage.contains("incorrect", ignoreCase = true) ||
                        errorMessage.contains("wrong", ignoreCase = true) ||
                        errorMessage.contains("old password", ignoreCase = true)
                    ) {
                        // Increment fail count for wrong old password
                        prefs.incrementPasswordFailCount()
                        failCount = prefs.getPasswordFailCount()
                        binding.tilOldPassword.error = "Incorrect old password (Attempt $failCount/10)"
                        updateUIState()
                    } else {
                        // Other errors (network, server, etc.)
                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Save"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up callback
        backPressedCallback?.remove()
        _binding = null
    }
}
