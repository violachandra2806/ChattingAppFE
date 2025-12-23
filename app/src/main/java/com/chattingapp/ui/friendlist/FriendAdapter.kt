package com.chattingapp.ui.friendlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chattingapp.R

class FriendAdapter(
    private var displayedList: List<Friend>,
    private val onChatClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val username: TextView = itemView.findViewById(R.id.username)
        val chatIcon: ImageView = itemView.findViewById(R.id.chatIcon)
        val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = displayedList[position]
        holder.username.text = friend.username

        // Load profile picture
        Glide.with(holder.itemView.context)
            .load(friend.profilePicture)
            .placeholder(R.drawable.ic_person_placeholder)
            .into(holder.profileImage)

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