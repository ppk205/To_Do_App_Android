package com.example.morp_prj.ui

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AssignedTeamTasksAdapter(
    private var tasks: List<TeamTask> = emptyList(),

    private val onArrowClick: ((TeamTask, View) -> Unit)? = null,
    // callback when checkbox toggled: (task, isChecked)
    private val onCheckChanged: ((TeamTask, Boolean) -> Unit)? = null,
    // callback when a status action is selected from the popup: (task, newStatus)
    private val onStatusChange: ((TeamTask, String) -> Unit)? = null
) : RecyclerView.Adapter<AssignedTeamTasksAdapter.ViewHolder>() {

    // track inflight updates by taskId
    private val loadingTaskIds = mutableSetOf<String>()

    fun setLoading(taskId: String, loading: Boolean) {
        if (loading) loadingTaskIds.add(taskId) else loadingTaskIds.remove(taskId)
        // find index and refresh that item
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun submitList(newTasks: List<TeamTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTaskTitle: TextView = view.findViewById(R.id.tvTaskTitle)
        val tvTaskDesc: TextView = view.findViewById(R.id.tvTaskDesc)
        val tvStatusBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvPriorityBadge: TextView? = view.findViewById(R.id.tvPriorityBadge)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val layoutAvatarContainer: LinearLayout = view.findViewById(R.id.layoutAvatarContainer)
        val chipGroupTags: ChipGroup? = view.findViewById(R.id.chip_group_tags)
        val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
        val cbTask: android.widget.CheckBox = view.findViewById(R.id.cbTask)
        val pbTaskLoading: android.widget.ProgressBar = view.findViewById(R.id.pbTaskLoading)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_team_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        val context = holder.itemView.context

        holder.tvTaskTitle.text = task.title
        holder.tvTaskDesc.text = task.description

        // Priority badge
        holder.tvPriorityBadge?.let { badge ->
            badge.text = task.priority.uppercase()
            when (task.priority.uppercase()) {
                "HIGH" -> badge.setBackgroundResource(R.drawable.bg_badge_red)
                "MEDIUM" -> badge.setBackgroundResource(R.drawable.bg_badge_purple)
                "LOW" -> badge.setBackgroundResource(R.drawable.bg_circle_green)
                else -> badge.setBackgroundResource(R.drawable.bg_badge_purple)
            }
        }

        // show progress or checkbox
        val isLoading = loadingTaskIds.contains(task.id)
        holder.pbTaskLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        holder.cbTask.visibility = if (isLoading) View.GONE else View.VISIBLE

        // Set checkbox state based on status
        holder.cbTask.setOnCheckedChangeListener(null)
        val isDone = task.status.equals("DONE", ignoreCase = true) || task.status.equals("COMPLETED", ignoreCase = true)
        holder.cbTask.isChecked = isDone
        holder.cbTask.setOnCheckedChangeListener { _, checked ->
            // If currently loading, ignore toggles
            if (loadingTaskIds.contains(task.id)) return@setOnCheckedChangeListener
            onCheckChanged?.invoke(task, checked)
        }

        holder.tvStatusBadge.text = task.status
        // Basic status coloring logic
        when (task.status.uppercase()) {
            "TODO" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_purple)
            "IN_PROGRESS" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
            "DONE" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_circle_green)
            else -> holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
        }

        // Make status badge interactive: show a popup menu to change status
        holder.tvStatusBadge.isClickable = true
        holder.tvStatusBadge.setOnClickListener { anchor ->
            try {
                val popup = PopupMenu(context, anchor)
                popup.menu.add("Mark TODO")
                popup.menu.add("Mark In Progress")
                popup.menu.add("Mark DONE")
                popup.setOnMenuItemClickListener { menuItem ->
                    val title = menuItem.title.toString()
                    val targetStatus = when (title) {
                        "Mark TODO" -> "TODO"
                        "Mark In Progress" -> "IN_PROGRESS"
                        "Mark DONE" -> "DONE"
                        else -> null
                    }
                    if (targetStatus != null && targetStatus != task.status) {
                        onStatusChange?.invoke(task, targetStatus)
                    }
                    true
                }
                popup.show()
            } catch (_: Exception) {
                // ignore popup errors
            }
        }

        if (task.dueDate != null) {
            val sdf = SimpleDateFormat("dd-MM-yyyy  |  HH:MM", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(task.dueDate))
        } else {
            holder.tvDate.text = ""
        }

        // Avatar logic
        holder.layoutAvatarContainer.removeAllViews()
        val assignees = task.assignees

        // If asignees.size > 3, show 3 items + overflow count?
        // This implies: [1][2][3][+N]

        val displayLimit = 3
        val countToShow = if (assignees.size > displayLimit) displayLimit else assignees.size

        for (i in 0 until countToShow) {
            val user = assignees[i]
            val avatarView = LayoutInflater.from(context)
                .inflate(R.layout.item_avatar_circle, holder.layoutAvatarContainer, false) as ImageView

            Glide.with(context)
                .load(user.avatarUrl)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .into(avatarView)

            holder.layoutAvatarContainer.addView(avatarView)
        }

        if (assignees.size > displayLimit) {
            val remaining = assignees.size - displayLimit

            // Create TextView for +N
            val textView = TextView(context)
            val size = (24 * context.resources.displayMetrics.density).toInt()
            val layoutParams = LinearLayout.LayoutParams(size, size)
            layoutParams.marginEnd = (-8 * context.resources.displayMetrics.density).toInt()
            textView.layoutParams = layoutParams

            textView.text = "+$remaining"
            textView.setTextColor(Color.WHITE)
            textView.textSize = 10f
            textView.gravity = Gravity.CENTER
            textView.setBackgroundResource(R.drawable.bg_circle_green)
            // Ideally define a drawable for this

            holder.layoutAvatarContainer.addView(textView)
        }

        // if tagsCsv present -> show tags as Material Chips in chip group (fallback to avatar container)
        // Clear previous chipGroup children if present
        holder.chipGroupTags?.removeAllViews()
        task.tagsCsv?.let { csv ->
            val tags = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            for (tag in tags) {
                val chip = Chip(context)
                chip.text = tag
                chip.isCheckable = false
                chip.isClickable = false
                chip.setTextColor(Color.DKGRAY)
                val bgColor = Color.parseColor("#E0E0E0")
                chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(bgColor)
                chip.textSize = 12f

                if (holder.chipGroupTags != null) {
                    holder.chipGroupTags.addView(chip)
                } else {
                    // fallback: append to avatar container
                    holder.layoutAvatarContainer.addView(chip)
                }
            }
        } ?: run {
            holder.chipGroupTags?.removeAllViews()
        }

        holder.ivArrow.setOnClickListener {
            onArrowClick?.invoke(task, holder.itemView)
        }
    }


    override fun getItemCount() = tasks.size
}