package com.chattingapp.ui.dashboard

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.chattingapp.R

class ChatRoomActivity : AppCompatActivity() {

    private lateinit var tvChatTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)

        val roomId = intent.getStringExtra("room_id")
        val userIdFirst = intent.getStringExtra("user_id_first")
        val userIdSecond = intent.getStringExtra("user_id_second")
        val otherUserName = intent.getStringExtra("other_user_name")

        // Set up toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Set up the chat room UI
        tvChatTitle = findViewById(R.id.tvChatTitle)
        tvChatTitle.text = otherUserName ?: "Chat"

        // You can now use roomId, userIdFirst, userIdSecond to fetch messages
        // For example:
        // fetchMessages(roomId!!)

        // Implement your chat functionality here
        // You'll need to fetch messages for this room_id and set up real-time messaging
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // Example method to fetch messages
    private fun fetchMessages(roomId: String) {
        // Implement your API call to get messages for this room
        // val url = "https://your-api.com/getmessages?room_id=$roomId"
    }
}