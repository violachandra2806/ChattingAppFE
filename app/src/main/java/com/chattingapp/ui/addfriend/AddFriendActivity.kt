package com.chattingapp.ui.addfriend

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chattingapp.R
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import com.chattingapp.BuildConfig

class AddFriendActivity : AppCompatActivity() {

    private lateinit var adapter: AddFriendAdapter
    private lateinit var textSearchInfo: TextView

    private val currentUserId = "U00004" // TODO: ambil dari session / login aktif

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_friend)

        val inputSearch = findViewById<TextInputEditText>(R.id.inputSearchUsername)
        val iconSend = findViewById<ImageView>(R.id.iconSendSearchUsername)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewResults)
        textSearchInfo = findViewById(R.id.textSearchInfo)

        adapter = AddFriendAdapter(mutableListOf()) { friend ->
            sendFriendRequest(friend.userId)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fun performSearch() {
            val query = inputSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                searchFriendByUsername(query)
            } else {
                textSearchInfo.visibility = TextView.GONE
                adapter.updateList(mutableListOf())
            }
        }

        inputSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                performSearch()
                true
            } else {
                false
            }
        }

        iconSend.setOnClickListener { performSearch() }
    }

    private fun searchFriendByUsername(keyword: String) {
        thread {
            try {
                val url = URL("${BuildConfig.BASE_URL}searchfriendbyusername?keyword=$keyword")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val data = json.getJSONArray("data")

                val friends = mutableListOf<FriendResult>()
                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    friends.add(
                        FriendResult(
                            userId = obj.getString("user_id"),
                            username = obj.getString("username"),
                            profilePicture = obj.optString("profile_picture", null)
                        )
                    )
                }

                runOnUiThread {
                    adapter.updateList(friends)
                    textSearchInfo.visibility = TextView.VISIBLE
                    textSearchInfo.text = "Ditemukan ${friends.size} hasil untuk \"$keyword\""
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Gagal mencari teman", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendFriendRequest(receiverId: String) {
        thread {
            try {
                val url = URL("${BuildConfig.BASE_URL}sendfriendrequest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true

                val payload = JSONObject()
                payload.put("sender_id", currentUserId)
                payload.put("receiver_id", receiverId)

                val output = conn.outputStream
                output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                output.flush()
                output.close()

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                runOnUiThread {
                    if (json.getString("status") == "success") {
                        Toast.makeText(this, "Permintaan pertemanan dikirim!", Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = json.optString("message", "Gagal mengirim permintaan")
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Terjadi kesalahan jaringan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
