package com.chattingapp.ui.friendlist.friendrequest

data class FriendRequest(
    val requestId: String,
    val senderId: String,
    val initials: String,
    val username: String,
    val color: Int
)
