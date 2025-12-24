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
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R
import com.chattingapp.ui.friendlist.addfriend.AddFriendActivity
import com.chattingapp.ui.friendlist.friendrequest.FriendRequestActivity
import com.chattingapp.ui.chat.ChatRoomActivity
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
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
    private var inputSearch: EditText? = null
    private var sendButton: ImageView? = null
    private var cardFriendRequest: CardView? = null
    private var btnAddFriend: Button? = null
    private var badge: TextView? = null
    private var progressBar: View? = null

    private var requestQueue: RequestQueue? = null
    private val volleyTag = "FriendListFragment"

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

        requestQueue = Volley.newRequestQueue(requireContext())

        setupRecyclerView()
        setupClickListeners()
        loadUserData()
    }

    override fun onDestroyView() {
        requestQueue?.cancelAll(volleyTag)
        requestQueue = null

        recyclerView = null
        inputSearch = null
        sendButton = null
        cardFriendRequest = null
        btnAddFriend = null
        badge = null
        progressBar = null

        super.onDestroyView()
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
            context?.let { Toast.makeText(it, getString(R.string.msg_user_not_logged_in), Toast.LENGTH_SHORT).show() }
        }
    }

    private fun loadFriendList(userId: String) {
        val url = "${BuildConfig.BASE_URL}getuserfriends?user_id=$userId&limit=50&page=1"
        val queue = requestQueue ?: return

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            Response.Listener { response ->
                if (!isAdded) return@Listener
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
                        context?.let { Toast.makeText(it, getString(R.string.msg_failed_load_friends), Toast.LENGTH_SHORT).show() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    context?.let { Toast.makeText(it, getString(R.string.msg_parsing_error_data), Toast.LENGTH_SHORT).show() }
                }
            },
            Response.ErrorListener { error ->
                error.printStackTrace()
                val ctx = context ?: return@ErrorListener
                Toast.makeText(ctx, getString(R.string.msg_server_connection_failed), Toast.LENGTH_SHORT).show()
            }
        )

        jsonObjectRequest.tag = volleyTag
        queue.add(jsonObjectRequest)
    }

    private fun createOrGetChatRoom(friend: Friend) {
        val url = "${BuildConfig.BASE_URL}createorgetchatroom"
        val queue = requestQueue ?: return

        val jsonBody = JSONObject().apply {
            put("user_id_first", currentUserId)
            put("user_id_second", friend.userId)
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            url,
            jsonBody,
            Response.Listener { response ->
                if (!isAdded) return@Listener
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
                        context?.let {
                            Toast.makeText(
                                it,
                                getString(R.string.msg_failed_with_reason, response.optString("message")),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    context?.let { Toast.makeText(it, getString(R.string.msg_error_with_reason, e.message ?: ""), Toast.LENGTH_SHORT).show() }
                }
            },
            Response.ErrorListener { error ->
                val ctx = context ?: return@ErrorListener
                Toast.makeText(
                    ctx,
                    getString(R.string.msg_server_connection_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        request.tag = volleyTag
        queue.add(request)
    }

    private fun loadFriendRequestCount(userId: String) {
        // Use a sufficiently large limit because some backends return `count` == returned data size.
        // We still prefer the server-provided `count` field when present.
        val url = "${BuildConfig.BASE_URL}getfriendrequests?receiver=$userId&limit=50&page=1"
        val queue = requestQueue ?: return

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            Response.Listener { response ->
                if (!isAdded) return@Listener
                try {
                    if (response.getString("status") == "success") {
                        val countFromResponse = when (val raw = response.opt("count")) {
                            is Number -> raw.toInt()
                            is String -> raw.toIntOrNull() ?: 0
                            else -> 0
                        }
                        val count = if (countFromResponse > 0) {
                            countFromResponse
                        } else {
                            // Fallback if backend doesn't provide count or sends a non-int.
                            response.optJSONArray("data")?.length() ?: 0
                        }
                        badge?.text = count.toString()
                        badge?.visibility = if (count > 0) View.VISIBLE else View.GONE
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            Response.ErrorListener { error ->
                error.printStackTrace()
            }
        )

        jsonObjectRequest.tag = volleyTag
        queue.add(jsonObjectRequest)
    }

    private fun performSearch() {
        val query = inputSearch?.text?.toString()?.trim()?.lowercase() ?: ""
        if (query.isNotEmpty()) {
            val filtered = friendList.filter { it.username.lowercase().contains(query) }

            if (filtered.isNotEmpty()) {
                adapter.updateList(filtered)
            } else {
                context?.let { Toast.makeText(it, getString(R.string.msg_not_found), Toast.LENGTH_SHORT).show() }
                adapter.updateList(emptyList())
            }
        } else {
            adapter.updateList(friendList)
        }
    }
}