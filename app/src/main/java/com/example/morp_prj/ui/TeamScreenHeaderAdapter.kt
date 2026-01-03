package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.utils.PreferenceManager

class TeamScreenHeaderAdapter(
    private val preferenceManager: PreferenceManager,
    private val pinnedTeams: List<Team>,
    private val onPinnedTeamClick: (Team) -> Unit,
    private val onPinnedTeamLongClick: (Team) -> Unit
) : RecyclerView.Adapter<TeamScreenHeaderAdapter.HeaderViewHolder>() {

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvUserEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
        val rvPinnedTeamsInternal: RecyclerView = itemView.findViewById(R.id.rvPinnedTeamsInternal)
        val tvPinnedTitle: TextView = itemView.findViewById(R.id.tvPinnedTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_team_screen_header, parent, false)
        return HeaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        val displayName = preferenceManager.getDisplayName()?.takeIf { it.isNotEmpty() }
            ?: preferenceManager.getUsername() ?: "User"
        holder.tvUserName.text = displayName
        holder.tvUserEmail.text = preferenceManager.getEmail() ?: "No Email"

        holder.tvPinnedTitle.text = "Pinned Teams (${pinnedTeams.size})"

        val pinnedAdapter = TeamAdapter(
            pinnedTeams,
            R.layout.item_pinned_team,
            onItemClick = onPinnedTeamClick,
            onItemLongClick = onPinnedTeamLongClick
        )

        holder.rvPinnedTeamsInternal.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvPinnedTeamsInternal.adapter = pinnedAdapter
    }

    override fun getItemCount(): Int = 1
}