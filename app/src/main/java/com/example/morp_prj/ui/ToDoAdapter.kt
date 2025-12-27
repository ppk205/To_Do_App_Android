package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import java.util.Locale

class ToDoAdapter(
    // 1. LINH HOẠT: Thêm tham số 'items' với giá trị mặc định là rỗng.
    // - PersonalFragment gọi 'ToDoAdapter(items = list)' -> OK (nhận list ban đầu)
    // - HomeFragment gọi 'ToDoAdapter()' -> OK (tự động lấy list rỗng)
    items: List<ToDoItem> = emptyList(),

    // Các callback sự kiện (đều có thể null)
    private val onItemClick: ((item: ToDoItem) -> Unit)? = null,
    private val onCheckedChanged: ((item: ToDoItem, isChecked: Boolean) -> Unit)? = null,
    private val onRowClicked: ((anchor: View, item: ToDoItem) -> Unit)? = null,
    private val onRowMenuClicked: ((anchor: View, item: ToDoItem) -> Unit)? = null,
    private val onSelectionToggle: ((item: ToDoItem) -> Unit)? = null,
    private val onLongPressForSelection: ((item: ToDoItem) -> Unit)? = null,
    private val onMoreClicked: ((item: ToDoItem) -> Unit)? = null
) : RecyclerView.Adapter<ToDoAdapter.ToDoViewHolder>() {

    private var items: MutableList<ToDoItem> = items.toMutableList()
    private var selectedIds: Set<Long> = emptySet()
    private var selectionEnabled: Boolean = false

    fun submitData(newItems: MutableList<ToDoItem>, selection: Set<Long>, selectionMode: Boolean) {
        items = newItems
        selectedIds = selection
        selectionEnabled = selectionMode
    }

    fun submitList(newList: List<ToDoItem>) {
        val diffCallback = ToDoDiffCallback(this.items, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        this.items.clear()
        this.items.addAll(newList)

        diffResult.dispatchUpdatesTo(this)
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

        val priorityText = item.priority.name.lowercase(Locale.getDefault())
            .replaceFirstChar { it.uppercase(Locale.getDefault()) }
        holder.txtPriority.text = priorityText

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

class ToDoDiffCallback(
    private val oldList: List<ToDoItem>,
    private val newList: List<ToDoItem>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}