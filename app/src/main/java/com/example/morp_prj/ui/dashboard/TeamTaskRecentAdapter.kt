package com.example.morp_prj.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask

class TeamTaskRecentAdapter(
    private val onArrowClick: (TeamTask, View) -> Unit,
    private val onStatusChange: (TeamTask, Boolean) -> Unit
) : ListAdapter<TeamTask, TeamTaskRecentAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TeamTask>() {
            override fun areItemsTheSame(oldItem: TeamTask, newItem: TeamTask): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: TeamTask, newItem: TeamTask): Boolean = oldItem == newItem
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTaskTitle)
        private val tvDesc: TextView = view.findViewById(R.id.tvTaskDesc)
        private val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
        private val cb: CheckBox = view.findViewById(R.id.cbTask)
        private val pb: ProgressBar? = view.findViewById(R.id.pbTaskLoading)
        private val chipGroupTags: ChipGroup? = view.findViewById(R.id.chip_group_tags)

        fun bind(item: TeamTask) {
            tvTitle.text = item.title
            tvDesc.text = item.description ?: ""
            cb.isEnabled = true
            cb.isChecked = item.status.equals("DONE", true)
            pb?.visibility = View.GONE

            // tagsCsv -> chips
            chipGroupTags?.removeAllViews()
            val tagsCsv = item.tagsCsv ?: ""
            val tags = tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            for (tag in tags) {
                val chip = Chip(itemView.context)
                chip.text = tag
                chip.isClickable = false
                chip.isCheckable = false
                chip.setTextColor(itemView.context.getColor(R.color.text_primary))
                chipGroupTags?.addView(chip)
            }

            ivArrow.setOnClickListener { onArrowClick(item, ivArrow) }

            cb.setOnClickListener {
                pb?.visibility = View.VISIBLE
                cb.isEnabled = false
                onStatusChange(item, cb.isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_team_task, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}

