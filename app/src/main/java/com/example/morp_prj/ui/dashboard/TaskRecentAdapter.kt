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
import com.example.morp_prj.data.db.TaskEntity

class TaskRecentAdapter(
    private val onArrowClick: (TaskEntity, View) -> Unit,
    private val onCheckboxToggle: (TaskEntity, Boolean) -> Unit
) : ListAdapter<TaskEntity, TaskRecentAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TaskEntity>() {
            override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean = oldItem == newItem
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTaskTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvTaskDesc)
        val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
        val cb: CheckBox = view.findViewById(R.id.cbTask)
        val pb: ProgressBar? = view.findViewById(R.id.pbTaskLoading)
        val chipGroupTags: ChipGroup? = view.findViewById(R.id.chip_group_tags)

        fun bind(item: TaskEntity) {
            tvTitle.text = item.title
            tvDesc.text = item.description
            // ensure checkbox enabled when binding
            cb.isEnabled = true
            cb.isChecked = item.status.equals("DONE", true)
            // hide progress by default
            pb?.visibility = View.GONE

            // populate tags
            chipGroupTags?.removeAllViews()
            val tags = item.tags()
            for (tag in tags) {
                val chip = Chip(itemView.context)
                chip.text = tag
                chip.isClickable = false
                chip.isCheckable = false
                chip.setChipBackgroundColorResource(R.color.bg_tag_grey_color_state)
                chip.setTextColor(itemView.context.getColor(R.color.text_primary))
                val lp = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                val margin = (4 * itemView.context.resources.displayMetrics.density).toInt()
                lp.setMargins(0, 0, margin, 0)
                chip.layoutParams = lp
                chipGroupTags?.addView(chip)
            }

            ivArrow.setOnClickListener { onArrowClick(item, ivArrow) }

            cb.setOnClickListener {
                // show progress indicator and hide checkbox (prevent double taps)
                pb?.visibility = View.VISIBLE
                cb.isEnabled = false
                onCheckboxToggle(item, cb.isChecked)
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
