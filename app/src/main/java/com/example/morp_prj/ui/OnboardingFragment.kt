package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.google.android.material.button.MaterialButton

class OnboardingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding, container, false)

        val btnGetStarted = view.findViewById<MaterialButton>(R.id.btn_get_started)
        val txtGuest = view.findViewById<View>(R.id.btn_guest_mode)

        btnGetStarted.setOnClickListener {
            // Navigate to main home (use existing nav id menu_home)
            findNavController().navigate(R.id.menu_home)
        }

        txtGuest.setOnClickListener {
            // For guest mode, navigate to home as well. If you later want to pass an arg,
            // add an action/arg in navigation graph and use it here.
            findNavController().navigate(R.id.menu_home)
        }

        return view
    }
}

