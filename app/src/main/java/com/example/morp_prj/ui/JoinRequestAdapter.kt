package com.example.morp_prj.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.TeamMember

class JoinRequestAdapter(
    private var requests: List<TeamMember>,
    private val onApprove: (TeamMember) -> Unit,
    private val onReject: (TeamMember) -> Unit
) : RecyclerView.Adapter<JoinRequestAdapter.RequestViewHolder>() {

    class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivUserAvatar)
        val btnApprove: Button = itemView.findViewById(R.id.btnApprove)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)
        // Thêm các view khác nếu cần, ví dụ: thời gian request
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_join_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]

        holder.tvUserName.text = request.displayName

        val fullUrl = RetrofitClient.buildFullUrl(request.avatarUrl) ?: request.avatarUrl
        Glide.with(holder.itemView.context)
            .load(fullUrl)
            .placeholder(R.drawable.ic_profile_selector)
            .error(R.drawable.ic_profile_selector)
            .circleCrop()
            .into(holder.ivAvatar)

        holder.btnApprove.setOnClickListener { onApprove(request) }
        holder.btnReject.setOnClickListener { onReject(request) }
    }

    override fun getItemCount(): Int = requests.size

    fun updateData(newRequests: List<TeamMember>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}