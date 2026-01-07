package com.example.morp_prj.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.TeamMember

class MemberAdapter(
    private var members: List<TeamMember>,
    private val onMoreClick: ((TeamMember, View) -> Unit)? = null
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvMemberName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvMemberEmail)
        val tvRole: TextView = itemView.findViewById(R.id.tvMemberRole)
        val viewStatus: View = itemView.findViewById(R.id.viewStatus)
        val btnMore: ImageView = itemView.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val user = members[position]

        val displayName = user.displayName
        val username = user.displayName

        holder.tvName.text = when {
            !displayName.isNullOrBlank() -> displayName
            !username.isNullOrBlank() -> username
            else -> "Member"
        }

        holder.tvEmail.text = user.email ?: "No Email"
        holder.tvEmail.visibility = View.VISIBLE

        val role = user.role
        if (!role.isNullOrBlank()) {
            holder.tvRole.text = role
            // Tùy chỉnh màu nền cho Role ở đây
        } else {
            holder.tvRole.text = "Member"
        }
        holder.tvRole.visibility = View.VISIBLE

        val avatarUrl = user.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            val fullUrl = RetrofitClient.buildFullUrl(avatarUrl) ?: avatarUrl
            Glide.with(holder.itemView.context)
                .load(fullUrl)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(holder.imgAvatar)
        } else {
            Glide.with(holder.itemView.context)
                .load(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(holder.imgAvatar)
        }

        // Status Dot
        holder.viewStatus.visibility = View.VISIBLE

        holder.btnMore.visibility = View.VISIBLE
        holder.btnMore.setOnClickListener {
            onMoreClick?.invoke(user, it)
        }
    }

    override fun getItemCount() = members.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newMembers: List<TeamMember>) {
        this.members = newMembers
        notifyDataSetChanged()
    }
}