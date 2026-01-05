package com.example.morp_prj.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
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
import com.example.morp_prj.data.model.HandleJoinRequest
import com.example.morp_prj.data.model.RemoveMemberRequest
import com.example.morp_prj.data.model.TeamMember
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.tabs.TabLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

class MemberManagementFragment : Fragment(R.layout.fragment_member_management) {

    private var teamId: String? = null
    private var inviteCode: String? = null
    private var currentUserRole: String = "member"

    private lateinit var memberAdapter: MemberAdapter
    private lateinit var joinRequestAdapter: JoinRequestAdapter
    private lateinit var preferenceManager: PreferenceManager

    private var allMembersList = listOf<TeamMember>()
    private var allRequestsList = listOf<TeamMember>()

    // UI Components
    private lateinit var rvMembers: RecyclerView
    private lateinit var rvRequests: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var etSearch: EditText
    private lateinit var tabLayout: TabLayout

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
        initViews(view)
        setupEvents(view)
        setupRecyclerViews()

        loadMembers()
    }

    private fun initViews(view: View) {
        rvMembers = view.findViewById(R.id.rvMembers)
        rvRequests = view.findViewById(R.id.rvRequests)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        etSearch = view.findViewById(R.id.etSearch)
        tabLayout = view.findViewById(R.id.tabLayout)
    }

    private fun setupEvents(view: View) {
        view.findViewById<ImageButton>(R.id.btnAddMember)?.setOnClickListener {
            showInviteDialog()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                handleTabChange(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterData(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<ImageButton>(R.id.btnMore)?.setOnClickListener {
            val drawerLayout = requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)

            if (drawerLayout != null) {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                } else {
                    drawerLayout.openDrawer(GravityCompat.END)
                }

            } else {
                Log.e("MemberFragment", "DrawerLayout not found in parent Activity")
            }
        }
    }

    private fun setupRecyclerViews() {
        rvMembers.layoutManager = LinearLayoutManager(context)
        memberAdapter = MemberAdapter(emptyList()) { member, anchorView ->
            showMemberOptions(member, anchorView)
        }
        rvMembers.adapter = memberAdapter

        rvRequests.layoutManager = LinearLayoutManager(context)
        joinRequestAdapter = JoinRequestAdapter(
            emptyList(),
            onApprove = { request -> handleJoinRequest(request, "approve") },
            onReject = { request -> handleJoinRequest(request, "reject") }
        )
        rvRequests.adapter = joinRequestAdapter
    }

    private fun handleTabChange(position: Int) {
        etSearch.setText("")

        if (position == 0) {
            // Tab Members
            rvMembers.visibility = View.VISIBLE
            rvRequests.visibility = View.GONE
            loadMembers()
        } else {
            // Tab Requests
            rvMembers.visibility = View.GONE
            rvRequests.visibility = View.VISIBLE
            loadRequests()
        }
    }

    private fun loadMembers() {
        val currentTeamId = teamId ?: return

        RetrofitClient.teamApiService.getTeamMembers(currentTeamId).enqueue(object : Callback<List<TeamMember>> {
            override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                if (response.isSuccessful) {
                    allMembersList = response.body() ?: emptyList()
                    filterData(etSearch.text.toString()) // Update UI qua filter
                } else {
                    Log.e("MemberManagement", "Failed to load members: ${response.code()}")
                }
            }
            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
                Log.e("MemberManagement", "Error loading members", t)
            }
        })
    }

    private fun loadRequests() {
        val currentTeamId = teamId ?: return

        RetrofitClient.teamApiService.getTeamMembers(currentTeamId, status = "pending").enqueue(object : Callback<List<TeamMember>> {
            override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                if (response.isSuccessful) {
                    allRequestsList = response.body() ?: emptyList()
                    filterData(etSearch.text.toString()) // Update UI qua filter
                } else {
                    Log.e("MemberManagement", "Failed to load requests: ${response.code()}")
                }
            }
            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
                Log.e("MemberManagement", "Error loading requests", t)
            }
        })
    }

    private fun filterData(query: String) {
        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        val isMembersTab = tabLayout.selectedTabPosition == 0

        if (isMembersTab) {
            val filtered = if (query.isEmpty()) {
                allMembersList
            } else {
                allMembersList.filter {
                    (it.displayName ?: "").lowercase().contains(lowerCaseQuery) ||
                            (it.email ?: "").lowercase().contains(lowerCaseQuery)
                }
            }
            memberAdapter.updateData(filtered)
            updateEmptyState(filtered.isEmpty(), "No members found")
        } else {
            val filtered = if (query.isEmpty()) {
                allRequestsList
            } else {
                allRequestsList.filter {
                    (it.displayName ?: "").lowercase().contains(lowerCaseQuery)
                }
            }
            joinRequestAdapter.updateData(filtered)
            updateEmptyState(filtered.isEmpty(), "No pending requests")
        }
    }

    private fun updateEmptyState(isEmpty: Boolean, message: String) {
        if (isEmpty) {
            tvEmptyState.visibility = View.VISIBLE
            tvEmptyState.text = message
        } else {
            tvEmptyState.visibility = View.GONE
        }
    }

    private fun handleJoinRequest(member: TeamMember, action: String) {
        val currentTeamId = teamId ?: return
        val requestBody = HandleJoinRequest(
            teamId = currentTeamId,
            userId = member.id,
            action = action
        )

        RetrofitClient.teamApiService.handleJoinRequest(requestBody).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    val msg = if (action == "approve") "Member approved" else "Request rejected"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    loadRequests()
                } else {
                    Toast.makeText(context, "Action failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showInviteDialog() {
        if (inviteCode.isNullOrEmpty()) {
            Toast.makeText(context, "No invite code available", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_invite_code_display, null)
        dialogView.findViewById<TextView>(R.id.tvInviteCode).text = inviteCode

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnCopy)?.setOnClickListener {
            copyToClipboard(inviteCode!!)
        }

        dialogView.findViewById<View>(R.id.btnGoToTeam)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Invite Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    private fun showMemberOptions(member: TeamMember, anchorView: View) {
        val popup = PopupMenu(context, anchorView)
        popup.menuInflater.inflate(R.menu.member_options_menu, popup.menu)

        val canManage = currentUserRole.equals("manager", ignoreCase = true) ||
                currentUserRole.equals("co-manager", ignoreCase = true)

        if (!canManage || member.id == preferenceManager.getUserId()) {
            popup.menu.findItem(R.id.action_remove_member)?.isVisible = false
            popup.menu.findItem(R.id.action_change_role)?.isVisible = false
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
                    Toast.makeText(context, "${member.displayName} removed", Toast.LENGTH_SHORT).show()
                    loadMembers()
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