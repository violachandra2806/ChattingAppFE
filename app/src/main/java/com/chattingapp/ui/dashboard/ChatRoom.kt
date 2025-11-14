package com.chattingapp.ui.dashboard

data class ChatRoom(
    val id: String,
    val username: String,
    val profilePicture: String?,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int
)