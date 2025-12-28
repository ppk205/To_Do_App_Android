package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.utils.PreferenceManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamFragment : Fragment() {

    private lateinit var pinnedTeamAdapter: TeamAdapter
    private lateinit var myTeamAdapter: TeamAdapter
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_team, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        // Setup User Info
        val tvUserName = view.findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail = view.findViewById<TextView>(R.id.tvUserEmail)
        
        val userName = preferenceManager.getDisplayName()?.takeIf { it.isNotEmpty() } 
            ?: preferenceManager.getUsername() 
            ?: "User"
        val userEmail = preferenceManager.getEmail() ?: "No Email"
        
        tvUserName.text = userName
        tvUserEmail.text = userEmail

        // Setup RecyclerViews
        val rvPinnedTeams = view.findViewById<RecyclerView>(R.id.rvPinnedTeams)
        rvPinnedTeams.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        pinnedTeamAdapter = TeamAdapter(emptyList(), R.layout.item_pinned_team)
        rvPinnedTeams.adapter = pinnedTeamAdapter

        val rvMyTeams = view.findViewById<RecyclerView>(R.id.rvMyTeams)
        rvMyTeams.layoutManager = LinearLayoutManager(context)
        myTeamAdapter = TeamAdapter(emptyList(), R.layout.item_team)
        rvMyTeams.adapter = myTeamAdapter
        
        // Setup Buttons
        val btnCreateTeam = view.findViewById<View>(R.id.btnCreateTeam)
        btnCreateTeam.setOnClickListener {
            findNavController().navigate(R.id.create_new_team_fragment)
        }
        
        val fabAdd = view.findViewById<View>(R.id.fabAdd)
        fabAdd.setOnClickListener {
             findNavController().navigate(R.id.create_new_team_fragment)
        }
        
        // Setup Listener
        parentFragmentManager.setFragmentResultListener("team_created", viewLifecycleOwner) { _, _ ->
            loadMyTeams()
        }

        // Load data
        loadMyTeams()
        // loadPinnedTeams() // Tạm ẩn vì chưa làm backend
    }

    private fun loadMyTeams() {
        val userId = preferenceManager.getUserId()
        
        if (userId.isNullOrEmpty()) {
            Log.e("TeamFragment", "User not logged in, cannot load teams")
            return
        }

        val call = RetrofitClient.teamApiService.getMyTeams(userId)
        
        Log.d("TeamFragment", "Requesting URL: ${call.request().url()}")

        call.enqueue(object : Callback<List<Team>> {
            override fun onResponse(call: Call<List<Team>>, response: Response<List<Team>>) {
                if (response.isSuccessful) {
                    val teams = response.body() ?: emptyList()
                    myTeamAdapter.updateData(teams)
                    
                    // Nếu muốn hiển thị tạm pinned teams từ danh sách này (ví dụ 3 team đầu tiên)
                    // pinnedTeamAdapter.updateData(teams.take(3))
                    
                    Log.d("TeamFragment", "Success: Loaded ${teams.size} teams")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("TeamFragment", "Error ${response.code()}: $errorBody")
                }
            }

            override fun onFailure(call: Call<List<Team>>, t: Throwable) {
                Log.e("TeamFragment", "Connection failed", t)
            }
        })
    }
}