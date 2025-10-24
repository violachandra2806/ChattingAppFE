package com.chattingapp.ui.friendlist

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R
import com.chattingapp.ui.addfriend.AddFriendActivity
import com.chattingapp.ui.friendrequest.FriendRequestActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.BuildConfig

class FriendListActivity : AppCompatActivity() {

    private lateinit var adapter: FriendAdapter
    private lateinit var friendList: List<Friend>

    private fun loadFriendList(userId: String) {
        val url = "${BuildConfig.BASE_URL}getuserfriends?user_id=$userId&limit=50&page=1"

        val requestQueue = Volley.newRequestQueue(this)

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    val status = response.getString("status")
                    if (status == "success") {
                        val dataArray = response.getJSONArray("data")
                        val friends = mutableListOf<Friend>()

                        for (i in 0 until dataArray.length()) {
                            val obj = dataArray.getJSONObject(i)
                            val username = obj.getString("friend_username")
                            val initials = username.take(2).uppercase()
                            val color = Color.parseColor("#${(100000..999999).random()}")

                            friends.add(Friend(username, initials, color))
                        }

                        friendList = friends
                        adapter.updateList(friendList)
                    } else {
                        Toast.makeText(this, "Gagal memuat teman", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Parsing error", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                error.printStackTrace()
                Toast.makeText(this, "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
            }
        )

        requestQueue.add(jsonObjectRequest)
    }

    private fun loadFriendRequestCount(userId: String) {
        val url = "${BuildConfig.BASE_URL}getfriendrequests?receiver=$userId&limit=1&page=1"

        val requestQueue = Volley.newRequestQueue(this)

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    if (response.getString("status") == "success") {
                        val count = response.optInt("count", 0)
                        val badge = findViewById<TextView>(R.id.textRequestCount)
                        badge.text = count.toString()
                        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error ->
                error.printStackTrace()
            }
        )

        requestQueue.add(jsonObjectRequest)
    }

    override fun onResume() {
        super.onResume()
        val sharedPref = getSharedPreferences("UserData", MODE_PRIVATE)
        val userId = sharedPref.getString("user_id", null)
        userId?.let {
            loadFriendRequestCount(it)
            loadFriendList(it)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_list)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val inputSearch = findViewById<TextInputEditText>(R.id.inputSearchFriend)
        val sendButton = findViewById<ImageView>(R.id.iconSendSearch)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        val cardFriendRequest = findViewById<MaterialCardView>(R.id.cardFriendRequest)
        val btnAddFriend = findViewById<MaterialButton>(R.id.buttonAddFriend)

        cardFriendRequest.setOnClickListener {
            startActivity(Intent(this, FriendRequestActivity::class.java))
        }

        btnAddFriend.setOnClickListener {
            startActivity(Intent(this, AddFriendActivity::class.java))
        }

        bottomNav.selectedItemId = R.id.navigation_dashboard

        adapter = FriendAdapter(emptyList()) { friend ->
            Toast.makeText(this, "Chat dengan ${friend.username}", Toast.LENGTH_SHORT).show()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val sharedPref = getSharedPreferences("UserData", MODE_PRIVATE)
        val userId = sharedPref.getString("user_id", null)

        if (userId != null) {
            loadFriendList(userId)
            loadFriendRequestCount(userId)
        } else {
            Toast.makeText(this, "User belum login", Toast.LENGTH_SHORT).show()
        }

        fun performSearch() {
            val query = inputSearch.text.toString().trim().lowercase()
            if (query.isNotEmpty()) {
                val filtered = friendList.filter { it.username.lowercase().contains(query) }

                if (filtered.isNotEmpty()) {
                    adapter.updateList(filtered)
                } else {
                    Toast.makeText(this, "Tidak ditemukan", Toast.LENGTH_SHORT).show()
                    adapter.updateList(emptyList())
                }
            } else {
                adapter.updateList(friendList)
            }
        }

        inputSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                performSearch()
                true
            } else false
        }

        sendButton.setOnClickListener {
            performSearch()
        }
    }
}
