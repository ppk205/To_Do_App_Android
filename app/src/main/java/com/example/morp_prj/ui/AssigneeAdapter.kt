package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.model.User

class AssigneeAdapter(private val users: List<User>) :
    RecyclerView.Adapter<AssigneeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val tvName: TextView = view.findViewById(R.id.tvName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Có thể dùng lại item_member nhưng ẩn nút btnMore đi
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.displayName ?: user.username

        // Ẩn các thành phần không cần thiết của item_member
        holder.itemView.findViewById<View>(R.id.btnMore).visibility = View.GONE
        holder.itemView.findViewById<View>(R.id.tvRole).visibility = View.GONE

        val avatarUrl = user.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(holder.ivAvatar)
        } else {
            // Load placeholder only
            Glide.with(holder.itemView.context)
                .load(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(holder.ivAvatar)
        }
    }

    override fun getItemCount() = users.size
}