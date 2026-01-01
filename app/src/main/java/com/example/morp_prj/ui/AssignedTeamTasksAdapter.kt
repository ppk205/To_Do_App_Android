package com.example.morp_prj.ui

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AssignedTeamTasksAdapter(
    private var tasks: List<TeamTask> = emptyList(),
    // provide the clicked item view so caller can animate it before navigation
    private val onArrowClick: ((TeamTask, View) -> Unit)? = null
) : RecyclerView.Adapter<AssignedTeamTasksAdapter.ViewHolder>() {

    fun submitList(newTasks: List<TeamTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTaskTitle: TextView = view.findViewById(R.id.tvTaskTitle)
        val tvTaskDesc: TextView = view.findViewById(R.id.tvTaskDesc)
        val tvStatusBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val layoutAvatarContainer: LinearLayout = view.findViewById(R.id.layoutAvatarContainer)
        val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
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

        holder.tvStatusBadge.text = task.status
        // Basic status coloring logic
        when (task.status.uppercase()) {
            "TODO" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_purple) // Assuming exists or generic
            "DONE" -> holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_circle_green) // Reuse existing or default
            else -> holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
        }

        if (task.dueDate != null) {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(task.dueDate))
        } else {
            holder.tvDate.text = ""
        }

        // Avatar logic
        holder.layoutAvatarContainer.removeAllViews()
        val assignees = task.assignees
        val maxAvatars = 3

        // We want to show up to 3 items. If > 3, show 3 items + overflow count?
        // User said: "tối đa hiển thị 3 item, nếu nhiều hơn 3 thì item thứ 4 sẽ hiển thị +(số lượng còn lại)"
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
            textView.setBackgroundResource(R.drawable.bg_circle_green) // Or generic grey circle
            // Ideally define a drawable for this

            holder.layoutAvatarContainer.addView(textView)
        }

        // Arrow click: pass both task and the itemView to the caller
        holder.ivArrow.setOnClickListener {
            onArrowClick?.invoke(task, holder.itemView)
        }
    }

    override fun getItemCount() = tasks.size
}