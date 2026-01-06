package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.model.Team

class TeamAdapter(
    private var teams: List<Team>,
    private val onTeamClick: (Team) -> Unit,
    private val onChatClick: (Team) -> Unit,
    private val onPinClick: (Team) -> Unit
) : RecyclerView.Adapter<TeamAdapter.TeamViewHolder>() {

    inner class TeamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvTeamName)
        val tvInfo: TextView = itemView.findViewById(R.id.tvTeamInfo)
        val imgPin: ImageView = itemView.findViewById(R.id.imgPin)
        private val layoutTags: LinearLayout = itemView.findViewById(R.id.layoutTags)
        val btnChat: ImageView = itemView.findViewById(R.id.btnChat)

        fun bind(team: Team) {
            tvName.text = team.name
            val roleDisplay = team.role?.replaceFirstChar { it.uppercase() } ?: "Member"
            tvInfo.text = "${team.memberCount} members • $roleDisplay"

            // Avatar
            if (!team.avatarUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(team.avatarUrl)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .fallback(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(imgAvatar)
            } else {
                imgAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
            }

            // Pin
            imgPin.visibility = View.VISIBLE
            imgPin.imageAlpha = if (team.isPinned) 255 else 120
            imgPin.setColorFilter(itemView.context.getColor(if (team.isPinned) R.color.soft_blue_primary else R.color.gray_text))
            imgPin.setOnClickListener { onPinClick(team) }

            // Tags Logic
            renderTags(team.tags)

            // Click Listeners
            // 1. Click vào cả card -> Xem chi tiết
            itemView.setOnClickListener { onTeamClick(team) }

            // 2. Click vào nút Chat -> Vào chat nhanh
            btnChat.setOnClickListener {
                onChatClick(team)
            }
        }

        private fun renderTags(tags: List<String>?) {
            layoutTags.removeAllViews()

            if (tags.isNullOrEmpty()) {
                layoutTags.visibility = View.GONE
                return
            }
            layoutTags.visibility = View.VISIBLE

            val inflater = LayoutInflater.from(itemView.context)
            val maxVisibleTags = 3 // show up to 3 slots; last may become "+N"
            val tagsToShow = tags.take(maxVisibleTags)
            val remaining = tags.size - tagsToShow.size

            tagsToShow.forEachIndexed { index, tag ->
                val isLastSlot = index == maxVisibleTags - 1 && remaining > 0
                if (isLastSlot) {
                    layoutTags.addView(createMoreTagView(remaining))
                } else {
                    layoutTags.addView(createTagView(tag))
                }
            }

            // If there are more tags beyond the taken subset, ensure we append +N
            if (remaining > 0 && tagsToShow.size < maxVisibleTags) {
                layoutTags.addView(createMoreTagView(remaining))
            }
        }

        private fun createTagView(text: String): View {
            val view = LayoutInflater.from(itemView.context).inflate(R.layout.view_tag_chip, layoutTags, false)
            view.findViewById<TextView>(R.id.tagText).text = text
            return view
        }

        private fun createMoreTagView(remaining: Int): View {
            val view = LayoutInflater.from(itemView.context).inflate(R.layout.view_tag_more_chip, layoutTags, false)
            view.findViewById<TextView>(R.id.tagText).text = "+$remaining"
            return view
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_team, parent, false)
        return TeamViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
        holder.bind(teams[position])
    }

    override fun getItemCount(): Int = teams.size

    fun updateData(newTeams: List<Team>) {
        this.teams = newTeams
        notifyDataSetChanged()
    }
}