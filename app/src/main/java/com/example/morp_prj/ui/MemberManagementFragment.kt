package com.example.morp_prj.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.RemoveMemberRequest
import com.example.morp_prj.data.model.TeamMember
import com.example.morp_prj.utils.PreferenceManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MemberManagementFragment : Fragment(R.layout.fragment_member_management) {

    private var teamId: String? = null
    private var inviteCode: String? = null
    private var currentUserRole: String = "member"

    private lateinit var memberAdapter: MemberAdapter
    private lateinit var preferenceManager: PreferenceManager
    private var membersList = mutableListOf<TeamMember>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            teamId = it.getString("teamId")
            inviteCode = it.getString("inviteCode")
            currentUserRole = it.getString("role", "member")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        view.findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            findNavController().navigate(R.id.teamDashboardFragment)
        }

        view.findViewById<ImageButton>(R.id.btnAddMember)?.setOnClickListener {
            showInviteDialog()
        }

        val rvMembers = view.findViewById<RecyclerView>(R.id.rvMembers)
        rvMembers.layoutManager = LinearLayoutManager(context)
        memberAdapter = MemberAdapter(emptyList()) { member ->
            val position = memberAdapter.members.indexOf(member)
            if (position != -1) {
                val holder = rvMembers.findViewHolderForAdapterPosition(position)
                holder?.itemView?.let { showMemberOptions(member, it) }
            }
        }
        rvMembers.adapter = memberAdapter

        loadMembers()
    }

    private fun loadMembers() {
        val currentTeamId = teamId ?: return
        
        RetrofitClient.teamApiService.getTeamMembers(currentTeamId).enqueue(object : Callback<List<TeamMember>> {
            override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                if (response.isSuccessful) {
                    membersList = (response.body() ?: emptyList()).toMutableList()
                    memberAdapter.updateData(membersList)
                } else {
                    Log.e("MemberManagement", "Failed to load members: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
                Log.e("MemberManagement", "Error loading members", t)
            }
        })
    }

    private fun showInviteDialog() {
        // ... (giữ nguyên)
    }

    private fun showMemberOptions(member: TeamMember, anchorView: View) {
        val popup = PopupMenu(context, anchorView.findViewById(R.id.btnMore))
        popup.menuInflater.inflate(R.menu.member_options_menu, popup.menu)

        if (!currentUserRole.equals("manager", ignoreCase = true)) {
            popup.menu.findItem(R.id.action_remove_member).isVisible = false
            popup.menu.findItem(R.id.action_change_role).isVisible = false
        }

        if (member.id == preferenceManager.getUserId()) {
             popup.menu.findItem(R.id.action_remove_member).isVisible = false
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_remove_member -> {
                    confirmRemoveMember(member)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmRemoveMember(member: TeamMember) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Member")
            .setMessage("Are you sure you want to remove ${member.displayName} from the team?")
            .setPositiveButton("Remove") { _, _ ->
                performRemoveMember(member)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRemoveMember(member: TeamMember) {
        val currentTeamId = teamId ?: return
        val request = RemoveMemberRequest(teamId = currentTeamId, userId = member.id)

        RetrofitClient.teamApiService.removeMember(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "${member.displayName} has been removed.", Toast.LENGTH_SHORT).show()
                    // Cập nhật lại danh sách
                    membersList.remove(member)
                    memberAdapter.updateData(membersList)
                } else {
                    Toast.makeText(context, "Failed to remove member", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}