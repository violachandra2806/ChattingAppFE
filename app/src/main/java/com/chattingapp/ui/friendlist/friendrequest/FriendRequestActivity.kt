package com.chattingapp.ui.friendlist.friendrequest

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.R
import com.chattingapp.ui.friendlist.addfriend.AddFriendActivity
import org.json.JSONObject
import com.chattingapp.BuildConfig
import android.widget.Button

class FriendRequestActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var adapter: FriendRequestAdapter
    private lateinit var queue: RequestQueue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_request)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnAddFriend = findViewById<Button>(R.id.buttonAddFriend)
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.tvEmptyState)
        recyclerView.layoutManager = LinearLayoutManager(this)
        queue = Volley.newRequestQueue(this)

        btnBack.setOnClickListener { finish() }
        btnAddFriend.setOnClickListener {
            val intent = Intent(this, AddFriendActivity::class.java)
            startActivity(intent)
        }

        val sharedPref = getSharedPreferences("UserData", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("user_id", null)

        if (userId == null) {
            Toast.makeText(this, getString(R.string.msg_user_id_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        getFriendRequests(userId)
    }

    private fun setEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun getFriendRequests(userId: String) {
        val url = "${BuildConfig.BASE_URL}getfriendrequests?receiver=$userId"

        val randomColor = Color.rgb((0..255).random(), (0..255).random(), (0..255).random())

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val status = response.getString("status")
                    if (status == "success") {
                        val data = response.getJSONArray("data")
                        val requests = mutableListOf<FriendRequest>()

                        for (i in 0 until data.length()) {
                            val obj = data.getJSONObject(i)
                            val requestId = obj.getString("request_id")
                            val senderId = obj.getString("sender_id")
                            val senderName = obj.getString("sender_username")

                            requests.add(
                                FriendRequest(
                                    requestId = requestId,
                                    senderId = senderId,
                                    initials = senderName.take(2).uppercase(),
                                    username = senderName,
                                    color = randomColor
                                )
                            )

                        }

                        if (requests.isEmpty()) {
                            setEmptyState(true)
                        } else {
                            setEmptyState(false)
                            adapter = FriendRequestAdapter(
                                requests,
                                onAccept = { req -> acceptFriendRequest(req.requestId, req.senderId, userId) },
                                onReject = { req -> rejectFriendRequest(req.requestId) }
                            )
                            recyclerView.adapter = adapter
                        }

                    } else {
                        setEmptyState(true)
                        Toast.makeText(this, getString(R.string.msg_failed_fetch_data), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("FriendReq", "Parsing error: ${e.message}")
                    setEmptyState(true)
                }
            },
            { error ->
                Log.e("FriendReq", "Volley error: ${error.message}")
                setEmptyState(true)
                Toast.makeText(this, getString(R.string.msg_network_error_en), Toast.LENGTH_SHORT).show()
            }
        )

        queue.add(request)
    }

    private fun acceptFriendRequest(requestId: String, senderId: String, receiverId: String) {
        val url = "${BuildConfig.BASE_URL}acceptfriendrequest"
        val json = JSONObject().apply {
            put("request_id", requestId)
            put("sender_id", senderId)
            put("receiver_id", receiverId)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, json,
            { response ->
                val status = response.optString("status")
                if (status == "success") {
                    Toast.makeText(this, getString(R.string.msg_friend_request_accepted), Toast.LENGTH_SHORT).show()
                    getFriendRequests(receiverId)
                } else {
                    Toast.makeText(this, getString(R.string.msg_friend_request_accept_failed), Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("FriendReq", "Accept error: ${error.message}")
                Toast.makeText(this, getString(R.string.msg_network_error_en), Toast.LENGTH_SHORT).show()
            }
        )

        queue.add(request)
    }

    private fun rejectFriendRequest(requestId: String) {
        val url = "${BuildConfig.BASE_URL}rejectfriendrequest"
        val json = JSONObject().apply {
            put("request_id", requestId)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, json,
            { response ->
                val status = response.optString("status")
                if (status == "success") {
                    Toast.makeText(this, getString(R.string.msg_friend_request_rejected), Toast.LENGTH_SHORT).show()
                    // Refresh list
                    val sharedPref = getSharedPreferences("UserData", Context.MODE_PRIVATE)
                    val userId = sharedPref.getString("user_id", null)
                    userId?.let { getFriendRequests(it) }
                } else {
                    Toast.makeText(this, getString(R.string.msg_friend_request_reject_failed), Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("FriendReq", "Reject error: ${error.message}")
                Toast.makeText(this, getString(R.string.msg_network_error_en), Toast.LENGTH_SHORT).show()
            }
        )

        queue.add(request)
    }
}
