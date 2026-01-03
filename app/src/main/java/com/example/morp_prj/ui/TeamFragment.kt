package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.JoinTeamRequest
import com.example.morp_prj.data.model.PinTeamRequest
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamFragment : Fragment() {

    private lateinit var myTeamAdapter: TeamAdapter
    private lateinit var headerAdapter: TeamScreenHeaderAdapter
    private lateinit var concatAdapter: ConcatAdapter

    private lateinit var preferenceManager: PreferenceManager
    private var isGuestDialogShowing = false

    private var allTeams = mutableListOf<Team>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_team, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Chặn back press - không cho người dùng quay lại màn hình trước login
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Không làm gì - chặn back press hoàn toàn
            }
        })

        preferenceManager = PreferenceManager(requireContext())

        setupUI(view)
    }

    override fun onResume() {
        super.onResume()
        checkGuestModeAndLoadData()
    }

    private fun setupUI(view: View) {
        // set up Adapter for My Teams rv
        myTeamAdapter = TeamAdapter(emptyList(), R.layout.item_team,
            onItemClick = { navigateToDetail(it) },
            onItemLongClick = { showPinDialog(it) }
        )

        // Set up Header Adapter (User Info + Pinned Teams)
        headerAdapter = TeamScreenHeaderAdapter(
            preferenceManager,
            emptyList(),
            onPinnedTeamClick = { navigateToDetail(it) },
            onPinnedTeamLongClick = { showPinDialog(it) }
        )

        //Nối 2 Adapter lại bằng ConcatAdapter
        concatAdapter = ConcatAdapter(headerAdapter, myTeamAdapter)

        // RecyclerView chính (rvMain)
        val rvMain = view.findViewById<RecyclerView>(R.id.rvMain)
        rvMain.layoutManager = LinearLayoutManager(context)
        rvMain.adapter = concatAdapter

        // Floating Action Button
        val fabAddTeam = view.findViewById<FloatingActionButton>(R.id.fabAddTeam)
        fabAddTeam.setOnClickListener {
            showAddTeamBottomSheet()
        }

        parentFragmentManager.setFragmentResultListener("team_created", viewLifecycleOwner) { _, _ ->
            loadMyTeams()
        }
    }

    private fun updateTeamLists() {
        val pinned = allTeams.filter { it.isPinned }

        headerAdapter = TeamScreenHeaderAdapter(
            preferenceManager,
            pinned,
            onPinnedTeamClick = { navigateToDetail(it) },
            onPinnedTeamLongClick = { showPinDialog(it) }
        )

        myTeamAdapter.updateData(allTeams)

        concatAdapter = ConcatAdapter(headerAdapter, myTeamAdapter)
        view?.findViewById<RecyclerView>(R.id.rvMain)?.adapter = concatAdapter
    }

    private fun showAddTeamBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_team_options, null)
        bottomSheetDialog.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btnOptionCreate).setOnClickListener {
            bottomSheetDialog.dismiss()
            findNavController().navigate(R.id.create_new_team_fragment)
        }

        sheetView.findViewById<View>(R.id.btnOptionJoin).setOnClickListener {
            bottomSheetDialog.dismiss()
            showJoinTeamDialog()
        }

        bottomSheetDialog.show()
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

    private fun showJoinTeamDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_join_team, null)
        val etInviteCode = dialogView.findViewById<EditText>(R.id.etInviteCode)

        AlertDialog.Builder(requireContext())
            .setTitle("Join a Team")
            .setView(dialogView)
            .setPositiveButton("Join") { _, _ ->
                val code = etInviteCode.text.toString().trim()
                if (code.isNotEmpty()) {
                    performJoinTeam(code)
                } else {
                    Toast.makeText(context, "Please enter a code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performJoinTeam(inviteCode: String) {
        val userId = preferenceManager.getUserId() ?: return
        val request = JoinTeamRequest(userId, inviteCode)

        RetrofitClient.teamApiService.joinTeam(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Successfully joined team!", Toast.LENGTH_SHORT).show()
                    loadMyTeams()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Failed to join team"
                    Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Connection Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
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
            putString("inviteCode", team.inviteCode ?: "")
        }
        findNavController().navigate(R.id.action_team_to_detail, bundle)
    }
}