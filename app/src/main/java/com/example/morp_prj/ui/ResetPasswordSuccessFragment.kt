package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.google.android.material.button.MaterialButton

class ResetPasswordSuccessFragment : Fragment(R.layout.fragment_reset_password_success) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Ẩn bottom navigation bar
        hideBottomNavigation()

        initViews(view)
    }

    private fun initViews(view: View) {
        val btnContinueLogin = view.findViewById<MaterialButton>(R.id.btn_continue_login_reset)

        btnContinueLogin.setOnClickListener {
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

    override fun onDestroyView() {
        super.onDestroyView()

        // ✅ Hiện lại bottom navigation khi thoát
        showBottomNavigation()
    }

    private fun hideBottomNavigation() {
        try {
            val bottomNav = activity?.findViewById<View>(R.id.bottom_nav_view)
            bottomNav?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("ResetPasswordSuccessFragment", "Error hiding bottom navigation", e)
        }
    }

    private fun showBottomNavigation() {
        try {
            val bottomNav = activity?.findViewById<View>(R.id.bottom_nav_view)
            bottomNav?.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("ResetPasswordSuccessFragment", "Error showing bottom navigation", e)
        }
    }
}
