package com.chattingapp.ui.friendlist.friendrequest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R
import com.chattingapp.utils.AvatarUtils

class FriendRequestAdapter(
    private var requests: List<FriendRequest>,
    private val onAccept: (FriendRequest) -> Unit,
    private val onReject: (FriendRequest) -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder>() {

    inner class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
        val username: TextView = itemView.findViewById(R.id.username)
        val btnAccept: ImageView = itemView.findViewById(R.id.btnAccept)
        val btnReject: ImageView = itemView.findViewById(R.id.btnReject)

        init {
            itemView.setOnClickListener(null)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]
        holder.username.text = "@${request.username}"

        // No profile picture in this model; always fallback to initial with random (stable) background
        AvatarUtils.loadInto(holder.profileImage, null, request.username)

        holder.btnAccept.setOnClickListener { onAccept(request) }
        holder.btnReject.setOnClickListener { onReject(request) }
    }

    override fun getItemCount(): Int = requests.size

    fun updateList(newList: List<FriendRequest>) {
        requests = newList
        notifyDataSetChanged()
    }
}
