package com.example.morp_prj.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.model.ChatMessage
import com.example.morp_prj.utils.DateUtils

class ChatAdapter(private val currentUserId: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val TYPE_ME = 1
        private const val TYPE_OTHER = 2
    }

    fun submitList(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) TYPE_ME else TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ME) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_me, parent, false)
            MeViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_other, parent, false)
            OtherViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is MeViewHolder) holder.bind(msg)
        else if (holder is OtherViewHolder) holder.bind(msg)
    }

    override fun getItemCount() = messages.size

    class MeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.tvMessageContent)
        val time: TextView = view.findViewById(R.id.tvTime)

        fun bind(msg: ChatMessage) {
            content.text = msg.content
            time.text = DateUtils.formatTime(msg.createdAt)
        }
    }

    class OtherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.tvMessageContent)
        val name: TextView = view.findViewById(R.id.tvSenderName)
        val time: TextView = view.findViewById(R.id.tvTime)

        val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)

        fun bind(msg: ChatMessage) {
            content.text = msg.content
            name.text = msg.senderName
            time.text = DateUtils.formatTime(msg.createdAt)

            Glide.with(itemView.context)
                .load(msg.senderAvatar)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .circleCrop()
                .into(imgAvatar)
        }
    }
}