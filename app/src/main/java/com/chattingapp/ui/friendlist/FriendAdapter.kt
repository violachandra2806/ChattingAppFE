package com.chattingapp.ui.friendlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R

class FriendAdapter(
    private var displayedList: List<Friend>,
    private val onChatClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: TextView = itemView.findViewById(R.id.avatar)
        val username: TextView = itemView.findViewById(R.id.username)
        val chatIcon: ImageView = itemView.findViewById(R.id.chatIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = displayedList[position]
        holder.avatar.text = friend.initials
        holder.avatar.background.setTint(friend.color)
        holder.username.text = friend.username

        holder.chatIcon.setOnClickListener {
            onChatClick(friend)
        }
    }

    override fun getItemCount(): Int = displayedList.size

    fun updateList(newList: List<Friend>) {
        displayedList = newList
        notifyDataSetChanged()
    }
}
