package com.chattingapp.ui.friendlist

data class Friend(
    val userId: String,
    val username: String,
    val profilePicture: String?,
    val initials: String,
    val color: Int
)