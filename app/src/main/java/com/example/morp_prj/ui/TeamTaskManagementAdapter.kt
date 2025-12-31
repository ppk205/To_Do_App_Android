package com.example.morp_prj.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask
import com.example.morp_prj.databinding.ItemTeamTaskManagementBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TeamTaskManagementAdapter(
    private var tasks: List<TeamTask> = emptyList(),
    private val onTaskClick: (TeamTask) -> Unit
) : RecyclerView.Adapter<TeamTaskManagementAdapter.ViewHolder>() {

    fun submitList(newTasks: List<TeamTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemTeamTaskManagementBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TeamTask) {
            binding.tvTitle.text = task.title
            
            // Format Date
            if (task.dueDate != null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                binding.tvDueDate.text = "Due: ${sdf.format(Date(task.dueDate))}"
            } else {
                binding.tvDueDate.text = "No Due Date"
            }

            // Priority Badge
            binding.tvPriorityBadge.text = task.priority
            when (task.priority.uppercase()) {
                "HIGH" -> binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_priority_high)
                "MEDIUM" -> binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_priority_medium) // Reuse existing or default
                "LOW" -> binding.tvPriorityBadge.setBackgroundResource(R.drawable.bg_priority_low)
                else -> binding.tvPriorityBadge.setBackgroundColor(Color.GRAY)
            }

            // Status Badge Logic
            // Check overdue first
            val isOverdue = task.dueDate != null && task.dueDate < System.currentTimeMillis() && task.status.uppercase() != "DONE"
            
            if (isOverdue) {
                binding.tvStatusBadge.text = "Overdue"
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
            } else {
                binding.tvStatusBadge.text = task.status
                when (task.status.uppercase()) {
                    "IN_PROGRESS" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_progress)
                    "TODO" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_todo_tag)
                    "DONE" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_circle_green)
                    else -> binding.tvStatusBadge.setBackgroundColor(Color.LTGRAY)
                }
            }

            // Avatars Logic
            binding.layoutAvatars.removeAllViews()
            val maxAvatars = 3
            val assignees = task.assignees
            val displayCount = if (assignees.size > maxAvatars) maxAvatars else assignees.size

            for (i in 0 until displayCount) {
                val user = assignees[i]
                val imageView = ImageView(binding.root.context)
                val size = (24 * binding.root.resources.displayMetrics.density).toInt()
                val params = LinearLayout.LayoutParams(size, size)
                
                // Add negative margin for overlap effect, except for the first item
                if (i > 0) {
                    params.marginStart = (-8 * binding.root.resources.displayMetrics.density).toInt()
                }
                
                imageView.layoutParams = params
                // Use a circular background/mask if needed, or Glide circleCrop
                Glide.with(binding.root.context)
                    .load(user.avatarUrl)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(imageView)
                
                // Optional: Add a white border to separate overlapping avatars
                imageView.background = ContextCompat.getDrawable(binding.root.context, R.drawable.bg_circle_gray) // Or a dedicated ring drawable
                
                binding.layoutAvatars.addView(imageView)
            }

            if (assignees.size > maxAvatars) {
                val remaining = assignees.size - maxAvatars
                val textView = TextView(binding.root.context)
                val size = (24 * binding.root.resources.displayMetrics.density).toInt()
                val params = LinearLayout.LayoutParams(size, size)
                params.marginStart = (-8 * binding.root.resources.displayMetrics.density).toInt()
                textView.layoutParams = params
                
                textView.text = "+$remaining"
                textView.textSize = 10f
                textView.setTextColor(Color.WHITE)
                textView.gravity = android.view.Gravity.CENTER
                textView.setBackgroundResource(R.drawable.bg_circle_gray) // Your gray circle drawable
                
                binding.layoutAvatars.addView(textView)
            }

            binding.btnViewDetails.setOnClickListener {
                onTaskClick(task)
            }
            
            binding.btnEdit.setOnClickListener {
                // Handle edit click
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTeamTaskManagementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size
}