package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.morp_prj.R

class TeamTasksFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_team_tasks, container, false)
    }
    
    // nhận teamId ở đây từ arguments để load task của team đó
    // val teamId = arguments?.getString("teamId")
}