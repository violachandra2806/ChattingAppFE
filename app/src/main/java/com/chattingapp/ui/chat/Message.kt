package com.chattingapp.ui.chat

import java.io.Serializable

data class Message(
        val messageId: String,
        val roomId: String,
        val senderId: String,
        val messageType: String, // "text", "voice", "video"
        val content: String?, //  text
        val mediaUrl: String?, //  voice/video
        val durationSec: Int?, //  voice note
        val transcriptText: String?,
        val sentAt: String
) : Serializable