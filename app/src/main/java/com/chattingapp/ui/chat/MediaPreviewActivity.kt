package com.chattingapp.ui.chat

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chattingapp.databinding.ActivityMediaBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.widget.MediaController
import com.chattingapp.BuildConfig
import java.io.File
import okhttp3.RequestBody.Companion.asRequestBody

data class SubtitleItem(val second: Double, val text: String)

class MediaPreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaBinding
    private lateinit var videoUri: Uri
    private lateinit var videoFile: File
    private var resolution: String = ""
    private var frameRate: Int = 30
    private var mediaUrl: String = ""
    private var fileSize: Long = 0
    private var roomId: String = ""
    private var senderId: String = ""

    private val subtitleItems = mutableListOf<SubtitleItem>()
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var currentSubtitle: String? = null
    private var updateSubtitleRunnable: Runnable? = null

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_SENDER_ID = "sender_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoUri = intent.getParcelableExtra(EXTRA_VIDEO_URI)!!
        roomId = intent.getStringExtra(EXTRA_ROOM_ID)!!
        senderId = intent.getStringExtra(EXTRA_SENDER_ID)!!

        // Fix the File creation
        val path = videoUri.path
        videoFile = if (path != null && path.isNotEmpty()) {
            File(path)
        } else {
            // Create a temporary file if path is null
            File(cacheDir, "temp_video.mp4")
        }

        setupVideoPlayer()
        extractVideoMetadata()
        setupClickListeners()
    }

    private fun setupVideoPlayer() {
        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)
        binding.videoView.setVideoURI(videoUri)

        binding.videoView.setOnPreparedListener {
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnPlayPause.visibility = android.view.View.VISIBLE
        }

        binding.videoView.setOnClickListener {
            if (isPlaying) {
                binding.videoView.pause()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                binding.videoView.start()
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
            isPlaying = !isPlaying
        }

        // Start subtitle tracking when video starts
        binding.videoView.setOnInfoListener { _, what, _ ->
            if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                startSubtitleTracking()
                return@setOnInfoListener true
            }
            false
        }
    }

    private fun extractVideoMetadata() {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, videoUri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()

            resolution = "${width}x${height}"
            frameRate = frameRateStr?.toInt() ?: 30

            // Fix file size extraction
            fileSize = if (videoFile.exists()) {
                videoFile.length()
            } else {
                // Get file size from content resolver if file doesn't exist
                val inputStream = contentResolver.openInputStream(videoUri)
                val bytes = inputStream?.readBytes()?.size?.toLong() ?: 0
                inputStream?.close()
                bytes
            }

            binding.tvVideoInfo.text = "Resolution: $resolution • ${frameRate}fps • ${String.format("%.1f", fileSize / (1024.0 * 1024.0))}MB"

            retriever.release()

            // Show send button
            binding.btnSend.visibility = android.view.View.VISIBLE
            binding.btnTranslateASL.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupClickListeners() {
        binding.btnTranslateASL.setOnClickListener {
            uploadVideoForTranslation()
        }

        binding.btnSend.setOnClickListener {
            sendVideoNote()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun uploadVideoForTranslation() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // First upload the video
                val uploadResponse = uploadVideoNote()

                if (uploadResponse != null) {
                    mediaUrl = uploadResponse.optString("media_url", "")

                    // Then call translation API
                    translateASL()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    android.widget.Toast.makeText(this@MediaPreviewActivity, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun uploadVideoNote(): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()

                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("video", videoFile.name,
                        videoFile.asRequestBody("video/mp4".toMediaType()))
                    .addFormDataPart("resolution", resolution)
                    .addFormDataPart("frame_rate", frameRate.toString())
                    .build()

                val request = okhttp3.Request.Builder()
                    .url("${BuildConfig.BASE_URL}uploadvideonote")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                response.close()

                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    if (json.optString("status") == "success") {
                        return@withContext json
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun translateASL() {
        withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()

                val json = JSONObject().apply {
                    put("video_url", mediaUrl)
                    put("room_id", roomId)
                    put("frame_rate", frameRate)
                    put("resolution", resolution)
                    put("generate_srt", false)
                }

                val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = okhttp3.Request.Builder()
                    .url("${BuildConfig.BASE_URL}translateasl")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                response.close()

                if (responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    if (jsonResponse.optString("status") == "success") {
                        val dataArray = jsonResponse.optJSONArray("data")
                        subtitleItems.clear()

                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.getJSONObject(i)
                                val second = item.optDouble("second", 0.0)
                                val text = item.optString("text", "")
                                subtitleItems.add(SubtitleItem(second, text))
                            }
                        }

                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = android.view.View.GONE
                            binding.subtitleTextView.visibility = android.view.View.VISIBLE
                            startSubtitleTracking()
                            android.widget.Toast.makeText(
                                this@MediaPreviewActivity,
                                "Translation completed",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun startSubtitleTracking() {
        updateSubtitleRunnable?.let { handler.removeCallbacks(it) }

        updateSubtitleRunnable = object : Runnable {
            override fun run() {
                if (binding.videoView.isPlaying) {
                    val currentPosition = binding.videoView.currentPosition / 1000.0 // Convert to seconds

                    // Find subtitle for current position
                    val subtitle = subtitleItems.find {
                        currentPosition >= it.second - 0.5 && currentPosition <= it.second + 2.0
                    }?.text

                    if (subtitle != currentSubtitle) {
                        currentSubtitle = subtitle
                        if (subtitle != null) {
                            binding.subtitleTextView.text = subtitle
                            binding.subtitleTextView.visibility = android.view.View.VISIBLE
                        } else {
                            binding.subtitleTextView.visibility = android.view.View.GONE
                        }
                    }
                }
                handler.postDelayed(this, 100) // Update every 100ms
            }
        }

        handler.post(updateSubtitleRunnable!!)
    }

    private fun sendVideoNote() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // If mediaUrl is not set yet (user didn't translate), upload first
                if (mediaUrl.isEmpty()) {
                    val uploadResponse = uploadVideoNote()
                    if (uploadResponse != null) {
                        mediaUrl = uploadResponse.optString("media_url", "")
                    }
                }

                if (mediaUrl.isNotEmpty()) {
                    val client = okhttp3.OkHttpClient()

                    val json = JSONObject().apply {
                        put("sender_id", senderId)
                        put("room_id", roomId)
                        put("media_url", mediaUrl)
                        put("file_size", fileSize)
                        put("resolution", resolution)
                        put("frame_rate", frameRate)
                    }

                    val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                    val request = okhttp3.Request.Builder()
                        .url("${BuildConfig.BASE_URL}sendvideonote")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    response.close()

                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = android.view.View.GONE

                        if (responseBody != null) {
                            val jsonResponse = JSONObject(responseBody)
                            if (jsonResponse.optString("status") == "success") {
                                android.widget.Toast.makeText(
                                    this@MediaPreviewActivity,
                                    "Video sent successfully",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()

                                // Return result to ChatRoomActivity
                                val resultIntent = Intent()
                                resultIntent.putExtra("video_sent", true)
                                setResult(RESULT_OK, resultIntent)
                                finish()
                            } else {
                                android.widget.Toast.makeText(
                                    this@MediaPreviewActivity,
                                    "Failed to send video",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    android.widget.Toast.makeText(
                        this@MediaPreviewActivity,
                        "Error: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        updateSubtitleRunnable?.let { handler.removeCallbacks(it) }
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateSubtitleRunnable?.let { handler.removeCallbacks(it) }
    }
}