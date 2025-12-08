package com.chattingapp.ui.friendlist

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R
import com.chattingapp.ui.friendlist.addfriend.AddFriendActivity
import com.chattingapp.ui.friendlist.friendrequest.FriendRequestActivity
import com.chattingapp.ui.chat.ChatRoomActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.BuildConfig
import com.chattingapp.utils.SharedPreferencesManager
import org.json.JSONObject

class FriendListFragment : Fragment() {

    private lateinit var adapter: FriendAdapter
    private var friendList = listOf<Friend>()
    private var currentUserId: String = ""

    private var recyclerView: RecyclerView? = null
    private var inputSearch: TextInputEditText? = null
    private var sendButton: ImageView? = null
    private var cardFriendRequest: MaterialCardView? = null
    private var btnAddFriend: MaterialButton? = null
    private var badge: TextView? = null
    private var progressBar: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_friend_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get current user ID
        val sharedPreferencesManager = SharedPreferencesManager(requireContext())
        currentUserId = sharedPreferencesManager.getUserId() ?: ""

        if (currentUserId.isEmpty()) {
            val sharedPref = requireActivity().getSharedPreferences("UserData", android.content.Context.MODE_PRIVATE)
            currentUserId = sharedPref.getString("user_id", "") ?: ""
        }

        try {
            recyclerView = view.findViewById(R.id.recyclerView)
            inputSearch = view.findViewById(R.id.inputSearchFriend)
            sendButton = view.findViewById(R.id.iconSendSearch)
            cardFriendRequest = view.findViewById(R.id.cardFriendRequest)
            btnAddFriend = view.findViewById(R.id.buttonAddFriend)
            badge = view.findViewById(R.id.textRequestCount)

            Log.d("FriendListFragment", "RecyclerView initialized: ${recyclerView != null}")
        } catch (e: Exception) {
            Log.e("FriendListFragment", "Error initializing views: ${e.message}")
        }

        setupRecyclerView()
        setupClickListeners()
        loadUserData()
    }

    override fun onResume() {
        super.onResume()
        if (currentUserId.isNotEmpty()) {
            loadFriendRequestCount(currentUserId)
            loadFriendList(currentUserId)
        }
    }

    private fun setupRecyclerView() {
        val currentRecyclerView = recyclerView
        if (currentRecyclerView == null) {
            Log.e("FriendListFragment", "RecyclerView is null")
            return
        }

        adapter = FriendAdapter(emptyList()) { friend ->
            createOrGetChatRoom(friend)
        }
        currentRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        currentRecyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        cardFriendRequest?.setOnClickListener {
            startActivity(Intent(requireActivity(), FriendRequestActivity::class.java))
        }

        btnAddFriend?.setOnClickListener {
            startActivity(Intent(requireActivity(), AddFriendActivity::class.java))
        }

        inputSearch?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                performSearch()
                true
            } else false
        }

        sendButton?.setOnClickListener {
            performSearch()
        }
    }

    private fun loadUserData() {
        if (currentUserId.isNotEmpty()) {
            loadFriendList(currentUserId)
            loadFriendRequestCount(currentUserId)
        } else {
            Toast.makeText(requireContext(), "User belum login", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadFriendList(userId: String) {
        val url = "${BuildConfig.BASE_URL}getuserfriends?user_id=$userId&limit=50&page=1"
        val requestQueue = Volley.newRequestQueue(requireContext())

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
                            val friendId = obj.getString("friend_id")
                            val username = obj.getString("friend_username")
                            val profilePicture = obj.optString("friend_profile_picture", null)
                            val initials = username.take(2).uppercase()
                            val color = Color.parseColor("#${(100000..999999).random()}")

                            friends.add(Friend(friendId, username, profilePicture, initials, color))
                        }

                        friendList = friends
                        adapter.updateList(friendList)
                    } else {
                        Toast.makeText(requireContext(), "Gagal memuat teman", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Parsing error", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                error.printStackTrace()
                Toast.makeText(requireContext(), "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
            }
        )

        requestQueue.add(jsonObjectRequest)
    }

    private fun createOrGetChatRoom(friend: Friend) {
        val url = "${BuildConfig.BASE_URL}createorgetchatroom"
        val requestQueue = Volley.newRequestQueue(requireContext())

        val jsonBody = JSONObject().apply {
            put("user_id_first", currentUserId)
            put("user_id_second", friend.userId)
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            url,
            jsonBody,
            { response ->
                try {
                    if (response.getString("status") == "success") {
                        val data = response.getJSONObject("data")
                        val roomId = data.getString("room_id")

                        // Navigate to ChatRoom
                        val intent = Intent(requireContext(), ChatRoomActivity::class.java).apply {
                            putExtra("room_id", roomId)
                            putExtra("user_id_first", currentUserId)
                            putExtra("user_id_second", friend.userId)
                            putExtra("other_user_name", friend.username)
                            putExtra("other_user_photo", friend.profilePicture)
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Gagal membuka chat: ${response.optString("message")}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(
                    requireContext(),
                    "Gagal terhubung: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        requestQueue.add(request)
    }

    private fun loadFriendRequestCount(userId: String) {
        val url = "${BuildConfig.BASE_URL}getfriendrequests?receiver=$userId&limit=1&page=1"
        val requestQueue = Volley.newRequestQueue(requireContext())

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    if (response.getString("status") == "success") {
                        val count = response.optInt("count", 0)
                        badge?.text = count.toString()
                        badge?.visibility = if (count > 0) View.VISIBLE else View.GONE
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

    private fun performSearch() {
        val query = inputSearch?.text?.toString()?.trim()?.lowercase() ?: ""
        if (query.isNotEmpty()) {
            val filtered = friendList.filter { it.username.lowercase().contains(query) }

            if (filtered.isNotEmpty()) {
                adapter.updateList(filtered)
            } else {
                Toast.makeText(requireContext(), "Tidak ditemukan", Toast.LENGTH_SHORT).show()
                adapter.updateList(emptyList())
            }
        } else {
            adapter.updateList(friendList)
        }
    }
}