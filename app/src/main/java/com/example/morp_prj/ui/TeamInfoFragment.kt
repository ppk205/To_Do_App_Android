package com.example.morp_prj.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.data.model.TeamMember
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamInfoFragment : Fragment(R.layout.fragment_team_info) {

    private var teamId: String = ""
    private var teamName: String = ""
    private var inviteCode: String = ""

    private lateinit var imgTeamAvatar: ImageView
    private lateinit var tvTeamName: TextView
    private lateinit var tvTeamDesc: TextView
    private lateinit var tvInviteCode: TextView
    private lateinit var btnMenu : ImageView
    private lateinit var rvManagers: RecyclerView
    private lateinit var managerAdapter: ManagerMiniAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            teamId = it.getString("teamId", "")
            teamName = it.getString("teamName", "")
            inviteCode = it.getString("inviteCode", "")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRecycler()
        openMenu(view)
        setupCopyInvite(view)
        loadTeamInfo()
        loadManagers()
    }

    private fun initViews(view: View) {
        imgTeamAvatar = view.findViewById(R.id.imgTeamAvatar)
        tvTeamName = view.findViewById(R.id.tvTeamName)
        tvTeamDesc = view.findViewById(R.id.tvTeamDesc)
        btnMenu = view.findViewById(R.id.btnMenu)
        tvInviteCode = view.findViewById(R.id.tvInviteCode)
        rvManagers = view.findViewById(R.id.rvManagers)
    }

    private fun setupRecycler() {
        managerAdapter = ManagerMiniAdapter { member ->
            // Navigate to member profile
            val args = Bundle().apply {
                putString("userId", member.id)
                putBoolean("readOnly", true)
                putString("displayName", member.displayName)
                putString("email", member.email)
                putString("avatarUrl", member.avatarUrl)
                putString("role", member.role)
            }
            findNavController().navigate(R.id.profileFragment, args)
        }
        rvManagers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = managerAdapter
        }
    }

    private fun openMenu(root: View) {
        root.findViewById<View>(R.id.btnMenu)?.setOnClickListener {
            requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)?.openDrawer(GravityCompat.END)
        }
    }

    private fun setupCopyInvite(root: View) {
        root.findViewById<View>(R.id.tvInviteCode)?.setOnClickListener {
            val text = tvInviteCode.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                val clip = ClipData.newPlainText("Invite Code", text)
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTeamInfo() {
        if (teamId.isEmpty()) return
        RetrofitClient.teamApiService.getTeamDetail(teamId).enqueue(object : Callback<Team> {
            override fun onResponse(call: Call<Team>, response: Response<Team>) {
                if (response.isSuccessful) {
                    val team = response.body()
                    tvTeamName.text = team?.name ?: teamName
                    tvTeamDesc.text = team?.description ?: ""
                    tvInviteCode.text = "INVITE: ${team?.inviteCode ?: inviteCode}"
                    Glide.with(requireContext())
                        .load(team?.avatarUrl)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .into(imgTeamAvatar)
                }
            }
            override fun onFailure(call: Call<Team>, t: Throwable) {
                Toast.makeText(requireContext(), "Failed to load team info", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadManagers() {
        if (teamId.isEmpty()) return
        RetrofitClient.teamApiService.getTeamMembers(teamId, "active").enqueue(object : Callback<List<TeamMember>> {
            override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                if (response.isSuccessful) {
                    val managers = (response.body() ?: emptyList()).filter { it.role.equals("manager", true) || it.role.equals("co-manager", true) }
                    managerAdapter.submit(managers)
                }
            }
            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
                Toast.makeText(requireContext(), "Failed to load members", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

private class ManagerMiniAdapter(
    private val onManagerClick: (TeamMember) -> Unit
) : RecyclerView.Adapter<ManagerMiniAdapter.VH>() {
    private val items = mutableListOf<TeamMember>()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: ImageView = view.findViewById(R.id.imgAvatar)
        private val name: TextView = view.findViewById(R.id.tvName)
        private val email: TextView = view.findViewById(R.id.tvEmail)
        private val role: TextView = view.findViewById(R.id.tvRole)
        fun bind(item: TeamMember) {
            name.text = item.displayName
            role.text = item.role.replaceFirstChar { it.uppercase() }
            email.text = item.email
            val fullUrl = RetrofitClient.buildFullUrl(item.avatarUrl) ?: item.avatarUrl
            Glide.with(itemView.context)
                .load(fullUrl)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(avatar)

            itemView.setOnClickListener { onManagerClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_member_mini, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    fun submit(data: List<TeamMember>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }
}
