package com.chattingapp.ui.addfriend

data class FriendResult(
    val userId: String,
    val username: String,
    val profilePicture: String? = null,
    var status: String
) {
    val requested: Boolean
        get() = status == "requested"
}

