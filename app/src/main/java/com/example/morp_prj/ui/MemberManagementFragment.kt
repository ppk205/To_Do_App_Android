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
                    val members = response.body() ?: emptyList()
                    memberAdapter.updateData(members)
                } else {
                    Log.e("MemberManagement", "Failed to load members: ${response.code()}")
                    Toast.makeText(context, "Failed to load members", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
                Log.e("MemberManagement", "Error loading members", t)
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showInviteDialog() {
        if (inviteCode.isNullOrEmpty()) {
            Toast.makeText(context, "Invite code is not available.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_invite_code_display, null)
        val tvInviteCode = dialogView.findViewById<TextView>(R.id.tvInviteCode)
        tvInviteCode.text = inviteCode

        val message = "Share this code with others to invite them to your team."
        
        AlertDialog.Builder(requireContext())
            .setTitle("Invite Members")
            .setMessage(message)
            .setView(dialogView)
            .setPositiveButton("Copy Code") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Team Invite Code", inviteCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Invite code copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
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
                R.id.action_view_profile -> {
                    Toast.makeText(context, "View profile of ${member.displayName}", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_remove_member -> {
                    Toast.makeText(context, "Remove ${member.displayName}", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_change_role -> {
                    Toast.makeText(context, "Change role of ${member.displayName}", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}