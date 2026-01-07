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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class JoinRequestAdapter(
    private var requests: List<TeamMember>,
    private val onApprove: (TeamMember) -> Unit,
    private val onReject: (TeamMember) -> Unit
) : RecyclerView.Adapter<JoinRequestAdapter.RequestViewHolder>() {

    class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvRequestTime: TextView = itemView.findViewById(R.id.tvRequestTime)
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivUserAvatar)
        val btnApprove: Button = itemView.findViewById(R.id.btnApprove)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_join_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]

        holder.tvUserName.text = request.displayName

        // Format and display request time
        holder.tvRequestTime.text = formatRequestTime(request.joinedAt)

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

    /**
     * Format joinedAt timestamp to human-readable relative time
     * e.g., "Just now", "5 minutes ago", "2 hours ago", "3 days ago"
     */
    private fun formatRequestTime(joinedAt: String?): String {
        if (joinedAt.isNullOrEmpty()) {
            return "Recently"
        }

        return try {
            // Try multiple date formats (MySQL DATETIME and ISO 8601)
            val joinDate = parseDate(joinedAt) ?: return "Recently"

            val now = Date()
            val diffInMillis = now.time - joinDate.time

            when {
                diffInMillis < 0 -> "Just now" // Future date (shouldn't happen)
                diffInMillis < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diffInMillis < TimeUnit.HOURS.toMillis(1) -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
                    "$minutes minute${if (minutes > 1) "s" else ""} ago"
                }
                diffInMillis < TimeUnit.DAYS.toMillis(1) -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(diffInMillis)
                    "$hours hour${if (hours > 1) "s" else ""} ago"
                }
                diffInMillis < TimeUnit.DAYS.toMillis(7) -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                    "$days day${if (days > 1) "s" else ""} ago"
                }
                diffInMillis < TimeUnit.DAYS.toMillis(30) -> {
                    val weeks = TimeUnit.MILLISECONDS.toDays(diffInMillis) / 7
                    "$weeks week${if (weeks > 1) "s" else ""} ago"
                }
                else -> {
                    // Show actual date for old requests
                    val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    displayFormat.format(joinDate)
                }
            }
        } catch (e: Exception) {
            // Fallback to simple display if parsing fails
            "Recently"
        }
    }

    /**
     * Parse date from multiple formats (MySQL DATETIME, ISO 8601, etc.)
     * Handles UTC timezone and converts to local time (UTC+7 for Vietnam)
     */
    private fun parseDate(dateString: String): Date? {
        // List of possible date formats
        val formats = listOf(
            // ISO 8601 with milliseconds and Z: "2024-01-07T10:30:00.000Z" - PRIORITY
            // This is UTC time, will be automatically converted to local timezone
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            },
            // ISO 8601 without milliseconds: "2024-01-07T10:30:00Z"
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            },
            // MySQL DATETIME: "2024-01-07 10:30:00" (assume UTC from server)
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            },
            // ISO 8601 with timezone offset: "2024-01-07T10:30:00+07:00"
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        )

        // Try each format until one works
        for (format in formats) {
            try {
                return format.parse(dateString)
            } catch (e: Exception) {
                // Try next format
                continue
            }
        }

        return null
    }
}