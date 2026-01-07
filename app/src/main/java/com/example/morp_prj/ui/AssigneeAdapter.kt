package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.User

class AssigneeAdapter(private val users: List<User>) :
    RecyclerView.Adapter<AssigneeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        val tvName: TextView = view.findViewById(R.id.tvMemberName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.displayName.takeIf { !it.isNullOrBlank() } ?: user.username

        // Hide unnecessary views from item_member layout
        holder.itemView.findViewById<View>(R.id.btnMore)?.visibility = View.GONE
        holder.itemView.findViewById<View>(R.id.tvMemberRole)?.visibility = View.GONE
        holder.itemView.findViewById<View>(R.id.tvMemberEmail)?.visibility = View.GONE
        holder.itemView.findViewById<View>(R.id.viewStatus)?.visibility = View.GONE

        val avatarUrl = user.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            val fullUrl = RetrofitClient.buildFullUrl(avatarUrl) ?: avatarUrl
            Glide.with(holder.itemView.context)
                .load(fullUrl)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(holder.ivAvatar)
        } else {
            Glide.with(holder.itemView.context)
                .load(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(holder.ivAvatar)
        }
    }

    override fun getItemCount() = users.size
}