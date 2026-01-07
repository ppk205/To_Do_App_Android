package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.google.android.material.imageview.ShapeableImageView

class NotificationsAdapter(
    private val items: MutableList<UiNotification> = mutableListOf(),
    private val onAction: ((item: UiNotification, actionId: Int) -> Unit)? = null,
) : RecyclerView.Adapter<NotificationsAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ShapeableImageView = itemView.findViewById(R.id.ivAvatar)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val badgeNew: View = itemView.findViewById(R.id.badgeNew)
        val btnMore: View = itemView.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvMessage.text = item.message
        holder.tvTime.text = item.time

        holder.badgeNew.visibility = if (item.isNew) View.VISIBLE else View.GONE

        // simple placeholder avatar
        holder.ivAvatar.setImageResource(R.drawable.ic_group_placeholder)

        holder.btnMore.setOnClickListener { v ->
            val popup = PopupMenu(v.context, v)
            popup.menuInflater.inflate(R.menu.notification_item_menu, popup.menu)
            popup.setOnMenuItemClickListener { mi ->
                onAction?.invoke(item, mi.itemId)
                true
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = items.size

    fun replaceAll(newItems: List<UiNotification>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun append(more: List<UiNotification>) {
        if (more.isEmpty()) return
        val start = items.size
        items.addAll(more)
        notifyItemRangeInserted(start, more.size)
    }

    fun removeById(id: Long) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return
        items.removeAt(idx)
        notifyItemRemoved(idx)
    }
}
