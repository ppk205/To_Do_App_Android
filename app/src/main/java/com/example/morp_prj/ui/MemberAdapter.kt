package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamMember
import java.util.Locale

class MemberAdapter(
    var members: List<TeamMember>, // Đổi thành public var
    private val onMoreClick: (TeamMember) -> Unit
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvRole: TextView = itemView.findViewById(R.id.tvRole)
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)
        val viewStatus: View = itemView.findViewById(R.id.viewStatus) // Trạng thái online/offline
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]

        holder.tvName.text = member.displayName
        holder.tvRole.text = member.role.replaceFirstChar { it.titlecase(Locale.getDefault()) }

        Glide.with(holder.itemView.context)
            .load(member.avatarUrl)
            .placeholder(R.drawable.ic_profile_selector) // Dùng icon profile mặc định
            .error(R.drawable.ic_profile_selector)
            .circleCrop()
            .into(holder.ivAvatar)

        // TODO: Xử lý trạng thái online/offline cho viewStatus

        holder.btnMore.setOnClickListener {
            onMoreClick(member)
        }
    }

    override fun getItemCount(): Int = members.size

    fun updateData(newMembers: List<TeamMember>) {
        members = newMembers
        notifyDataSetChanged()
    }
}