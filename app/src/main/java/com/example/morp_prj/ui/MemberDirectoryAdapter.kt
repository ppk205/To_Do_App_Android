package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamMember

class MemberDirectoryAdapter(
    private val onMemberClick: (TeamMember) -> Unit
) : RecyclerView.Adapter<MemberDirectoryAdapter.ViewHolder>() {

    private var members: List<TeamMember> = emptyList()

    fun submitList(newList: List<TeamMember>) {
        members = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member_directory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(members[position])
    }

    override fun getItemCount() = members.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvEmail: TextView = view.findViewById(R.id.tvEmail)
        private val tvRole: TextView = view.findViewById(R.id.tvRole)

        fun bind(member: TeamMember) {
            tvName.text = member.displayName ?: "(No name)"
            tvEmail.text = member.email ?: ""
            tvRole.text = member.role?.uppercase() ?: "MEMBER"

            // Role badge color
            val roleColor = when (member.role?.lowercase()) {
                "manager" -> android.graphics.Color.parseColor("#FF6B35")
                "co-manager" -> android.graphics.Color.parseColor("#4ECDC4")
                else -> android.graphics.Color.parseColor("#95A5A6")
            }
            tvRole.setTextColor(roleColor)

            // Load avatar
            val baseUrl = "http://10.0.2.2:3001"
            val avatarUrl = member.avatarUrl
            if (!avatarUrl.isNullOrBlank()) {
                val fullUrl = if (avatarUrl.startsWith("http")) avatarUrl else "$baseUrl$avatarUrl"
                Glide.with(itemView)
                    .load(fullUrl)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(imgAvatar)
            } else {
                imgAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
            }

            itemView.setOnClickListener { onMemberClick(member) }
        }
    }
}

