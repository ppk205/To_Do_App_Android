package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R

class ToDoAdapter(
    private val onCheckedChanged: ((item: ToDoItem, isChecked: Boolean) -> Unit)? = null,
    private val onRowClicked: ((anchor: View, item: ToDoItem) -> Unit)? = null,
    private val onRowMenuClicked: ((anchor: View, item: ToDoItem) -> Unit)? = null,
    private val onSelectionToggle: ((item: ToDoItem) -> Unit)? = null,
    private val onLongPressForSelection: ((item: ToDoItem) -> Unit)? = null,
) : RecyclerView.Adapter<ToDoAdapter.ToDoViewHolder>() {

    private var items: List<ToDoItem> = emptyList()
    private var selectedIds: Set<Long> = emptySet()
    private var selectionEnabled: Boolean = false

    fun submitData(newItems: List<ToDoItem>, selection: Set<Long>, selectionMode: Boolean) {
        items = newItems
        selectedIds = selection
        selectionEnabled = selectionMode
    }

    inner class ToDoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkDone: CheckBox = itemView.findViewById(R.id.checkDone)
        val txtTitle: TextView = itemView.findViewById(R.id.txtTitle)
        val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        val txtPriority: TextView = itemView.findViewById(R.id.txtPriority)
        val txtTagOne: TextView = itemView.findViewById(R.id.txtTagWork)
        val txtTagTwo: TextView = itemView.findViewById(R.id.txtTagMarketing)
        val ivMore: ImageView = itemView.findViewById(R.id.ivMore)
        val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)
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

        val tag1 = item.tags.getOrNull(0)
        val tag2 = item.tags.getOrNull(1)
        holder.txtTagOne.isVisible = !tag1.isNullOrBlank()
        holder.txtTagTwo.isVisible = !tag2.isNullOrBlank()
        holder.txtTagOne.text = tag1.orEmpty()
        holder.txtTagTwo.text = tag2.orEmpty()

        holder.checkDone.setOnCheckedChangeListener(null)
        holder.checkDone.isChecked = item.status == TaskStatus.DONE
        holder.checkDone.isEnabled = !selectionEnabled
        holder.checkDone.setOnCheckedChangeListener { _, isChecked ->
            if (!selectionEnabled) onCheckedChanged?.invoke(item, isChecked)
        }

        holder.ivMore.isVisible = !selectionEnabled
        holder.ivMore.setOnClickListener { anchor ->
            if (selectionEnabled) {
                onSelectionToggle?.invoke(item)
            } else {
                onRowMenuClicked?.invoke(anchor, item)
            }
        }

        val isSelected = selectionEnabled && selectedIds.contains(item.id)
        holder.selectionOverlay.isVisible = isSelected

        holder.itemView.setOnLongClickListener {
            onLongPressForSelection?.invoke(item)
            true
        }
        holder.itemView.setOnClickListener { anchor ->
            if (selectionEnabled) {
                onSelectionToggle?.invoke(item)
            } else {
                onRowClicked?.invoke(anchor, item)
            }
        }
    }
}
