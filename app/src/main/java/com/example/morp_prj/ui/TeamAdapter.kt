package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.model.Team
import java.util.Locale

class TeamAdapter(
    private var teamList: List<Team>,
    private val layoutResId: Int,
    private val onItemClick: ((Team) -> Unit)? = null,
    private val onItemLongClick: ((Team) -> Unit)? = null
) : RecyclerView.Adapter<TeamAdapter.TeamViewHolder>() {

    class TeamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTeamName: TextView = itemView.findViewById(R.id.tvTeamName)
        val imgTeamLogo: ImageView = itemView.findViewById(R.id.imgTeamLogo)
        val tvRole: TextView? = itemView.findViewById(R.id.tvRole)
        val tvMemberCount: TextView? = itemView.findViewById(R.id.tvMemberCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return TeamViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
        val team = teamList[position]
        
        holder.tvTeamName.text = team.name
        
        Glide.with(holder.itemView.context)
            .load(team.avatarUrl)
            .placeholder(android.R.drawable.btn_star) 
            .error(android.R.drawable.btn_star)
            .into(holder.imgTeamLogo)

        val count = team.memberCount
        holder.tvMemberCount?.text = "$count member${if (count > 1) "s" else ""}"
        
        val roleText = team.role?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Member"
        holder.tvRole?.text = roleText

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(team)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(team)
            true
        }
    }

    override fun getItemCount(): Int {
        return teamList.size
    }
    
    fun updateData(newTeams: List<Team>) {
        teamList = newTeams
        notifyDataSetChanged()
    }
}