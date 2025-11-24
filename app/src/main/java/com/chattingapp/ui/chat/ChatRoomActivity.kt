package com.chattingapp.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.chattingapp.databinding.ActivityChatRoomBinding
import com.chattingapp.ui.chat.adapter.MessageAdapter
import com.chattingapp.ui.chat.Message
import com.chattingapp.utils.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.MediaMetadataRetriever
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ChatRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatRoomBinding
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private val httpClient = OkHttpClient()

    // audio helpers
    private lateinit var recorderHelper: AudioRecorderHelper
    private lateinit var playerHelper: AudioPlayerHelper
    private var currentAudioFile: File? = null

    // intent extras
    private var roomId: String = ""
    private var currentUserId: String = ""
    private var otherUserId: String = ""
    private var otherUserName: String = ""

    // replace with your base server if not set in BuildConfig
    private val baseUrl: String = if (BuildConfig.BASE_URL.endsWith("/")) BuildConfig.BASE_URL else BuildConfig.BASE_URL + "/"

    // permission launcher for picking video/image
    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // handle picked media upload
            uploadMediaFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // read extras
        roomId = intent.getStringExtra("room_id") ?: ""
        currentUserId = intent.getStringExtra("user_id_first") ?: ""
        otherUserId = intent.getStringExtra("user_id_second") ?: ""
        otherUserName = intent.getStringExtra("other_user_name") ?: "Chat"

        // toolbar title (ensure toolbar exists in layout)
        binding.toolbar.title = otherUserName

        recorderHelper = AudioRecorderHelper(this)
        playerHelper = AudioPlayerHelper(this)

        setupRecycler()
        setupInput()
        loadMessages()
//      initRealtimeSubscribe()
    }

    private fun setupRecycler() {
        adapter = MessageAdapter(currentUserId, onPlayVoice = { msg, _view ->
            msg.mediaUrl?.let { url ->
                // play from URL; you can add caching logic to play local file if available
                playerHelper.playFromUrl(url)
            }
        }, onTranscribe = { msg ->
            callTranscribe(msg.messageId, msg.mediaUrl ?: "")
        })

        binding.recyclerMessages.adapter = adapter
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        binding.recyclerMessages.layoutManager = lm
    }

    private fun setupInput() {
        val et = binding.etMessage
        val btnMic = binding.btnRecordVoice
        val btnVideo = binding.btnSendVideo
        val btnSend = binding.btnSend

        // toggle send button visibility based on text (uses extension from androidx.core.widget)
        et.addTextChangedListener { s ->
            val hasContent = !s.isNullOrBlank() && s.toString().trim().isNotEmpty()
            btnSend.visibility = if (hasContent) View.VISIBLE else View.GONE
            btnMic.visibility = if (hasContent) View.GONE else View.VISIBLE
            btnVideo.visibility = if (hasContent) View.GONE else View.VISIBLE
        }

        // send text on click
        btnSend.setOnClickListener {
            val text = et.text.toString().trim()
            if (text.isNotEmpty()) {
                sendTextMessage(text)
                et.setText("")
            }
        }

        // mic behaviour - record on toggle
        btnMic.setOnClickListener {
            // request mic permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 111)
                return@setOnClickListener
            }

            if (btnMic.tag == "recording") {
                // stop recording
                currentAudioFile = recorderHelper.stopRecording()
                btnMic.tag = "idle"
                btnMic.setImageResource(R.drawable.ic_mic)
                // upload recorded file (duration extraction can be added)
                currentAudioFile?.let { file ->
                    uploadVoiceNote(file)
                }
            } else {
                // start recording
                recorderHelper.startRecording()
                btnMic.tag = "recording"
                btnMic.setImageResource(R.drawable.ic_stop)
            }
        }

        btnVideo.setOnClickListener {
            // open picker for video
            pickMediaLauncher.launch("video/*")
        }

        // keyboard send
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = et.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendTextMessage(text)
                    et.setText("")
                }
                true
            } else false
        }
    }

    private fun loadMessages() {
        // call GET /chattingapp/getmessages?room_id=...&viewer=...
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "${baseUrl}getmessages?room_id=$roomId&viewer=$currentUserId"
                val request = Request.Builder().url(url).get().build()
                val resp = httpClient.newCall(request).execute()
                try {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (json.optString("status") == "success") {
                        val data = json.optJSONObject("data")
                        val arr = data?.optJSONArray("messages")
                        val list = mutableListOf<Message>()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)

                                val rawTranscript = o.opt("transcript_text")
                                val transcript: String? = when (rawTranscript) {
                                    null,
                                    JSONObject.NULL -> null
                                    else -> {
                                        val s = rawTranscript.toString().trim()
                                        if (s.isEmpty() || s.equals("null", ignoreCase = true)) null else s
                                    }
                                }

                                val message = Message(
                                    messageId = o.optString("message_id"),
                                    roomId = o.optString("room_id"),
                                    senderId = o.optString("sender_id"),
                                    messageType = o.optString("message_type", "text"),
                                    content = o.optString("content", null)
                                        ?.takeIf { it.isNotBlank() && !it.equals("null", true) },
                                    mediaUrl = o.optString("media_url", null)
                                        ?.takeIf { it.isNotBlank() && !it.equals("null", true) },
                                    durationSec = if (o.has("duration_sec")) o.optInt("duration_sec") else null,
                                    transcriptText = transcript,
                                    sentAt = formatTime(o.optString("sent_at"))
                                )
                                list.add(message)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            messages.clear()
                            messages.addAll(list)
                            adapter.submitList(messages.toList())
                            if (adapter.itemCount > 0) binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                        }
                    }
                } finally {
                    resp.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

//    private var realtimeChannel: RealtimeChannel? = null
//
//    private fun initRealtimeSubscribe() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            try {
//                val supabase = SupabaseClient.client
//                realtimeChannel = supabase.realtime.createChannel("messages:$roomId")
//
//                realtimeChannel?.postgresChangeFlow<kotlinx.serialization.json.JsonObject>("public") {
//                    table = "messages"
//                    filter = "room_id=eq.$roomId"
//                }?.collect { change ->
//                    when (change) {
//                        is io.github.jan.supabase.realtime.PostgresAction.Insert -> {
//                            val record = change.record
//                            val messageId = record["message_id"]?.jsonPrimitive?.content ?: ""
//                            if (messageId.isNotEmpty()) {
//                                fetchAndAppendNewMessage(messageId)
//                            }
//                        }
//                        else -> {}
//                    }
//                }
//
//                realtimeChannel?.postgresChangeFlow<kotlinx.serialization.json.JsonObject>("public") {
//                    table = "voice_notes"
//                }?.collect { change ->
//                    when (change) {
//                        is io.github.jan.supabase.realtime.PostgresAction.Update -> {
//                            val record = change.record
//                            val messageId = record["message_id"]?.jsonPrimitive?.content ?: ""
//                            val transcript = record["transcript_text"]?.jsonPrimitive?.content
//
//                            withContext(Dispatchers.Main) {
//                                val idx = messages.indexOfFirst { it.messageId == messageId }
//                                if (idx >= 0 && transcript != null) {
//                                    messages[idx] = messages[idx].copy(transcriptText = transcript)
//                                    adapter.submitList(messages.toList())
//                                    adapter.notifyItemChanged(idx)
//                                }
//                            }
//                        }
//                        else -> {}
//                    }
//                }
//
//                realtimeChannel?.subscribe()
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }

    private suspend fun fetchAndAppendNewMessage(messageId: String) {
        try {
            val url = "${baseUrl}getmessages?room_id=$roomId&viewer=$currentUserId"
            val request = Request.Builder().url(url).get().build()
            val resp = httpClient.newCall(request).execute()
            val body = resp.body?.string() ?: ""
            resp.close()

            val json = JSONObject(body)
            if (json.optString("status") == "success") {
                val arr = json.optJSONObject("data")?.optJSONArray("messages")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optString("message_id") == messageId) {
                            val rawTranscript = o.opt("transcript_text")
                            val transcript: String? = when (rawTranscript) {
                                null, JSONObject.NULL -> null
                                else -> {
                                    val s = rawTranscript.toString().trim()
                                    if (s.isEmpty() || s.equals("null", true)) null else s
                                }
                            }

                            val newMsg = Message(
                                messageId = o.optString("message_id"),
                                roomId = o.optString("room_id"),
                                senderId = o.optString("sender_id"),
                                messageType = o.optString("message_type", "text"),
                                content = o.optString("content", null)
                                    ?.takeIf { it.isNotBlank() && !it.equals("null", true) },
                                mediaUrl = o.optString("media_url", null)
                                    ?.takeIf { it.isNotBlank() && !it.equals("null", true) },
                                durationSec = if (o.has("duration_sec")) o.optInt("duration_sec") else null,
                                transcriptText = transcript,
                                sentAt = formatTime(o.optString("sent_at"))
                            )

                            withContext(Dispatchers.Main) {
                                messages.add(newMsg)
                                adapter.submitList(messages.toList())
                                binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendTextMessage(text: String) {
        val payload = JSONObject()
        payload.put("room_id", roomId)
        payload.put("sender_id", currentUserId)
        val messageObj = JSONObject()
        messageObj.put("type", "text")
        messageObj.put("content", text)
        payload.put("message", messageObj)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder().url("${baseUrl}sendmessage").post(body).build()
                val resp = httpClient.newCall(req).execute()
                try {
                    val respStr = resp.body?.string()
                    // Optionally append locally while waiting for realtime event,
                    // but we rely on realtime update to keep server as source-of-truth.
                } finally {
                    resp.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadVoiceNote(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
                val durationSec = (durationMs / 1000).toInt()

                val fileBody = file.asRequestBody("audio/m4a".toMediaType())
                val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("voice", file.name, fileBody)
                    .addFormDataPart("sender_id", currentUserId)
                    .addFormDataPart("room_id", roomId)
                    .addFormDataPart("duration_sec", durationSec.toString())
                    .build()

                val req = Request.Builder().url("${baseUrl}voicenote/upload").post(multipart).build()
                val resp = httpClient.newCall(req).execute()
                try {
                    val respStr = resp.body?.string() ?: ""
                    // parse server response if needed; rely on realtime to update UI
                } finally {
                    resp.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadMediaFromUri(uri: Uri) {
        // convert uri to file or stream and upload to appropriate endpoint (media/messages)
        // This is placeholder. You'll need to copy URI to a temp file, detect mime type and upload as multipart.
        runOnUiThread {
            Toast.makeText(this, "Uploading media...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun callTranscribe(messageId: String, mediaUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject()
                json.put("message_id", messageId)
                json.put("media_url", mediaUrl)
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder().url("${baseUrl}voicenote/transcribe").post(body).build()
                val resp = httpClient.newCall(req).execute()
                try {
                    val respStr = resp.body?.string() ?: ""
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatRoomActivity, "Transcribe result: $respStr", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    resp.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun formatTime(ts: String): String {
        // Target: dd/MM/yy HH:mm (WIB)
        val targetPattern = "dd/MM/yy HH:mm"
        val wibZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")

        return try {
            // Try parse ISO with offset (2025-11-24T15:31:12.123456+00:00)
            val cleaned = ts.trim().replace(Regex("\\.\\d+"), "") // strip fractional seconds
            val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            sdfIn.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = sdfIn.parse(cleaned) ?: Date()

            val sdfOut = SimpleDateFormat(targetPattern, Locale("id", "ID"))
            sdfOut.timeZone = wibZone
            sdfOut.format(date)
        } catch (e1: Exception) {
            try {
                // Fallback: parse Z format (2025-11-24T15:31:12Z)
                val sdfIn2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                sdfIn2.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date2 = sdfIn2.parse(ts) ?: Date()

                val sdfOut2 = SimpleDateFormat(targetPattern, Locale("id", "ID"))
                sdfOut2.timeZone = wibZone
                sdfOut2.format(date2)
            } catch (e2: Exception) {
                ts // last resort
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerHelper.release()
//        lifecycleScope.launch {
//            realtimeChannel?.unsubscribe()
//        }
    }
}
