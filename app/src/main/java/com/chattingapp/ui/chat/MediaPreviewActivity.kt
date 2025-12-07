package com.chattingapp.ui.chat

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chattingapp.databinding.ActivityMediaBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import okhttp3.RequestBody.Companion.asRequestBody
import com.chattingapp.BuildConfig
import androidx.appcompat.app.AlertDialog

data class SubtitleItem(val second: Double, val text: String)

class MediaPreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaBinding
    private lateinit var videoUri: Uri
    private lateinit var videoFile: File

    private var isPlaying = false
    private var userSeeking = false
    private var resolution: String = ""
    private var frameRate: Int = 30
    private var mediaUrl: String = ""
    private var fileSize: Long = 0
    private var roomId: String = ""
    private var senderId: String = ""
    private var videoFileName: String = "" // ADD THIS

    private val subtitleItems = mutableListOf<SubtitleItem>()
    private val handler = Handler(Looper.getMainLooper())
    private var currentSubtitle: String? = null
    private var updateSubtitleRunnable: Runnable? = null

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_FILE_PATH = "video_file_path" // ADD THIS
        const val EXTRA_VIDEO_FILE_NAME = "video_file_name" // ADD THIS
        const val EXTRA_VIDEO_RESOLUTION = "video_resolution" // ADD THIS
        const val EXTRA_VIDEO_FPS = "video_fps" // ADD THIS
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_SENDER_ID = "sender_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoUri = intent.getParcelableExtra(EXTRA_VIDEO_URI)!!
        val videoFilePath = intent.getStringExtra(EXTRA_VIDEO_FILE_PATH)
        videoFileName = intent.getStringExtra(EXTRA_VIDEO_FILE_NAME) ?: "video.mp4"
        resolution = intent.getStringExtra(EXTRA_VIDEO_RESOLUTION) ?: ""
        frameRate = intent.getIntExtra(EXTRA_VIDEO_FPS, 30)
        roomId = intent.getStringExtra(EXTRA_ROOM_ID)!!
        senderId = intent.getStringExtra(EXTRA_SENDER_ID)!!

        videoFile = if (videoFilePath != null && File(videoFilePath).exists()) {
            File(videoFilePath)
        } else {
            createTempFileFromUri(videoUri)
        }

        setupVideoPlayer()
        setupSeekbar()
        extractVideoMetadata()
        setupClickListeners()
    }

    private fun createTempFileFromUri(uri: Uri): File {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("video_", ".mp4", cacheDir)

            inputStream?.use { input ->
                java.io.FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            File.createTempFile("video_", ".mp4", cacheDir)
        }
    }

    private fun setupVideoPlayer() {
        binding.videoView.setZOrderOnTop(false)
        binding.videoView.requestFocus()
        binding.videoView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.videoView.setVideoURI(videoUri)

        binding.videoView.setOnPreparedListener { mp ->
            mp.setScreenOnWhilePlaying(true)
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnPlayPause.visibility = android.view.View.VISIBLE

            mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)

            // IMPORTANT: Get duration and set seekbar max
            val duration = mp.duration
            binding.seekBar.max = duration  // Set actual duration as max
            binding.tvDuration.text = formatTime(duration)

            // IMPORTANT: Don't auto-start, let user control
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            isPlaying = false

            // Start seekbar tracking
            startSeekbarTracking()
        }

        binding.videoView.setOnClickListener { togglePlayPause() }
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }

        binding.videoView.setOnErrorListener { mp, what, extra ->
            Log.e("VIDEO_PLAYER", "Error playing video: what=$what, extra=$extra")
            false
        }

        // ADD THIS: Handle completion to reset to start
        binding.videoView.setOnCompletionListener {
            binding.videoView.seekTo(0)
            binding.seekBar.progress = 0
            binding.tvCurrentTime.text = formatTime(0)
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            isPlaying = false
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun togglePlayPause() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            isPlaying = false
        } else {
            // If video is at the end, reset to beginning
            if (binding.videoView.currentPosition >= binding.videoView.duration - 100) {
                binding.videoView.seekTo(0)
                binding.seekBar.progress = 0
            }

            binding.videoView.start()
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            isPlaying = true
            startSubtitleTracking()
        }
    }

    private fun startSeekbarTracking() {
        // Remove any existing callbacks first
        handler.removeCallbacksAndMessages(null)

        handler.post(object : Runnable {
            override fun run() {
                // Always update current position and time display
                val current = binding.videoView.currentPosition
                binding.seekBar.progress = current
                binding.tvCurrentTime.text = formatTime(current)

                // Always update duration text
                binding.tvDuration.text = formatTime(binding.videoView.duration)

                // IMPORTANT: ALWAYS post delayed to keep updating, regardless of playing state
                handler.postDelayed(this, 100)
            }
        })
    }

    private fun setupSeekbar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Update the time display when user is dragging
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                userSeeking = true
                // Pause video while seeking
                if (binding.videoView.isPlaying) {
                    binding.videoView.pause()
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                    isPlaying = false
                }
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                userSeeking = false
                // Seek to the selected position
                binding.videoView.seekTo(binding.seekBar.progress)
                // Don't auto-play after seeking - let user decide
                binding.tvCurrentTime.text = formatTime(binding.seekBar.progress)
            }
        })
    }

    private fun resetToStart() {
        binding.videoView.seekTo(0)
        binding.seekBar.progress = 0
        binding.tvCurrentTime.text = formatTime(0)
        binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        isPlaying = false
    }

    private fun extractVideoMetadata() {
        // If we already have resolution from CameraActivity, use it
        if (resolution.isEmpty()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, videoUri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val fr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                resolution = "${w}x${h}"
                frameRate = fr?.toInt() ?: 30
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Get file size from the video file
        fileSize = if (videoFile.exists() && videoFile.length() > 0) {
            videoFile.length()
        } else {
            // Try to get from URI
            try {
                contentResolver.openFileDescriptor(videoUri, "r")?.use { fd ->
                    fd.statSize
                } ?: 0
            } catch (e: Exception) {
                0
            }
        }

        binding.tvVideoInfo.text = "Resolution: $resolution • ${frameRate}fps • ${String.format("%.1f", fileSize / (1024.0 * 1024.0))}MB"
        binding.btnSend.visibility = android.view.View.VISIBLE
        binding.btnTranslateASL.visibility = android.view.View.VISIBLE
    }

    private fun setupClickListeners() {
        binding.btnTranslateASL.setOnClickListener { uploadVideoForTranslation() }
        binding.btnSend.setOnClickListener { sendVideoNote() }
    }

    private fun uploadVideoForTranslation() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uploadResponse = uploadVideoNote()

                withContext(Dispatchers.Main) {
                    if (uploadResponse != null) {
                        mediaUrl = uploadResponse.optString("media_url", "")
                        Log.d("TRANSLATE_UPLOAD", "Upload successful. Media URL: $mediaUrl")

                        // Start translation after successful upload
                        translateASL()
                    } else {
                        binding.progressBar.visibility = android.view.View.GONE
                        showErrorDialog(
                            "Upload Failed",
                            "Failed to upload video for translation",
                            retryAction = { uploadVideoForTranslation() }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TRANSLATE_UPLOAD", "Upload error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    showErrorDialog(
                        "Upload Error",
                        "Error: ${e.message ?: "Unknown error"}",
                        retryAction = { uploadVideoForTranslation() }
                    )
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
                    .addFormDataPart("video", videoFileName,
                        videoFile.asRequestBody("video/mp4".toMediaType()))
                    .addFormDataPart("resolution", resolution)
                    .addFormDataPart("frame_rate", frameRate.toString())
                    .build()

                val request = okhttp3.Request.Builder()
                    .url("${BuildConfig.BASE_URL}uploadvideonote")
                    .post(requestBody)
                    .build()

                Log.d("UPLOAD_VIDEO_NOTE", "Uploading video: ${videoFile.name}, size: ${videoFile.length()} bytes")
                Log.d("UPLOAD_VIDEO_NOTE", "URL: ${request.url}")
                Log.d("UPLOAD_VIDEO_NOTE", "Resolution: $resolution, FPS: $frameRate")

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d("UPLOAD_VIDEO_NOTE", "Response code: ${response.code}")
                Log.d("UPLOAD_VIDEO_NOTE", "Response body: $responseBody")

                response.close()

                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    val status = json.optString("status", "error")
                    if (status == "success") {
                        Log.i("UPLOAD_VIDEO_NOTE", "✅ Upload successful")
                        return@withContext json
                    } else {
                        val error = json.optString("error", "Unknown error")
                        Log.e("UPLOAD_VIDEO_NOTE", "❌ Upload failed: $error")
                    }
                } else {
                    Log.e("UPLOAD_VIDEO_NOTE", "❌ Empty response body")
                }
                null
            } catch (e: Exception) {
                Log.e("UPLOAD_VIDEO_NOTE", "❌ Exception: ${e.message}", e)
                null
            }
        }
    }

    private fun translateASL() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
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

                Log.d("TRANSLATE_ASL", "Request URL: ${request.url}")
                Log.d("TRANSLATE_ASL", "Request body: $json")

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d("TRANSLATE_ASL", "Response code: ${response.code}")
                Log.d("TRANSLATE_ASL", "Response body: $responseBody")

                response.close()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE

                    if (responseBody != null) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            val status = jsonResponse.optString("status", "error")

                            if (status == "success") {
                                Log.i("TRANSLATE_ASL", "✅ Translation successful")

                                val dataArray = jsonResponse.optJSONArray("data")
                                subtitleItems.clear()

                                if (dataArray != null) {
                                    for (i in 0 until dataArray.length()) {
                                        val item = dataArray.getJSONObject(i)
                                        val second = item.optDouble("second", 0.0)
                                        val text = item.optString("text", "")
                                        subtitleItems.add(SubtitleItem(second, text))
                                    }
                                    Log.d("TRANSLATE_ASL", "Got ${subtitleItems.size} subtitle items")
                                }

                                binding.subtitleTextView.visibility = android.view.View.VISIBLE
                                startSubtitleTracking()
                                android.widget.Toast.makeText(
                                    this@MediaPreviewActivity,
                                    "Translation completed",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val error = jsonResponse.optString("error", "Translation failed")
                                Log.e("TRANSLATE_ASL", "❌ Translation failed: $error")
                                showErrorDialog(
                                    "Translation Failed",
                                    error,
                                    retryAction = { translateASL() }
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("TRANSLATE_ASL", "❌ JSON parsing error: ${e.message}", e)
                            showErrorDialog(
                                "Translation Error",
                                "Failed to parse response: ${e.message}",
                                retryAction = { translateASL() }
                            )
                        }
                    } else {
                        Log.e("TRANSLATE_ASL", "❌ Empty response body")
                        showErrorDialog(
                            "Translation Error",
                            "Empty response from server",
                            retryAction = { translateASL() }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TRANSLATE_ASL", "❌ Network error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    showErrorDialog(
                        "Network Error",
                        "Failed to connect: ${e.message}",
                        retryAction = { translateASL() }
                    )
                }
            }
        }
    }

    private fun startSubtitleTracking() {
        updateSubtitleRunnable?.let { handler.removeCallbacks(it) }

        updateSubtitleRunnable = object : Runnable {
            override fun run() {
                if (binding.videoView.isPlaying) {
                    val currentPosition = binding.videoView.currentPosition / 1000.0
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
                handler.postDelayed(this, 100)
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
                    Log.d("SEND_VIDEO_NOTE", "Media URL empty, uploading first...")
                    val uploadResponse = uploadVideoNote()
                    if (uploadResponse != null) {
                        mediaUrl = uploadResponse.optString("media_url", "")
                        Log.d("SEND_VIDEO_NOTE", "Uploaded successfully, media URL: $mediaUrl")
                    } else {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = android.view.View.GONE
                            showErrorDialog(
                                "Upload Failed",
                                "Failed to upload video",
                                retryAction = { sendVideoNote() }
                            )
                        }
                        return@launch
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

                    Log.d("SEND_VIDEO_NOTE", "Sending video note...")
                    Log.d("SEND_VIDEO_NOTE", "URL: ${request.url}")
                    Log.d("SEND_VIDEO_NOTE", "Request: $json")

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    Log.d("SEND_VIDEO_NOTE", "Response code: ${response.code}")
                    Log.d("SEND_VIDEO_NOTE", "Response body: $responseBody")

                    response.close()

                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = android.view.View.GONE

                        if (responseBody != null) {
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                val status = jsonResponse.optString("status", "error")

                                if (status == "success") {
                                    Log.i("SEND_VIDEO_NOTE", "✅ Video sent successfully")
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
                                    val error = jsonResponse.optString("error", "Failed to send video")
                                    Log.e("SEND_VIDEO_NOTE", "❌ Send failed: $error")
                                    showErrorDialog(
                                        "Send Failed",
                                        error,
                                        retryAction = { sendVideoNote() }
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("SEND_VIDEO_NOTE", "❌ JSON parsing error: ${e.message}", e)
                                showErrorDialog(
                                    "Send Error",
                                    "Failed to parse response: ${e.message}",
                                    retryAction = { sendVideoNote() }
                                )
                            }
                        } else {
                            Log.e("SEND_VIDEO_NOTE", "❌ Empty response body")
                            showErrorDialog(
                                "Send Error",
                                "Empty response from server",
                                retryAction = { sendVideoNote() }
                            )
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = android.view.View.GONE
                        showErrorDialog(
                            "Send Failed",
                            "No video URL available",
                            retryAction = { sendVideoNote() }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SEND_VIDEO_NOTE", "❌ Network error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    showErrorDialog(
                        "Network Error",
                        "Failed to connect: ${e.message}",
                        retryAction = { sendVideoNote() }
                    )
                }
            }
        }
    }

    private fun showErrorDialog(title: String, message: String, retryAction: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ ->
                retryAction?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        handler.removeCallbacksAndMessages(null)
        updateSubtitleRunnable?.let { handler.removeCallbacks(it) }
    }
}