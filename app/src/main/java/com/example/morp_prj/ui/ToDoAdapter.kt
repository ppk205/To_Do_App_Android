package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R

class ToDoAdapter(
    private val items: List<ToDoItem>,
    private val onCheckedChanged: ((item: ToDoItem, isChecked: Boolean) -> Unit)? = null,
    private val onMoreClicked: ((item: ToDoItem) -> Unit)? = null,
) : RecyclerView.Adapter<ToDoAdapter.ToDoViewHolder>() {

    inner class ToDoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkDone: CheckBox = itemView.findViewById(R.id.checkDone)
        val txtTitle: TextView = itemView.findViewById(R.id.txtTitle)
        val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        val txtPriority: TextView = itemView.findViewById(R.id.txtPriority)
        val txtTagWork: TextView = itemView.findViewById(R.id.txtTagWork)
        val txtTagMarketing: TextView = itemView.findViewById(R.id.txtTagMarketing)
        val ivMore: ImageView = itemView.findViewById(R.id.ivMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToDoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_todo, parent, false)
        return ToDoViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ToDoViewHolder, position: Int) {
        val item = items[position]

        holder.txtTitle.text = item.title
        holder.txtTime.text = item.timeLabel
        holder.txtPriority.text = item.priority.name

        // Tags: map up to 2 chips for now
        val tag1 = item.tags.getOrNull(0)
        val tag2 = item.tags.getOrNull(1)

        if (tag1.isNullOrBlank()) {
            holder.txtTagWork.visibility = View.GONE
        } else {
            holder.txtTagWork.visibility = View.VISIBLE
            holder.txtTagWork.text = tag1
        }

        if (tag2.isNullOrBlank()) {
            holder.txtTagMarketing.visibility = View.GONE
        } else {
            holder.txtTagMarketing.visibility = View.VISIBLE
            holder.txtTagMarketing.text = tag2
        }

        holder.checkDone.setOnCheckedChangeListener(null)
        holder.checkDone.isChecked = item.status == TaskStatus.DONE
        holder.checkDone.setOnCheckedChangeListener { _, isChecked ->
            onCheckedChanged?.invoke(item, isChecked)
        }

        holder.ivMore.setOnClickListener { onMoreClicked?.invoke(item) }
    }
}
