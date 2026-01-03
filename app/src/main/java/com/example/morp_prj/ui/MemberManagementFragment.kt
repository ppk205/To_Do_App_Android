package com.example.morp_prj.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
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
            findNavController().navigateUp()
        }

        view.findViewById<ImageButton>(R.id.btnAddMember)?.setOnClickListener {
            showInviteDialog()
        }

        view.findViewById<ImageView>(R.id.btnCopy)?.setOnClickListener {
            inviteCode?.let { code -> copyToClipboard(code) }
        }

        val rvMembers = view.findViewById<RecyclerView>(R.id.rvMembers)
        rvMembers.layoutManager = LinearLayoutManager(context)

        // Adapter callback nhận 2 tham số: member và view (nút 3 chấm)
        memberAdapter = MemberAdapter(emptyList()) { member, anchorView ->
            showMemberOptions(member, anchorView)
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
                    memberAdapter.updateData(membersList) // Gọi hàm updateData mới thêm vào Adapter
                } else {
                    Log.e("MemberManagement", "Failed to load members: ${response.code()}")
                }
            }
            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
                Log.e("MemberManagement", "Error loading members", t)
            }
        })
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Invite Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
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
            .setPositiveButton("Close", null)
            .create()

        dialogView.findViewById<View>(R.id.btnCopy)?.setOnClickListener {
            copyToClipboard(inviteCode!!)
        }

        dialogView.findViewById<View>(R.id.btnGoToTeam)?.setOnClickListener {
            dialog.dismiss()
            findNavController().navigateUp()
        }

        dialog.show()
    }

    private fun showMemberOptions(member: TeamMember, anchorView: View) {
        // anchorView chính là cái nút 3 chấm mà người dùng vừa bấm
        val popup = PopupMenu(context, anchorView)
        popup.menuInflater.inflate(R.menu.member_options_menu, popup.menu)

        // Logic check quyền
        // Chỉ hiện nút Remove nếu user hiện tại là manager
        // Và không được xóa chính mình
        val canManage = currentUserRole.equals("owner", ignoreCase = true) ||
                currentUserRole.equals("admin", ignoreCase = true) ||
                currentUserRole.equals("manager", ignoreCase = true)

        if (!canManage) {
            popup.menu.findItem(R.id.action_remove_member)?.isVisible = false
            popup.menu.findItem(R.id.action_change_role)?.isVisible = false
        }

        if (member.id == preferenceManager.getUserId()) {
            popup.menu.findItem(R.id.action_remove_member)?.isVisible = false
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