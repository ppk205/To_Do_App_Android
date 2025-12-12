package com.example.morp_prj.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.morp_prj.R
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.utils.PreferenceManager

class TeamFragment : Fragment() {

    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_team, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        // Nếu là guest, chuyển đến guest prompt
        if (preferenceManager.isGuest()) {
            findNavController().navigate(R.id.guestPromptFragment)
        }
    }
}