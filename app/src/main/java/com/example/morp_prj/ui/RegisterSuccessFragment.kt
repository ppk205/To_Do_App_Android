package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.google.android.material.button.MaterialButton

class RegisterSuccessFragment : Fragment(R.layout.fragment_register_success) {

    private var username: String? = null
    private var email: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get arguments
        username = arguments?.getString("username")
        email = arguments?.getString("email")

        initViews(view)
    }

    private fun initViews(view: View) {
        val txtUsername = view.findViewById<TextView>(R.id.txt_username)
        val txtEmail = view.findViewById<TextView>(R.id.txt_email)
        val btnContinueLogin = view.findViewById<MaterialButton>(R.id.btn_continue_login)

        // Set user info
        txtUsername.text = username ?: ""
        txtEmail.text = email ?: ""

        // Navigate to login when button clicked
        btnContinueLogin.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_registerSuccess_to_login)
            } catch (e: Exception) {
                android.util.Log.e("RegisterSuccessFragment", "Navigation error", e)
                // Fallback: pop all and go to login
                findNavController().popBackStack(R.id.onboarding_fragment, false)
                findNavController().navigate(R.id.action_onboarding_to_login)
            }
        }
    }
}

