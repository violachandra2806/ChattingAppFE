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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.BuildConfig
import com.chattingapp.databinding.FragmentDashboardBinding
import com.chattingapp.utils.SharedPreferencesManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val chatRooms = mutableListOf<ChatRoom>()
    private lateinit var adapter: ChatRoomAdapter

    private var currentPage = 1
    private val limit = 10
    private var isLoading = false
    private var hasMore = true
    private var currentUserId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        // FIX: Check both SharedPreferences locations
        val sharedPreferencesManager = SharedPreferencesManager(requireContext())
        currentUserId = sharedPreferencesManager.getUserId() ?: ""

        // If not found in SharedPreferencesManager, check the old location
        if (currentUserId.isEmpty()) {
            val oldSharedPref = requireContext().getSharedPreferences("UserData", Context.MODE_PRIVATE)
            currentUserId = oldSharedPref.getString("user_id", "") ?: ""
            Log.d("Dashboard", "Falling back to UserData SharedPreferences: $currentUserId")
        }

        Log.d("Dashboard", "Final Current User ID: $currentUserId")

        if (currentUserId.isEmpty()) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
            return binding.root
        }

        setupRecyclerView()
        setupSearch()
        fetchChatRooms()

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = ChatRoomAdapter(chatRooms) { chatRoom ->
            openChatRoom(chatRoom)
        }

        binding.recyclerChatRooms.adapter = adapter
        binding.recyclerChatRooms.layoutManager = LinearLayoutManager(requireContext())

        // Add scroll listener for pagination
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
                        // Load next page
                        currentPage++
                        fetchChatRooms()
                    }
                }
            }
        })
    }

    private fun setupSearch() {
        // Remove the automatic search on editor action
        binding.searchChat.setOnEditorActionListener(null)

        // Set up search button click listener
        binding.btnSearch.setOnClickListener {
            performSearch()
        }

        // Optional: Also allow search when pressing enter
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
        // Hide keyboard
        val inputMethodManager = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.searchChat.windowToken, 0)

        // Reset to first page when searching
        currentPage = 1
        hasMore = true
        chatRooms.clear()
        adapter.notifyDataSetChanged()
        fetchChatRooms()
    }

    private fun fetchChatRooms() {
        if (isLoading) return

        isLoading = true
        binding.progressBar.isVisible = currentPage == 1
        binding.progressBarBottom.isVisible = currentPage > 1

        val searchQuery = binding.searchChat.text.toString().trim()
        var url = "${BuildConfig.BASE_URL}/getchatroomlist?user_id=$currentUserId&page=$currentPage&limit=$limit"

        if (searchQuery.isNotEmpty()) {
            url += "&search=$searchQuery"
        }

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                isLoading = false
                binding.progressBar.isVisible = false
                binding.progressBarBottom.isVisible = false

                try {
                    // Check if response is successful
                    if (response.getString("status") == "success" && response.getInt("code") == 0) {
                        val dataObject = response.getJSONObject("data")
                        val chatRoomsArray = dataObject.getJSONArray("chat_rooms")
                        val newChatRooms = mutableListOf<ChatRoom>()

                        for (i in 0 until chatRoomsArray.length()) {
                            val item = chatRoomsArray.getJSONObject(i)
                            val friendObject = item.getJSONObject("friend")

                            newChatRooms.add(
                                ChatRoom(
                                    id = item.getString("room_id"),
                                    username = friendObject.getString("username"),
                                    profilePicture = friendObject.optString("profile_picture", ""),
                                    lastMessage = item.getString("last_message"),
                                    time = formatTime(item.getString("last_message_at")),
                                    unreadCount = 0, // You can adjust this based on your backend response
                                    userIdFirst = currentUserId,
                                    userIdSecond = friendObject.getString("user_id"),
                                    lastMessageAt = item.getString("last_message_at")
                                )
                            )
                        }

                        if (currentPage == 1) {
                            chatRooms.clear()
                        }

                        if (newChatRooms.size < limit) {
                            hasMore = false
                        }

                        chatRooms.addAll(newChatRooms)
                        adapter.notifyDataSetChanged()

                        // Show no data message if no chat rooms
                        updateEmptyState()
                    } else {
                        handleError("Failed to load chat rooms: ${response.optString("message", "Unknown error")}")
                    }
                } catch (e: Exception) {
                    handleError("Failed to parse response: ${e.message}")
                }
            },
            { error ->
                isLoading = false
                binding.progressBar.isVisible = false
                binding.progressBarBottom.isVisible = false
                handleError("Failed to load chat rooms: ${error.message}")
            })

        Volley.newRequestQueue(requireContext()).add(request)
    }

    private fun formatTime(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            // If parsing fails, try to extract time from the string or return a default
            try {
                dateString.split(" ").getOrNull(4)?.substring(0, 5) ?: "00:00"
            } catch (e2: Exception) {
                "00:00"
            }
        }
    }

    private fun updateEmptyState() {
        if (chatRooms.isEmpty()) {
            binding.tvNoData.isVisible = true
            binding.recyclerChatRooms.isVisible = false
        } else {
            binding.tvNoData.isVisible = false
            binding.recyclerChatRooms.isVisible = true
        }
    }

    private fun handleError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        updateEmptyState()
    }

    private fun openChatRoom(chatRoom: ChatRoom) {
        val intent = Intent(requireContext(), ChatRoomActivity::class.java).apply {
            putExtra("room_id", chatRoom.id)
            putExtra("user_id_first", chatRoom.userIdFirst)
            putExtra("user_id_second", chatRoom.userIdSecond)
            putExtra("other_user_name", chatRoom.username)
            putExtra("profile_picture", chatRoom.profilePicture)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}