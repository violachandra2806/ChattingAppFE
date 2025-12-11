package com.chattingapp.ui.chat

import java.io.Serializable

data class Message(
        val messageId: String,
        val roomId: String,
        val senderId: String,
        val messageType: String = "text",
        val content: String? = null,
        val mediaUrl: String? = null,
        val durationSec: Int? = null,
        val transcriptText: String? = null,
        val sentAt: String,
        val sentAtRaw: String = "",
        var isTranscribing: Boolean = false,
        val translateYN: String? = null,
        val frameRate: Int? = null,
        val resolution: String? = null
) : Serializable
