package com.example.morp_prj.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask
import com.example.morp_prj.utils.DateUtils
import com.google.android.material.card.MaterialCardView

class AssignedTeamTasksAdapter(
    private var tasks: List<TeamTask> = emptyList(),
    private val onTaskClick: ((TeamTask) -> Unit)? = null,
    private val onStatusChange: ((TeamTask, String) -> Unit)? = null
) : RecyclerView.Adapter<AssignedTeamTasksAdapter.ViewHolder>() {

    private val pastelColors = listOf(
        R.color.pastel_yellow,
        R.color.pastel_gray,
        R.color.pastel_green,
        R.color.pastel_purple
    )

    fun submitList(newTasks: List<TeamTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTaskTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvTaskDesc)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvPriority: TextView = view.findViewById(R.id.tvPriorityMini)
        val cardTask: MaterialCardView = view.findViewById(R.id.cardTask)
        val ivDot: ImageView = view.findViewById(R.id.ivTimelineDot)

        fun bind(task: TeamTask, position: Int) {
            tvTitle.text = task.title
            tvDesc.text = task.description ?: ""

            // Priority Color
            tvPriority.text = task.priority.uppercase()
            tvPriority.setTextColor(Color.parseColor(
                when(task.priority.uppercase()) {
                    "HIGH" -> "#D32F2F"
                    "LOW" -> "#388E3C"
                    else -> "#F57C00"
                }
            ))

            task.dueDate?.let {
                tvTime.text = DateUtils.formatTime(it)
            } ?: run { tvTime.text = "--:--" }

            // LOGIC TRẠNG THÁI (3 States + Overdue)
            val status = task.status.uppercase()
            val isOverdue = task.dueDate != null && task.dueDate < System.currentTimeMillis() && status != "DONE"

            tvTitle.paintFlags = tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            cardTask.alpha = 1.0f

            when {
                status == "DONE" || status == "COMPLETED" -> {
                    // DONE: Xanh, Stick, Gạch chữ
                    ivDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                    ivDot.setImageResource(R.drawable.ic_check)
                    cardTask.alpha = 0.6f
                    tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                }
                status == "IN_PROGRESS" -> {
                    // IN_PROGRESS: Cam, Icon đang chạy
                    ivDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9800"))
                    ivDot.setImageResource(R.drawable.ic_in_progress) // Đảm bảo có icon này
                }
                isOverdue -> {
                    // OVERDUE: Đỏ, Không icon
                    ivDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F44336"))
                    ivDot.setImageDrawable(null)
                }
                else -> {
                    // TODO: Xám, Không icon
                    ivDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BDBDBD"))
                    ivDot.setImageDrawable(null)
                }
            }

            if (status != "DONE" && status != "IN_PROGRESS") {
                ivDot.setImageDrawable(null)
            }

            val colorRes = pastelColors[position % pastelColors.size]
            cardTask.setCardBackgroundColor(ContextCompat.getColor(itemView.context, colorRes))

            cardTask.setOnClickListener { onTaskClick?.invoke(task) }

            // Click Dot Xoay vòng trạng thái
            ivDot.setOnClickListener {
                val nextStatus = when (status) {
                    "TODO" -> "IN_PROGRESS"
                    "IN_PROGRESS" -> "DONE"
                    "DONE" -> "TODO"
                    else -> "TODO"
                }
                onStatusChange?.invoke(task, nextStatus)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline_task, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(tasks[position], position)
    }

    override fun getItemCount() = tasks.size
}