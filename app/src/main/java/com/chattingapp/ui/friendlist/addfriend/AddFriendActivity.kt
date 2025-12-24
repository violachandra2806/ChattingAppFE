package com.chattingapp.ui.friendlist.addfriend

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import com.chattingapp.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import com.chattingapp.BuildConfig

class AddFriendActivity : AppCompatActivity() {

    private lateinit var adapter: AddFriendAdapter
    private lateinit var textSearchInfo: TextView
    private var loadingOverlay: View? = null

    private val currentUserId: String by lazy {
        val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
        prefs.getString("user_id", "") ?: ""
    }


    override fun onCreate(savedInstanceState: Bundle?) {

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_login_session_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_friend)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val inputSearch = findViewById<EditText>(R.id.inputSearchUsername)
        val iconSend = findViewById<ImageView>(R.id.iconSendSearchUsername)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewResults)
        textSearchInfo = findViewById(R.id.textSearchInfo)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        btnBack.setOnClickListener { finish() }

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
        runOnUiThread { setLoading(true) }
        thread {
            try {
                val url = URL("${BuildConfig.BASE_URL}searchfriendbyusername?user_id=$currentUserId&keyword=$keyword&limit=10&page=1")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.msg_server_error_with_code, responseCode), Toast.LENGTH_SHORT).show()
                        setLoading(false)
                    }
                    return@thread
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                if (json.getString("status") != "success") {
                    runOnUiThread {
                        val message = json.optString("message", getString(R.string.msg_no_results_found))
                        textSearchInfo.text = message
                        textSearchInfo.visibility = TextView.VISIBLE
                        adapter.updateList(mutableListOf())
                        setLoading(false)
                    }
                    return@thread
                }

                val data = json.getJSONArray("data")
                val friends = mutableListOf<FriendResult>()
                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    friends.add(
                        FriendResult(
                            userId = obj.getString("user_id"),
                            username = obj.getString("username"),
                            profilePicture = obj.optString("profile_picture", null),
                            status = obj.optString("status", "not connected")
                        )
                    )
                }

                runOnUiThread {
                    if (friends.isEmpty()) {
                        textSearchInfo.text = getString(R.string.msg_no_results_for, keyword)
                        adapter.updateList(mutableListOf())
                    } else {
                        textSearchInfo.text = getString(R.string.msg_found_results_for, friends.size, keyword)
                        adapter.updateList(friends)
                    }
                    textSearchInfo.visibility = TextView.VISIBLE
                    setLoading(false)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.msg_search_friend_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay?.visibility = if (isLoading) View.VISIBLE else View.GONE
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
                        Toast.makeText(this, getString(R.string.msg_friend_request_sent), Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = json.optString("message", getString(R.string.msg_friend_request_send_failed))
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.msg_network_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
