package com.chattingapp.ui.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RealtimeMessage(
    @SerialName("message_id") val messageId: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("message_type") val messageType: String,
    @SerialName("sent_at") val sentAt: String
)