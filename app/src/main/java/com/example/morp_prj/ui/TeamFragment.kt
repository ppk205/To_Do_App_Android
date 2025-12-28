package com.example.morp_prj.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.utils.PreferenceManager

class TeamFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_team, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if user is in guest mode
        val prefs = PreferenceManager(requireContext())
        if (prefs.isGuest()) {
            // Navigate to guest prompt instead of showing team content
            findNavController().navigate(R.id.action_team_to_guestPrompt)
        }
    }
}