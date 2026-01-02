package com.example.morp_prj.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.morp_prj.R

// small adapter to present 4 summary cards
class SummaryAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<SummaryAdapter.VH>() {
    private var state: TaskDashboardUiState? = null

    fun submit(s: TaskDashboardUiState) {
        state = s
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_summary_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = state ?: return
        val ctx = parentContext(holder)
        when (position) {
            0 -> holder.bind(s.total, ctx.getString(R.string.total_tasks))
            1 -> holder.bind(s.done, "Done")
            2 -> holder.bind(s.pending, "Pending")
            3 -> holder.bind(s.overdue, "Overdue")
        }
    }

    private fun parentContext(holder: VH) = holder.itemView.context

    override fun getItemCount(): Int = 4

    class VH(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        private val tvCount: android.widget.TextView = view.findViewById(R.id.tvSummaryCount)
        private val tvLabel: android.widget.TextView = view.findViewById(R.id.tvSummaryLabel)
        fun bind(count: Int, label: String) {
            tvCount.text = count.toString()
            tvLabel.text = label
        }
    }
}