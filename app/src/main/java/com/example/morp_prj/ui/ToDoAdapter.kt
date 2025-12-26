package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
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
    private val onMoreClicked: ((item: ToDoItem) -> Unit)? = null
) : RecyclerView.Adapter<ToDoAdapter.ToDoViewHolder>() {

    // List dữ liệu nội bộ được khởi tạo từ tham số constructor
    private val items: MutableList<ToDoItem> = items.toMutableList()

    // 2. HỖ TRỢ submitList: Để cập nhật dữ liệu về sau (cho HomeFragment load API/Mock)
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

        val priorityText = item.priority.name.lowercase(Locale.getDefault())
            .replaceFirstChar { it.uppercase(Locale.getDefault()) }
        holder.txtPriority.text = priorityText

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
        holder.checkDone.isChecked = (item.status == TaskStatus.DONE)
        holder.checkDone.setOnCheckedChangeListener { _, isChecked ->
            item.status = if (isChecked) TaskStatus.DONE else TaskStatus.TODO
            onCheckedChanged?.invoke(item, isChecked)
        }

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }

        holder.ivMore.setOnClickListener {
            onMoreClicked?.invoke(item)
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