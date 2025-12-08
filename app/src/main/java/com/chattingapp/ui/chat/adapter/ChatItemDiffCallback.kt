package com.chattingapp.ui.chat.adapter

import androidx.recyclerview.widget.DiffUtil
import com.chattingapp.ui.chat.ChatItem

class ChatItemDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
    override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return when {
            oldItem is ChatItem.DateHeader && newItem is ChatItem.DateHeader ->
                oldItem.date == newItem.date
            oldItem is ChatItem.MessageItem && newItem is ChatItem.MessageItem ->
                oldItem.message.messageId == newItem.message.messageId
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
        return oldItem == newItem
    }
}
