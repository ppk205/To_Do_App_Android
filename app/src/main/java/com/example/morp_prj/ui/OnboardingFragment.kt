package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.button.MaterialButton

class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btnGetStarted)?.setOnClickListener {
            findNavController().navigate(R.id.action_onboarding_to_login)
        }
        view.findViewById<View>(R.id.tvGuestMode)?.setOnClickListener {
            // Lưu trạng thái guest vào PreferenceManager
            val prefs = PreferenceManager(requireContext())
            prefs.saveGuestMode()
            findNavController().navigate(R.id.action_onboarding_to_home)
        }
    }
}