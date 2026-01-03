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
import com.example.morp_prj.data.model.Team
import java.util.Locale

class TeamAdapter(
    var members: List<Team>,
    private val layoutResId: Int,
    private val onItemClick: ((Team) -> Unit)? = null,
    private val onItemLongClick: ((Team) -> Unit)? = null,
    private val onChatClick: ((Team) -> Unit)? = null
) : RecyclerView.Adapter<TeamAdapter.TeamViewHolder>() {

    class TeamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTeamName: TextView = itemView.findViewById(R.id.tvTeamName)
        val imgTeamLogo: ImageView = itemView.findViewById(R.id.imgTeamLogo)
        val tvRole: TextView? = itemView.findViewById(R.id.tvRole)
        val tvMemberCount: TextView? = itemView.findViewById(R.id.tvMemberCount)
        val tvPendingStatus: TextView? = itemView.findViewById(R.id.tvPendingStatus)
        val btnChat: ImageView? = itemView.findViewById(R.id.btnChat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return TeamViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
        val team = members[position]
        
        holder.tvTeamName.text = team.name
        
        Glide.with(holder.itemView.context)
            .load(team.avatarUrl)
            .placeholder(android.R.drawable.btn_star) 
            .error(android.R.drawable.btn_star)
            .into(holder.imgTeamLogo)

        // Xử lý hiển thị dựa trên status
        if (team.status == "pending") {
            holder.tvRole?.visibility = View.GONE
            holder.tvMemberCount?.visibility = View.GONE
            holder.tvPendingStatus?.visibility = View.VISIBLE
        } else {
            holder.tvRole?.visibility = View.VISIBLE
            holder.tvMemberCount?.visibility = View.VISIBLE
            holder.tvPendingStatus?.visibility = View.GONE

            val count = team.memberCount
            holder.tvMemberCount?.text = "$count member${if (count > 1) "s" else ""}"
            
            val roleText = team.role?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: "Member"
            holder.tvRole?.text = roleText
        }

        holder.itemView.setOnClickListener {
            if (team.status != "pending") {
                onItemClick?.invoke(team)
            }
        }

        holder.btnChat?.setOnClickListener {
            if (team.status != "pending") {
                onChatClick?.invoke(team)
            }
        }

        // Ẩn nút chat nếu là pending
        if (team.status == "pending") {
            holder.btnChat?.visibility = View.GONE
        } else {
            holder.btnChat?.visibility = View.VISIBLE
        }

        holder.itemView.setOnLongClickListener {
            if (team.status != "pending") {
                onItemLongClick?.invoke(team)
            }
            true
        }
    }

    override fun getItemCount(): Int = members.size

    fun updateData(newTeams: List<Team>) {
        members = newTeams
        notifyDataSetChanged()
    }
}