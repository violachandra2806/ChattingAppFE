package com.chattingapp.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chattingapp.R

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
        holder.tvLastMessage.text = chatRoom.lastMessage
        holder.tvTime.text = chatRoom.time
        holder.tvUnreadCount.text = chatRoom.unreadCount.toString()
        holder.tvUnreadCount.visibility = if (chatRoom.unreadCount > 0) View.VISIBLE else View.GONE

        // Load profile picture with Glide or Picasso
        Glide.with(holder.itemView.context)
            .load(chatRoom.profilePicture)
            .placeholder(R.drawable.ic_person_placeholder)
            .into(holder.profileImage)

        holder.itemView.setOnClickListener {
            onClick(chatRoom)
        }
    }

    override fun getItemCount(): Int = chatRooms.size
}