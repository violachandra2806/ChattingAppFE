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
import com.chattingapp.utils.SupabaseClient
import androidx.appcompat.app.AlertDialog
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
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.TimeZone
import java.util.Calendar
import com.bumptech.glide.Glide
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.content.Intent
import android.provider.MediaStore

class ChatRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatRoomBinding
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private lateinit var recorderHelper: AudioRecorderHelper
    private lateinit var playerHelper: AudioPlayerHelper
    private var currentAudioFile: File? = null

    private var roomId: String = ""
    private var currentUserId: String = ""
    private var otherUserId: String = ""
    private var otherUserName: String = ""
    private var otherUserPhoto: String? = null

    private val REQUEST_CODE_VIDEO_CAPTURE = 101
    private val REQUEST_CODE_VIDEO_PICK = 102

    // replace with your base server if not set in BuildConfig
    private val baseUrl: String = if (BuildConfig.BASE_URL.endsWith("/")) BuildConfig.BASE_URL else BuildConfig.BASE_URL + "/"

    fun getPlayerHelper(): AudioPlayerHelper = playerHelper

    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomId = intent.getStringExtra("room_id") ?: ""
        currentUserId = intent.getStringExtra("user_id_first") ?: ""
        otherUserId = intent.getStringExtra("user_id_second") ?: ""
        otherUserName = intent.getStringExtra("other_user_name") ?: "Chat"
        otherUserPhoto = intent.getStringExtra("other_user_photo")

        setupToolbar()
        recorderHelper = AudioRecorderHelper(this)
        playerHelper = AudioPlayerHelper()

        setupRecycler()
        setupInput()
        loadMessages()
        initRealtimeSubscribe()

        binding.etMessage.setOnLongClickListener {
            android.util.Log.d("VideoPlayer", "Test: Long press on message input")
            // Test VideoPlayerActivity with a simple video
            val testIntent = Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                putExtra(VideoPlayerActivity.EXTRA_MESSAGE_ID, "test123")
                putExtra(VideoPlayerActivity.EXTRA_ROOM_ID, roomId)
                putExtra(VideoPlayerActivity.EXTRA_TRANSLATE_YN, "N")
                putExtra(VideoPlayerActivity.EXTRA_FRAME_RATE, 30)
                putExtra(VideoPlayerActivity.EXTRA_RESOLUTION, "1280x720")
                putExtra(VideoPlayerActivity.EXTRA_DURATION, 10000)
            }
            startActivity(testIntent)
            true
        }
    }

    private fun setupToolbar() {
        binding.tvChatName.text = otherUserName

        // Load profile picture
        Glide.with(this)
            .load(otherUserPhoto)
            .placeholder(R.drawable.ic_person_placeholder)
            .error(R.drawable.ic_person_placeholder)
            .into(binding.ivProfilePicture)

        // Back button click
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecycler() {
        adapter = MessageAdapter(currentUserId, onPlayVoice = { msg, _view ->
            msg.mediaUrl?.let { url ->
                playerHelper.play(url) {
                    android.util.Log.d("AudioPlayer", "Playback finished for: ${msg.messageId}")
                }
            }
        }, onTranscribe = { msg ->
            callTranscribe(msg.messageId, msg.mediaUrl ?: "")
        })

        binding.recyclerMessages.adapter = adapter
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        binding.recyclerMessages.layoutManager = lm

        binding.recyclerMessages.addItemDecoration(StickyDateHeaderDecoration())
    }

    private fun setupInput() {
        val et = binding.etMessage
        val btnMic = binding.btnRecordVoice
        val btnVideo = binding.btnSendVideo
        val btnSend = binding.btnSend

        et.addTextChangedListener { s ->
            val hasContent = !s.isNullOrBlank() && s.toString().trim().isNotEmpty()
            btnSend.visibility = if (hasContent) View.VISIBLE else View.GONE
            btnMic.visibility = if (hasContent) View.GONE else View.VISIBLE
            btnVideo.visibility = if (hasContent) View.GONE else View.VISIBLE
        }

        btnSend.setOnClickListener {
            val text = et.text.toString().trim()
            if (text.isNotEmpty()) {
                sendTextMessage(text)
                et.setText("")
            }
        }

        btnMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 111)
                return@setOnClickListener
            }

            if (btnMic.tag == "recording") {
                currentAudioFile = recorderHelper.stopRecording()
                btnMic.tag = "idle"
                btnMic.setImageResource(R.drawable.ic_mic)
                currentAudioFile?.let { file ->
                    uploadVoiceNote(file)
                }
            } else {
                recorderHelper.startRecording()
                btnMic.tag = "recording"
                btnMic.setImageResource(R.drawable.ic_stop)
            }
        }

        btnVideo.setOnClickListener {
            showVideoSourceDialog()
        }

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_VIDEO_CAPTURE, REQUEST_CODE_VIDEO_PICK -> {
                    val videoUri = data?.data
                    if (videoUri != null) {
                        // Open MediaPreviewActivity
                        val intent = Intent(this, MediaPreviewActivity::class.java).apply {
                            putExtra(MediaPreviewActivity.EXTRA_VIDEO_URI, videoUri)
                            putExtra(MediaPreviewActivity.EXTRA_ROOM_ID, roomId)
                            putExtra(MediaPreviewActivity.EXTRA_SENDER_ID, currentUserId)
                        }
                        startActivityForResult(intent, 103) // Use different request code
                    }
                }
                103 -> {
                    // Video was sent from MediaPreviewActivity
                    loadMessages() // Refresh messages
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_VIDEO_CAPTURE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startVideoRecording()
            } else {
                Toast.makeText(this, getString(R.string.msg_permissions_camera_mic_required), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showVideoSourceDialog() {
        val items = arrayOf(
            getString(R.string.dialog_record_video),
            getString(R.string.dialog_choose_from_gallery)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_select_video_source))
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> startVideoRecording()
                    1 -> pickVideoFromGallery()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startVideoRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_VIDEO_CAPTURE
            )
            return
        }

        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra("room_id", roomId)
            putExtra("sender_id", currentUserId)
        }
        startActivityForResult(intent, REQUEST_CODE_VIDEO_CAPTURE)
    }

    private fun pickVideoFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        intent.type = "video/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/mp4", "video/3gp", "video/avi", "video/webm"))
        startActivityForResult(intent, REQUEST_CODE_VIDEO_PICK)
    }

    private fun loadMessages() {
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

                                val sentAtRaw = o.optString("sent_at")
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
                                    sentAt = formatTimeOnly(sentAtRaw),
                                    sentAtRaw = sentAtRaw,
                                    // Add these lines for video metadata - FIXED VERSION
                                    translateYN = o.optString("translate_yn", null).let { str ->
                                        if (str.isNullOrBlank() || str.equals("null", true)) null else str
                                    },
                                    frameRate = try {
                                        if (o.has("frame_rate")) o.optInt("frame_rate") else null
                                    } catch (e: Exception) {
                                        null
                                    },
                                    resolution = o.optString("resolution", null).let { str ->
                                        if (str.isNullOrBlank() || str.equals("null", true)) null else str
                                    }
                                )

                                if (message.messageType == "video") {
                                    android.util.Log.d("ChatRoom", "Video message parsed: id=${message.messageId}, translateYN=${message.translateYN}, frameRate=${message.frameRate}, resolution=${message.resolution}, durationSec=${message.durationSec}, mediaUrl=${message.mediaUrl}")
                                }
                                list.add(message)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            messages.clear()
                            messages.addAll(list)

                            // Group messages by date and create ChatItems
                            val chatItems = groupMessagesByDate(list)
                            adapter.submitList(chatItems) {
                                if (adapter.itemCount > 0) {
                                    binding.recyclerMessages.post {
                                        binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                                    }
                                }
                            }
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
                } finally {
                    resp.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadMediaFromUri(uri: Uri) {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.msg_uploading_media), Toast.LENGTH_SHORT).show()
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

                httpClient.newCall(req).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        android.util.Log.e("Transcribe", "❌ Failed: ${e.message}")
                        lifecycleScope.launch(Dispatchers.Main) {
                            val reason = e.message ?: getString(R.string.msg_unknown_error)
                            Toast.makeText(
                                this@ChatRoomActivity,
                                getString(R.string.msg_failed_with_reason, reason),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.use {
                            android.util.Log.d("Transcribe", "✅ Success")
                            lifecycleScope.launch(Dispatchers.Main) {
                                Toast.makeText(
                                    this@ChatRoomActivity,
                                    getString(R.string.msg_transcription_done_wait_update),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                })

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ChatRoomActivity,
                        getString(R.string.msg_processing_transcription),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("Transcribe", "❌ Error: ${e.message}", e)
            }
        }
    }

    // Format time to show only HH:mm
    private fun formatTimeOnly(ts: String): String {
        if (ts.isBlank()) return ""
        return try {
            if (ts.contains("T") && (ts.contains("+") || ts.contains("Z"))) {
                val cleaned = ts.replace(Regex("\\.\\d{3}\\d+")) { match ->
                    match.value.substring(0, 4)
                }

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(cleaned) ?: Date()

                val output = SimpleDateFormat("HH:mm", Locale("id", "ID"))
                output.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                return output.format(date)
            }

            if (ts.contains(",") && ts.contains("GMT")) {
                val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("GMT")
                val date = sdf.parse(ts) ?: Date()

                val output = SimpleDateFormat("HH:mm", Locale("id", "ID"))
                output.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                return output.format(date)
            }

            ts
        } catch (e: Exception) {
            android.util.Log.e("TimeFormat", "Error: ${e.message}", e)
            ts
        }
    }

    // Format date for header (e.g., "Monday, 08 Dec 2025")
    private fun formatDateHeader(ts: String): String {
        if (ts.isBlank()) return ""
        return try {
            if (ts.contains("T") && (ts.contains("+") || ts.contains("Z"))) {
                val cleaned = ts.replace(Regex("\\.\\d{3}\\d+")) { match ->
                    match.value.substring(0, 4)
                }

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(cleaned) ?: Date()

                val output = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("id", "ID"))
                output.timeZone = TimeZone.getTimeZone("Asia/Jakarta")

                // Check if date is today, yesterday, etc.
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
                calendar.time = date

                val today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
                val yesterday = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
                yesterday.add(Calendar.DAY_OF_YEAR, -1)

                return when {
                    isSameDay(calendar, today) -> getString(R.string.label_today)
                    isSameDay(calendar, yesterday) -> getString(R.string.label_yesterday)
                    else -> output.format(date)
                }
            }

            if (ts.contains(",") && ts.contains("GMT")) {
                val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("GMT")
                val date = sdf.parse(ts) ?: Date()

                val output = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("id", "ID"))
                output.timeZone = TimeZone.getTimeZone("Asia/Jakarta")

                val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
                calendar.time = date

                val today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
                val yesterday = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
                yesterday.add(Calendar.DAY_OF_YEAR, -1)

                return when {
                    isSameDay(calendar, today) -> getString(R.string.label_today)
                    isSameDay(calendar, yesterday) -> getString(R.string.label_yesterday)
                    else -> output.format(date)
                }
            }

            ts
        } catch (e: Exception) {
            android.util.Log.e("TimeFormat", "Error: ${e.message}", e)
            ts
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // Group messages by date and insert date headers
    private fun groupMessagesByDate(messages: List<Message>): List<ChatItem> {
        val chatItems = mutableListOf<ChatItem>()
        var lastDate: String? = null

        for (message in messages) {
            val dateHeader = formatDateHeader(message.sentAtRaw)

            if (dateHeader != lastDate) {
                chatItems.add(ChatItem.DateHeader(dateHeader))
                lastDate = dateHeader
            }

            chatItems.add(ChatItem.MessageItem(message))
        }

        return chatItems
    }

    private fun initRealtimeSubscribe() {
        val supabase = SupabaseClient.getClient(this)

        lifecycleScope.launch {
            try {
                android.util.Log.d("ChatRealtime", "=== STARTING REALTIME SUBSCRIPTION ===")
                android.util.Log.d("ChatRealtime", "Room ID: $roomId")
                android.util.Log.d("ChatRealtime", "Current User: $currentUserId")

                realtimeChannel = supabase.channel("messages-room-$roomId")

                val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "messages"
                    filter("room_id", FilterOperator.EQ, roomId)
                }

                changeFlow.onEach { action ->
                    android.util.Log.d("ChatRealtime", "🔥 RECEIVED ACTION: ${action.javaClass.simpleName}")

                    when (action) {
                        is PostgresAction.Insert -> {
                            val newRecord = action.record as? Map<*, *>
                            android.util.Log.d("ChatRealtime", "📩 INSERT Record: $newRecord")
                            val msgId = newRecord?.get("message_id")?.toString()?.trim('"')
                            android.util.Log.d("ChatRealtime", "Message ID: $msgId")

                            if (msgId != null) {
                                android.util.Log.d("ChatRealtime", "🚀 Launching fetch for: $msgId")
                                lifecycleScope.launch(Dispatchers.IO) {
                                    android.util.Log.d("ChatRealtime", "🔥 Inside coroutine, calling fetch...")
                                    fetchAndAppendNewMessage(msgId)
                                }
                            }
                        }
                        is PostgresAction.Update -> {
                            val updatedRecord = action.record as? Map<*, *>
                            android.util.Log.d("ChatRealtime", "🔄 UPDATE Record: $updatedRecord")
                            val msgId = updatedRecord?.get("message_id")?.toString()?.trim('"')
                            if (msgId != null) {
                                android.util.Log.d("ChatRealtime", "🚀 Launching update for: $msgId")
                                launch(Dispatchers.IO) {
                                    updateMessageInList(msgId)
                                }
                            }
                        }
                        is PostgresAction.Delete -> {
                            val oldRecord = action.oldRecord as? Map<*, *>
                            android.util.Log.d("ChatRealtime", "🗑️ DELETE Record: $oldRecord")
                            val msgId = oldRecord?.get("message_id")?.toString()?.trim('"')
                            if (msgId != null) {
                                removeMessageFromList(msgId)
                            }
                        }
                        else -> {
                            android.util.Log.d("ChatRealtime", "❓ Unknown action: $action")
                        }
                    }
                }.launchIn(lifecycleScope)

                realtimeChannel!!.subscribe()
                android.util.Log.d("ChatRealtime", "✅ Successfully subscribed to channel")

            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("ChatRealtime", "❌ Subscription error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val message = e.message ?: getString(R.string.msg_unknown_error)
                    Toast.makeText(
                        this@ChatRoomActivity,
                        getString(R.string.msg_realtime_error, message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun fetchAndAppendNewMessage(messageId: String, isUpdate: Boolean = false) {
        android.util.Log.d("ChatRealtime", "🎯 Fetching single message: $messageId")

        withContext(Dispatchers.IO) {
            try {
                val url = "${baseUrl}getmessage/$messageId?viewer=$currentUserId"
                val request = Request.Builder().url(url).get().build()
                val resp = httpClient.newCall(request).execute()

                try {
                    val body = resp.body?.string() ?: ""
                    android.util.Log.d("ChatRealtime", "📥 Response: ${body.take(200)}")

                    val json = JSONObject(body)
                    if (json.optString("status") == "success") {
                        val msgObj = json.optJSONObject("data")?.optJSONObject("message")

                        if (msgObj != null) {
                            val rawTranscript = msgObj.opt("transcript_text")
                            val transcript: String? = when (rawTranscript) {
                                null, JSONObject.NULL -> null
                                else -> {
                                    val s = rawTranscript.toString().trim()
                                    if (s.isEmpty() || s.equals("null", ignoreCase = true)) null else s
                                }
                            }

                            val sentAtRaw = msgObj.optString("sent_at")
                            val newMsg = Message(
                                messageId = msgObj.optString("message_id"),
                                roomId = msgObj.optString("room_id"),
                                senderId = msgObj.optString("sender_id"),
                                messageType = msgObj.optString("message_type", "text"),
                                content = msgObj.optString("content", null)
                                    ?.takeIf { it.isNotBlank() && !it.equals("null", true) },
                                mediaUrl = msgObj.optString("media_url", null)
                                    ?.takeIf { it.isNotBlank() && !it.equals("null", true) },
                                durationSec = if (msgObj.has("duration_sec")) msgObj.optInt("duration_sec") else null,
                                transcriptText = transcript,
                                sentAt = formatTimeOnly(sentAtRaw),
                                sentAtRaw = sentAtRaw
                            )

                            android.util.Log.d("ChatRealtime", "✅ Message parsed: ${newMsg.messageId}")

                            withContext(Dispatchers.Main) {
                                handleMessageUpdate(newMsg, isUpdate)
                            }
                        } else {
                            android.util.Log.e("ChatRealtime", "❌ No message object in response")
                        }
                    } else {
                        val errorMsg = json.optString("message", "Unknown error")
                        android.util.Log.e("ChatRealtime", "❌ API Error: $errorMsg")
                    }
                } finally {
                    resp.close()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatRealtime", "❌ Error fetching message: ${e.message}", e)
            }
        }
    }

    private fun handleMessageUpdate(newMsg: Message, isUpdate: Boolean) {
        android.util.Log.d("ChatRealtime", "🔄 Handling message: ${newMsg.messageId}, isUpdate=$isUpdate")

        if (isUpdate) {
            val index = messages.indexOfFirst {
                it.messageId.equals(newMsg.messageId, ignoreCase = true)
            }

            if (index != -1) {
                android.util.Log.d("ChatRealtime", "📝 Updating message at index $index")

                newMsg.isTranscribing = false
                messages[index] = newMsg

                // Regroup and submit
                val chatItems = groupMessagesByDate(messages)
                adapter.submitList(chatItems)

                android.util.Log.d("ChatRealtime", "✅ Transcript updated for message at $index")
            } else {
                android.util.Log.w("ChatRealtime", "⚠️ Message not found for update: ${newMsg.messageId}")
            }
        } else {
            val existingIndex = messages.indexOfFirst {
                it.messageId.equals(newMsg.messageId, ignoreCase = true)
            }

            if (existingIndex != -1) {
                android.util.Log.d("ChatRealtime", "⚠️ Duplicate message ignored: ${newMsg.messageId}")
                return
            }

            messages.add(newMsg)
            android.util.Log.d("ChatRealtime", "➕ New message added. Total: ${messages.size}")

            // Regroup and submit
            val chatItems = groupMessagesByDate(messages)
            adapter.submitList(chatItems) {
                binding.recyclerMessages.post {
                    binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    private suspend fun updateMessageInList(messageId: String) {
        fetchAndAppendNewMessage(messageId, isUpdate = true)
    }

    private fun removeMessageFromList(messageId: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            val index = messages.indexOfFirst { it.messageId == messageId }
            if (index != -1) {
                messages.removeAt(index)

                // Regroup and submit
                val chatItems = groupMessagesByDate(messages)
                adapter.submitList(chatItems)
                android.util.Log.d("ChatRealtime", "🗑️ Message removed at index $index")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            playerHelper.release()
            android.util.Log.d("ChatRoom", "AudioPlayer released")
        } catch (e: Exception) {
            android.util.Log.e("ChatRoom", "Error releasing AudioPlayer: ${e.message}", e)
        }

        try {
            lifecycleScope.launch {
                realtimeChannel?.unsubscribe()
                android.util.Log.d("ChatRealtime", "Channel unsubscribed")
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRealtime", "Error unsubscribing: ${e.message}", e)
        }
    }
}
