package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.PinTeamRequest
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.utils.PreferenceManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamFragment : Fragment() {

    private lateinit var pinnedTeamAdapter: TeamAdapter
    private lateinit var myTeamAdapter: TeamAdapter
    private lateinit var preferenceManager: PreferenceManager
    private var isGuestDialogShowing = false

    private var allTeams = mutableListOf<Team>()

    // Thêm tham chiếu đến TextViews
    private lateinit var tvPinnedTitle: TextView
    private lateinit var tvMyTeamsTitle: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_team, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        setupUI(view)
    }

    override fun onResume() {
        super.onResume()
        checkGuestModeAndLoadData()
    }

    private fun setupUI(view: View) {
        // Khởi tạo TextViews
        tvPinnedTitle = view.findViewById(R.id.tvPinnedTitle)
        tvMyTeamsTitle = view.findViewById(R.id.tvMyTeamsTitle)

        // User Info
        val tvUserName = view.findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail = view.findViewById<TextView>(R.id.tvUserEmail)
        tvUserName.text = preferenceManager.getDisplayName()?.takeIf { it.isNotEmpty() } ?: preferenceManager.getUsername() ?: "User"
        tvUserEmail.text = preferenceManager.getEmail() ?: "No Email"

        // RecyclerViews
        val rvPinnedTeams = view.findViewById<RecyclerView>(R.id.rvPinnedTeams)
        rvPinnedTeams.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        pinnedTeamAdapter = TeamAdapter(emptyList(), R.layout.item_pinned_team, onItemClick = { navigateToDetail(it) }, onItemLongClick = { showPinDialog(it) })
        rvPinnedTeams.adapter = pinnedTeamAdapter

        val rvMyTeams = view.findViewById<RecyclerView>(R.id.rvMyTeams)
        rvMyTeams.layoutManager = LinearLayoutManager(context)
        myTeamAdapter = TeamAdapter(emptyList(), R.layout.item_team, onItemClick = { navigateToDetail(it) }, onItemLongClick = { showPinDialog(it) })
        rvMyTeams.adapter = myTeamAdapter
        
        // Buttons
        view.findViewById<View>(R.id.btnCreateTeam).setOnClickListener { findNavController().navigate(R.id.create_new_team_fragment) }
        view.findViewById<View>(R.id.fabAdd).setOnClickListener { findNavController().navigate(R.id.create_new_team_fragment) }
        
        // Listener
        parentFragmentManager.setFragmentResultListener("team_created", viewLifecycleOwner) { _, _ ->
            loadMyTeams()
        }
    }

    private fun checkGuestModeAndLoadData() {
        if (preferenceManager.isGuest()) {
            if (!isGuestDialogShowing) {
                isGuestDialogShowing = true
                AlertDialog.Builder(requireContext())
                    .setTitle("Restricted Access").setMessage("Please sign in to use Team Mode features.")
                    .setPositiveButton("Sign In") { d, _ -> d.dismiss(); findNavController().navigate(R.id.login_fragment) }
                    .setNegativeButton("Cancel") { d, _ -> d.dismiss(); if (!findNavController().popBackStack()) findNavController().navigate(R.id.menu_home) }
                    .setOnDismissListener { isGuestDialogShowing = false }.setCancelable(false).show()
            }
        } else {
            loadMyTeams()
        }
    }

    private fun loadMyTeams() {
        val userId = preferenceManager.getUserId() ?: return

        RetrofitClient.teamApiService.getMyTeams(userId).enqueue(object : Callback<List<Team>> {
            override fun onResponse(call: Call<List<Team>>, response: Response<List<Team>>) {
                if (response.isSuccessful) {
                    allTeams = (response.body() ?: emptyList()).toMutableList()
                    updateTeamLists()
                } else {
                    Log.e("TeamFragment", "API Error: ${response.errorBody()?.string()}")
                }
            }
            override fun onFailure(call: Call<List<Team>>, t: Throwable) {
                Log.e("TeamFragment", "API Failure: ${t.message}", t)
            }
        })
    }

    private fun updateTeamLists() {
        val pinned = allTeams.filter { it.isPinned }

        // Cập nhật tiêu đề với số lượng team
        tvPinnedTitle.text = "Pinned Teams (${pinned.size})"
        tvMyTeamsTitle.text = "My Teams (${allTeams.size})"

        pinnedTeamAdapter.updateData(pinned)
        myTeamAdapter.updateData(allTeams)
    }

    private fun showPinDialog(team: Team) {
        val newPinStatus = !team.isPinned
        val title = if (newPinStatus) "Pin Team" else "Unpin Team"
        val message = "Are you sure you want to ${if (newPinStatus) "pin" else "unpin"} this team?"

        AlertDialog.Builder(requireContext()).setTitle(title).setMessage(message)
            .setPositiveButton("Yes") { _, _ -> togglePinStatus(team, newPinStatus) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun togglePinStatus(team: Team, newPinStatus: Boolean) {
        val userId = preferenceManager.getUserId() ?: return
        val request = PinTeamRequest(userId, team.id, newPinStatus)

        RetrofitClient.teamApiService.togglePinTeam(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    val teamInList = allTeams.find { it.id == team.id }
                    teamInList?.isPinned = newPinStatus
                    updateTeamLists()
                    Toast.makeText(context, "Team pin status updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to update pin status", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun navigateToDetail(team: Team) {
        val bundle = Bundle().apply {
            putString("teamId", team.id)
            putString("teamName", team.name)
            putString("role", team.role ?: "member")
        }
        findNavController().navigate(R.id.action_team_to_detail, bundle)
    }
}