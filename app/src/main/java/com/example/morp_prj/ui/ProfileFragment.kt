package com.example.morp_prj.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.security.SecureTokenStorage
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnLogout = view.findViewById<MaterialButton>(R.id.btn_logout)
        btnLogout.setOnClickListener {
            try {
                // Clear plain prefs login data
                val pref = PreferenceManager(requireContext())
                pref.clearLoginData()

                // Clear secure tokens if used
                val tokenStorage = SecureTokenStorage(requireContext())
                tokenStorage.clearTokens()

                // Navigate back to login and clear back stack up to onboarding
                val navController = findNavController()
                navController.navigate(R.id.login_fragment) {
                    popUpTo(R.id.onboarding_fragment) { inclusive = false }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Logout failed", e)
            }
        }
    }
}