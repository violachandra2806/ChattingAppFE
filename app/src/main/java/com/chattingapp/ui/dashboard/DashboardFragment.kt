package com.chattingapp.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.chattingapp.utils.CryptoUtils
import com.chattingapp.databinding.FragmentDashboardBinding
import com.chattingapp.ui.chat.ChatRoomActivity
import com.chattingapp.utils.SharedPreferencesManager
import com.chattingapp.utils.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var requestQueue: RequestQueue? = null
    private val volleyTag = "DashboardFragment"

    private val chatRooms = mutableListOf<ChatRoom>()
    private lateinit var adapter: ChatRoomAdapter

    private var currentPage = 1
    private val limit = 10
    private var isLoading = false
    private var hasMore = true
    private var currentUserId: String = ""

    // ✅ Realtime channel
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(requireContext().applicationContext)
        }

        val sharedPreferencesManager = SharedPreferencesManager(requireContext())
        currentUserId = sharedPreferencesManager.getUserId() ?: ""

        if (currentUserId.isEmpty()) {
            val oldSharedPref = requireContext().getSharedPreferences("UserData", Context.MODE_PRIVATE)
            currentUserId = oldSharedPref.getString("user_id", "") ?: ""
            Log.d("Dashboard", "Falling back to UserData SharedPreferences: $currentUserId")
        }

        Log.d("Dashboard", "Final Current User ID: $currentUserId")

        if (currentUserId.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.msg_user_not_logged_in), Toast.LENGTH_SHORT).show()
            requireActivity().finish()
            return binding.root
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (currentUserId.isEmpty()) return

        setupRecyclerView()
        setupSearch()
        fetchChatRooms()

        // ✅ Initialize realtime subscription
        initRealtimeSubscription()
    }

    private fun setupRecyclerView() {
        val binding = _binding ?: return
        adapter = ChatRoomAdapter(chatRooms) { chatRoom ->
            openChatRoom(chatRoom)
        }

        binding.recyclerChatRooms.adapter = adapter
        binding.recyclerChatRooms.layoutManager = LinearLayoutManager(requireContext())

        binding.recyclerChatRooms.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasMore && dy > 0) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0) {
                        currentPage++
                        fetchChatRooms()
                    }
                }
            }
        })
    }

    private fun setupSearch() {
        val binding = _binding ?: return
        binding.searchChat.setOnEditorActionListener(null)

        binding.btnSearch.setOnClickListener {
            performSearch()
        }

        binding.searchChat.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        val binding = _binding ?: return
        val inputMethodManager = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.searchChat.windowToken, 0)

        currentPage = 1
        hasMore = true
        chatRooms.clear()
        adapter.notifyDataSetChanged()
        fetchChatRooms()
    }

    private fun fetchChatRooms() {
        if (isLoading) return

        val binding = _binding ?: return

        isLoading = true
        binding.progressBar.isVisible = currentPage == 1
        binding.progressBarBottom.isVisible = currentPage > 1

        val searchQuery = binding.searchChat.text.toString().trim()
        var url = "${BuildConfig.BASE_URL}getchatroomlist?user_id=$currentUserId&page=$currentPage&limit=$limit"

        if (searchQuery.isNotEmpty()) {
            url += "&search=$searchQuery"
        }

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                isLoading = false

                val binding = _binding
                if (binding != null) {
                    binding.progressBar.isVisible = false
                    binding.progressBarBottom.isVisible = false
                }

                try {
                    if (response.getString("status") == "success" && response.getInt("code") == 0) {
                        val dataObject = response.getJSONObject("data")
                        val chatRoomsArray = dataObject.optJSONArray("chat_rooms")
                        val newChatRooms = mutableListOf<ChatRoom>()

                        if (chatRoomsArray != null) {
                            for (i in 0 until chatRoomsArray.length()) {
                                val item = chatRoomsArray.getJSONObject(i)
                                val friendObject = item.getJSONObject("friend")

                                newChatRooms.add(
                                    ChatRoom(
                                        id = item.getString("room_id"),
                                        username = friendObject.getString("username"),
                                        profilePicture = friendObject.optString("profile_picture", ""),
                                        lastMessage = CryptoUtils.decryptIfNeeded(item.optString("last_message", "")) ?: "",
                                        time = formatTime(item.optString("last_message_at", "")),
                                        unreadCount = 0,
                                        userIdFirst = currentUserId,
                                        userIdSecond = friendObject.getString("user_id"),
                                        lastMessageAt = item.optString("last_message_at", "")
                                    )
                                )
                            }
                        }

                        if (currentPage == 1) {
                            chatRooms.clear()
                        }

                        if (newChatRooms.size < limit) {
                            hasMore = false
                        }

                        chatRooms.addAll(newChatRooms)
                        adapter.notifyDataSetChanged()

                        updateEmptyState()
                    } else {
                        val msg = response.optString("message", "").trim()
                        if (msg.isBlank() || msg.equals("null", true)) {
                            showNoChatsState()
                        } else {
                            handleError("Failed to load chat rooms: $msg")
                        }
                    }
                } catch (e: Exception) {
                    handleError("Failed to parse response: ${e.message}")
                }
            },
            { error ->
                isLoading = false
                val binding = _binding
                if (binding != null) {
                    binding.progressBar.isVisible = false
                    binding.progressBarBottom.isVisible = false
                }
                val msg = error.message?.trim().orEmpty()
                if (msg.isBlank() || msg.equals("null", true)) {
                    showNoChatsState()
                } else {
                    handleError("Failed to load chat rooms: $msg")
                }
            })

        request.tag = volleyTag
        requestQueue?.add(request)
    }

    private fun showNoChatsState() {
        if (_binding == null) return
        if (currentPage == 1) {
            chatRooms.clear()
            hasMore = false
            adapter.notifyDataSetChanged()
        }
        updateEmptyState()
    }

    // ✅ REALTIME SUBSCRIPTION FOR CHAT_ROOM TABLE
    private fun initRealtimeSubscription() {
        val supabase = SupabaseClient.getClient(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("DashboardRealtime", "=== STARTING REALTIME SUBSCRIPTION ===")
                Log.d("DashboardRealtime", "User ID: $currentUserId")

                realtimeChannel = supabase.channel("chat-rooms-$currentUserId")

                val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "chat_room"
                    // Filter rooms where user is participant
                    filter("user_id_first", FilterOperator.EQ, currentUserId)
                }

                // Also subscribe to rooms where user is second participant
                val changeFlow2 = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "chat_room"
                    filter("user_id_second", FilterOperator.EQ, currentUserId)
                }

                // Handle changes
                changeFlow.onEach { action ->
                    handleRealtimeAction(action)
                }.launchIn(viewLifecycleOwner.lifecycleScope)

                changeFlow2.onEach { action ->
                    handleRealtimeAction(action)
                }.launchIn(viewLifecycleOwner.lifecycleScope)

                realtimeChannel!!.subscribe()
                Log.d("DashboardRealtime", "✅ Successfully subscribed to chat_room table")

            } catch (e: Exception) {
                Log.e("DashboardRealtime", "❌ Subscription error: ${e.message}", e)
            }
        }
    }

    private suspend fun handleRealtimeAction(action: PostgresAction) {
        Log.d("DashboardRealtime", "🔥 RECEIVED ACTION: ${action.javaClass.simpleName}")

        when (action) {
            is PostgresAction.Insert -> {
                val record = action.record as? Map<*, *>
                Log.d("DashboardRealtime", "📩 INSERT: $record")
                val roomId = record?.get("room_id")?.toString()?.trim('"')
                if (roomId != null) {
                    fetchAndUpdateSingleRoom(roomId)
                }
            }
            is PostgresAction.Update -> {
                val record = action.record as? Map<*, *>
                Log.d("DashboardRealtime", "🔄 UPDATE: $record")
                val roomId = record?.get("room_id")?.toString()?.trim('"')
                if (roomId != null) {
                    fetchAndUpdateSingleRoom(roomId)
                }
            }
            is PostgresAction.Delete -> {
                val oldRecord = action.oldRecord as? Map<*, *>
                Log.d("DashboardRealtime", "🗑️ DELETE: $oldRecord")
                val roomId = oldRecord?.get("room_id")?.toString()?.trim('"')
                if (roomId != null) {
                    removeRoomFromList(roomId)
                }
            }
            else -> {
                Log.d("DashboardRealtime", "❓ Unknown action: $action")
            }
        }
    }

    private suspend fun fetchAndUpdateSingleRoom(roomId: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = "${BuildConfig.BASE_URL}getchatroomlist?user_id=$currentUserId&page=1&limit=100"
                val request = okhttp3.Request.Builder().url(url).get().build()
                val client = okhttp3.OkHttpClient()
                val response = client.newCall(request).execute()

                try {
                    val body = response.body?.string() ?: ""
                    val json = org.json.JSONObject(body)

                    if (json.optString("status") == "success") {
                        val data = json.getJSONObject("data")
                        val rooms = data.getJSONArray("chat_rooms")

                        // Find the specific room
                        for (i in 0 until rooms.length()) {
                            val item = rooms.getJSONObject(i)
                            if (item.getString("room_id") == roomId) {
                                val friendObject = item.getJSONObject("friend")

                                val updatedRoom = ChatRoom(
                                    id = item.getString("room_id"),
                                    username = friendObject.getString("username"),
                                    profilePicture = friendObject.optString("profile_picture", ""),
                                    lastMessage = CryptoUtils.decryptIfNeeded(item.optString("last_message", "")) ?: "",
                                    time = formatTime(item.optString("last_message_at", "")),
                                    unreadCount = 0,
                                    userIdFirst = currentUserId,
                                    userIdSecond = friendObject.getString("user_id"),
                                    lastMessageAt = item.optString("last_message_at", "")
                                )

                                withContext(Dispatchers.Main) {
                                    updateOrInsertRoom(updatedRoom)
                                }
                                break
                            }
                        }
                    } else {}
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                Log.e("DashboardRealtime", "Error fetching room: ${e.message}", e)
            }
        }
    }

    private fun updateOrInsertRoom(room: ChatRoom) {
        val binding = _binding ?: return
        val index = chatRooms.indexOfFirst { it.id == room.id }

        if (index != -1) {
            // Update existing room
            chatRooms[index] = room
            // Sort by last_message_at (most recent first)
            chatRooms.sortByDescending { it.lastMessageAt }
            adapter.notifyDataSetChanged()
            Log.d("DashboardRealtime", "✅ Room updated: ${room.id}")
        } else {
            // Insert new room at top
            chatRooms.add(0, room)
            adapter.notifyItemInserted(0)
            binding.recyclerChatRooms.scrollToPosition(0)
            Log.d("DashboardRealtime", "➕ New room added: ${room.id}")
        }

        updateEmptyState()
    }

    private fun removeRoomFromList(roomId: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (_binding == null) return@launch
            val index = chatRooms.indexOfFirst { it.id == roomId }
            if (index != -1) {
                chatRooms.removeAt(index)
                adapter.notifyItemRemoved(index)
                Log.d("DashboardRealtime", "🗑️ Room removed: $roomId")
                updateEmptyState()
            }
        }
    }

    private fun formatTime(dateString: String): String {
        if (dateString.isBlank() || dateString.equals("null", ignoreCase = true)) return ""
        return try {
            val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("GMT")

            val outputFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))
            outputFormat.timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")

            val date = inputFormat.parse(dateString)
            if (date != null) {
                outputFormat.format(date)
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error parsing time: ${e.message}")
            try {
                dateString.split(" ").getOrNull(4)?.substring(0, 5).orEmpty()
            } catch (e2: Exception) {
                ""
            }
        }
    }

    private fun updateEmptyState() {
        val binding = _binding ?: return
        if (chatRooms.isEmpty()) {
            binding.tvNoData.isVisible = true
            binding.recyclerChatRooms.isVisible = false
        } else {
            binding.tvNoData.isVisible = false
            binding.recyclerChatRooms.isVisible = true
        }
    }

    private fun handleError(message: String) {
        val context = context
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        updateEmptyState()
    }

    private fun openChatRoom(chatRoom: ChatRoom) {
        val intent = Intent(requireContext(), ChatRoomActivity::class.java).apply {
            putExtra("room_id", chatRoom.id)
            putExtra("user_id_first", chatRoom.userIdFirst)
            putExtra("user_id_second", chatRoom.userIdSecond)
            putExtra("other_user_name", chatRoom.username)
            putExtra("other_user_photo", chatRoom.profilePicture)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        requestQueue?.cancelAll(volleyTag)

        // ✅ Unsubscribe from realtime
        try {
            lifecycleScope.launch {
                realtimeChannel?.unsubscribe()
                Log.d("DashboardRealtime", "Channel unsubscribed")
            }
        } catch (e: Exception) {
            Log.e("DashboardRealtime", "Error unsubscribing: ${e.message}", e)
        }

        _binding = null
    }
}
