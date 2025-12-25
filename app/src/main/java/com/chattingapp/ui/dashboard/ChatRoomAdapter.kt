package com.chattingapp.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R
import com.chattingapp.utils.AvatarUtils

class ChatRoomAdapter(
    private val chatRooms: List<ChatRoom>,
    private val onClick: (ChatRoom) -> Unit
) : RecyclerView.Adapter<ChatRoomAdapter.ChatRoomViewHolder>() {

    inner class ChatRoomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvLastMessage: TextView = view.findViewById(R.id.tvLastMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvUnreadCount: TextView = view.findViewById(R.id.tvUnreadCount)
        val profileImage: ImageView = view.findViewById(R.id.profileImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_room, parent, false)
        return ChatRoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        val chatRoom = chatRooms[position]
        holder.tvUsername.text = chatRoom.username

        val lastMessageText = chatRoom.lastMessage.trim()
        val hasLastMessage = lastMessageText.isNotBlank() && !lastMessageText.equals("null", true)
        val hasLastMessageAt = chatRoom.lastMessageAt.isNotBlank() && !chatRoom.lastMessageAt.equals("null", true)
        val shouldShowPlaceholder = !hasLastMessage

        if (shouldShowPlaceholder) {
            holder.tvLastMessage.text = holder.itemView.context.getString(R.string.label_message_first)
            holder.tvTime.visibility = View.GONE
        } else {
            holder.tvLastMessage.text = lastMessageText

            val timeText = chatRoom.time
            if (timeText.isBlank()) {
                holder.tvTime.visibility = View.GONE
            } else {
                holder.tvTime.visibility = View.VISIBLE
                holder.tvTime.text = timeText
            }
        }

        holder.tvUnreadCount.text = chatRoom.unreadCount.toString()
        holder.tvUnreadCount.visibility = if (chatRoom.unreadCount > 0) View.VISIBLE else View.GONE

        // Load profile picture; fallback to initial with random (stable) background
        AvatarUtils.loadInto(holder.profileImage, chatRoom.profilePicture, chatRoom.username)

        holder.itemView.setOnClickListener {
            onClick(chatRoom)
        }
    }

    override fun getItemCount(): Int = chatRooms.size
}