package com.chattingapp.ui.friendlist.addfriend

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R
import com.chattingapp.utils.AvatarUtils

class AddFriendAdapter(
    private val results: MutableList<FriendResult>,
    private val onRequestSent: (FriendResult) -> Unit
) : RecyclerView.Adapter<AddFriendAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val username: TextView = itemView.findViewById(R.id.username)
        val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
        val btnAdd: Button = itemView.findViewById(R.id.btnAddFriend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_add_friend_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val friend = results[position]
        holder.username.text = friend.username

        // Load profile picture; fallback to initial with random (stable) background
        AvatarUtils.loadInto(holder.profileImage, friend.profilePicture, friend.username)

        if (friend.requested) {
            holder.btnAdd.text = "Menunggu"
            holder.btnAdd.isEnabled = false
            holder.btnAdd.alpha = 0.6f
            holder.btnAdd.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_clock,
                0,
                0,
                0
            )
        } else {
            holder.btnAdd.text = "Tambah teman"
            holder.btnAdd.isEnabled = true
            holder.btnAdd.alpha = 1f
            holder.btnAdd.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_add,
                0,
                0,
                0
            )
        }


        holder.btnAdd.setOnClickListener {
            friend.status = "requested"
            notifyItemChanged(position)
            onRequestSent(friend)
        }

    }

    override fun getItemCount() = results.size

    fun updateList(newList: MutableList<FriendResult>) {
        results.clear()
        results.addAll(newList)
        notifyDataSetChanged()
    }
}
