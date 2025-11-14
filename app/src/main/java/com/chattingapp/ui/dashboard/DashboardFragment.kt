package com.chattingapp.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val chatRooms = mutableListOf<ChatRoom>()
    private lateinit var adapter: ChatRoomAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        setupRecyclerView()
        fetchChatRooms()

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = ChatRoomAdapter(chatRooms) { chatRoom ->
            // handle click, e.g. open chat detail
        }
        binding.recyclerChatRooms.adapter = adapter
        binding.recyclerChatRooms.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun fetchChatRooms() {
        val url = "https://your-api.com/getchatroomlist"

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                chatRooms.clear()
                val dataArray = response.getJSONArray("data")
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    chatRooms.add(
                        ChatRoom(
                            id = item.getString("id"),
                            username = item.getString("username"),
                            profilePicture = item.optString("profile_picture", null),
                            lastMessage = item.getString("last_message"),
                            time = item.getString("time"),
                            unreadCount = item.getInt("unread_count")
                        )
                    )
                }
                adapter.notifyDataSetChanged()
            },
            { error ->
                Toast.makeText(requireContext(), "Failed to load chat rooms", Toast.LENGTH_SHORT).show()
            })

        Volley.newRequestQueue(requireContext()).add(request)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
