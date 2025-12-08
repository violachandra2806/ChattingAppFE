package com.chattingapp.ui.chat

sealed class ChatItem {
    data class DateHeader(val date: String) : ChatItem()
    data class MessageItem(val message: Message) : ChatItem()
}
