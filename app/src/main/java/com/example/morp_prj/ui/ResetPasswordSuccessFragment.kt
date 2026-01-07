package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.google.android.material.button.MaterialButton

class ResetPasswordSuccessFragment : Fragment(R.layout.fragment_reset_password_success) {

    override fun onStart() {
        super.onStart()
        hideBottomNavigation()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Ẩn bottom navigation bar
        hideBottomNavigation()

        initViews(view)
    }

    private fun initViews(view: View) {
        val btnContinueLogin = view.findViewById<MaterialButton>(R.id.btn_continue_login_reset)

        btnContinueLogin.setOnClickListener {
            hideBottomNavigation()
            try {
                findNavController().navigate(R.id.action_resetSuccess_to_login)
            } catch (e: Exception) {
                android.util.Log.e("ResetPasswordSuccess", "Navigation error", e)
                // fallback: go to login via popBackStack
                findNavController().popBackStack(R.id.onboarding_fragment, false)
                findNavController().navigate(R.id.action_onboarding_to_login)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideBottomNavigation()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // ✅ Chỉ hiện lại nếu user đã đăng nhập
        showBottomNavigationIfLoggedIn()
    }

    private fun hideBottomNavigation() {
        try {
            val bottomNav = activity?.findViewById<View>(R.id.bottom_nav_view)
            bottomNav?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("ResetPasswordSuccessFragment", "Error hiding bottom navigation", e)
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
            Log.e("ResetPasswordSuccessFragment", "Error showing bottom navigation", e)
        }
    }
}
