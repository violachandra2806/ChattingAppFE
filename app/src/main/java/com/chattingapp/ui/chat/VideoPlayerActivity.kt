package com.chattingapp.ui.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.chattingapp.databinding.ActivityVideoPlayerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var videoUrl: String
    private var messageId: String = ""
    private var roomId: String = ""
    private var translateYN: String = "N"
    private var frameRate: Int = 30
    private var resolution: String = ""
    private var duration: Int = 0

    private var isPlaying = false
    private var userSeeking = false
    private val subtitleItems = mutableListOf<SubtitleItem>()
    private val handler = Handler(Looper.getMainLooper())
    private var currentSubtitle: String? = null
    private var updateSubtitleRunnable: Runnable? = null
    private var updateSeekbarRunnable: Runnable? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_TRANSLATE_YN = "translate_yn"
        const val EXTRA_FRAME_RATE = "frame_rate"
        const val EXTRA_RESOLUTION = "resolution"
        const val EXTRA_DURATION = "duration"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("VideoPlayer", "=== VIDEO PLAYER ACTIVITY STARTED ===")

        // Get extras
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: ""
        roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
        translateYN = intent.getStringExtra(EXTRA_TRANSLATE_YN) ?: "N"
        frameRate = intent.getIntExtra(EXTRA_FRAME_RATE, 30)
        resolution = intent.getStringExtra(EXTRA_RESOLUTION) ?: ""
        duration = intent.getIntExtra(EXTRA_DURATION, 0)

        Log.d("VideoPlayer", "Intent extras received:")
        Log.d("VideoPlayer", "  - videoUrl: $videoUrl")
        Log.d("VideoPlayer", "  - messageId: $messageId")
        Log.d("VideoPlayer", "  - roomId: $roomId")
        Log.d("VideoPlayer", "  - translateYN: $translateYN")
        Log.d("VideoPlayer", "  - frameRate: $frameRate")
        Log.d("VideoPlayer", "  - resolution: $resolution")
        Log.d("VideoPlayer", "  - duration: $duration")

        // Check if video URL is valid
        if (videoUrl.isBlank()) {
            Log.e("VideoPlayer", "ERROR: videoUrl is empty!")
            Toast.makeText(this, "Video URL is empty", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Make fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Hide system bars
        hideSystemBars()

        setupVideoPlayer()
        setupClickListeners()
        setupSeekbar()

        // Check translation status and load subtitles if available
        checkTranslationStatusAndLoadSubtitles()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupVideoPlayer() {
        binding.videoView.setVideoURI(Uri.parse(videoUrl))
        binding.progressBar.visibility = View.VISIBLE

        binding.videoView.setOnPreparedListener { mp ->
            binding.progressBar.visibility = View.GONE
            binding.btnPlayPause.visibility = View.VISIBLE

            // Get actual duration from video player
            val videoDuration = mp.duration
            if (videoDuration > 0) {
                duration = videoDuration
                Log.d("VideoPlayer", "Video duration from player: ${duration}ms")
            }

            // Set seekbar max to duration in milliseconds
            binding.seekBar.max = duration
            binding.tvDuration.text = formatTime(duration)

            // Don't auto-start
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            isPlaying = false

            // Start seekbar updates
            startSeekbarTracking()
        }

        binding.videoView.setOnClickListener { togglePlayPause() }
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }

        binding.videoView.setOnCompletionListener {
            binding.videoView.seekTo(0)
            binding.seekBar.progress = 0
            binding.tvCurrentTime.text = formatTime(0)
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            isPlaying = false
        }

        binding.videoView.setOnErrorListener { mp, what, extra ->
            Log.e("VideoPlayer", "Error playing video: what=$what, extra=$extra")
            binding.progressBar.visibility = View.GONE

            // Try to play the video directly
            try {
                binding.videoView.setVideoURI(Uri.parse(videoUrl))
                binding.videoView.start()
            } catch (e: Exception) {
                Log.e("VideoPlayer", "Failed to play video: ${e.message}")
                Toast.makeText(this, "Failed to play video", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnTranslateASL.setOnClickListener {
            translateASL()
        }
    }

    private fun setupSeekbar() {
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
                if (binding.videoView.isPlaying) {
                    binding.videoView.pause()
                    binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                    isPlaying = false
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                binding.videoView.seekTo(binding.seekBar.progress)

                // Clear subtitle when seeking
                currentSubtitle = null
                binding.subtitleTextView.text = ""
                binding.subtitleTextView.visibility = View.GONE

                // If video was playing, restart subtitle tracking
                if (isPlaying) {
                    startSubtitleTracking()
                }
            }
        })
    }

    private fun debugSubtitleTiming(currentPositionSeconds: Double) {
        if (subtitleItems.isNotEmpty()) {
            val nearestSubtitle = subtitleItems.minByOrNull {
                Math.abs(it.second - currentPositionSeconds)
            }

            if (nearestSubtitle != null) {
                val diff = currentPositionSeconds - nearestSubtitle.second
                Log.d("VideoPlayer",
                    "Time: ${String.format("%.2f", currentPositionSeconds)}s | " +
                            "Nearest subtitle: '${nearestSubtitle.text}' at ${nearestSubtitle.second}s | " +
                            "Diff: ${String.format("%.2f", diff)}s | " +
                            "Showing: ${binding.subtitleTextView.text}"
                )
            }
        }
    }

    private fun togglePlayPause() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            isPlaying = false

            // Stop subtitle tracking when paused
            updateSubtitleRunnable?.let { handler.removeCallbacks(it) }
            updateSubtitleRunnable = null
        } else {
            // If at end, reset to beginning
            if (binding.videoView.currentPosition >= binding.videoView.duration - 100) {
                binding.videoView.seekTo(0)
                binding.seekBar.progress = 0

                // Clear subtitle when resetting to beginning
                currentSubtitle = null
                binding.subtitleTextView.text = ""
                binding.subtitleTextView.visibility = View.GONE
            }

            binding.videoView.start()
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            isPlaying = true

            // Start subtitle tracking
            startSubtitleTracking()
        }
    }

    private fun startSeekbarTracking() {
        updateSeekbarRunnable?.let { handler.removeCallbacks(it) }

        updateSeekbarRunnable = object : Runnable {
            override fun run() {
                val current = binding.videoView.currentPosition
                binding.seekBar.progress = current
                binding.tvCurrentTime.text = formatTime(current)

                // Update duration display from actual video
                val videoDuration = binding.videoView.duration
                if (videoDuration > 0 && duration != videoDuration) {
                    duration = videoDuration
                    binding.seekBar.max = duration
                    binding.tvDuration.text = formatTime(duration)
                }

                // Keep updating regardless of playing state
                handler.postDelayed(this, 100)
            }
        }

        handler.post(updateSeekbarRunnable!!)
    }

    private fun startSubtitleTracking() {
        updateSubtitleRunnable?.let { handler.removeCallbacks(it) }

        updateSubtitleRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    // Get current position in SECONDS (not milliseconds)
                    val currentPositionSeconds = binding.videoView.currentPosition / 1000.0

                    if (BuildConfig.DEBUG) {
                        debugSubtitleTiming(currentPositionSeconds)
                    }

                    // Find the subtitle that should be displayed
                    val subtitle = subtitleItems.find {
                        // Show subtitle starting 0.1 seconds before its time, until next subtitle starts
                        // or for a maximum of 3 seconds if it's the last subtitle
                        val shouldShow = currentPositionSeconds >= (it.second - 0.1)

                        // Check if we should still show this subtitle
                        if (shouldShow) {
                            // Find next subtitle time
                            val nextSubtitleTime = subtitleItems
                                .firstOrNull { nextItem -> nextItem.second > it.second }
                                ?.second

                            // If there's a next subtitle, stop at that time
                            // Otherwise, show for up to 3 seconds
                            val shouldStop = if (nextSubtitleTime != null) {
                                currentPositionSeconds < nextSubtitleTime
                            } else {
                                currentPositionSeconds < (it.second + 3.0)
                            }

                            shouldStop
                        } else {
                            false
                        }
                    }?.text

                    if (subtitle != currentSubtitle) {
                        currentSubtitle = subtitle
                        if (subtitle != null) {
                            binding.subtitleTextView.text = subtitle
                            binding.subtitleTextView.visibility = View.VISIBLE
                        } else {
                            binding.subtitleTextView.visibility = View.GONE
                        }
                    }
                }
                handler.postDelayed(this, 100) // Check every 100ms
            }
        }

        handler.post(updateSubtitleRunnable!!)
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun checkTranslationStatusAndLoadSubtitles() {
        // First, check the current translation status from getvideonotes
        fetchVideoNotesForTranslationStatus()
    }

    private fun fetchVideoNotesForTranslationStatus() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "${BuildConfig.BASE_URL}getvideonotes?room_id=$roomId&message_id=$messageId"
                Log.d("VideoPlayer", "Fetching video notes for translation status: $url")
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()

                response.use {
                    val body = response.body?.string()
                    Log.d("VideoPlayer", "Video notes response code: ${response.code}")
                    Log.d("VideoPlayer", "Video notes response body: ${body?.take(500)}")

                    if (response.isSuccessful && body != null) {
                        val json = org.json.JSONObject(body)
                        val status = json.optString("status", "error")

                        if (status == "success") {
                            val dataArray = json.optJSONArray("data")
                            var updatedTranslateYN = translateYN
                            var updatedResolution = resolution

                            // Find the specific message in the data array
                            if (dataArray != null && dataArray.length() > 0) {
                                for (i in 0 until dataArray.length()) {
                                    val item = dataArray.optJSONObject(i)
                                    val itemMessageId = item.optString("message_id", "")

                                    if (itemMessageId == messageId) {
                                        // Found our message, extract the fields
                                        updatedTranslateYN = item.optString("translate_yn", translateYN)
                                        updatedResolution = item.optString("resolution", resolution)

                                        Log.d("VideoPlayer", "Found message: $itemMessageId")
                                        Log.d("VideoPlayer", "  - translate_yn: $updatedTranslateYN")
                                        Log.d("VideoPlayer", "  - resolution: $updatedResolution")
                                        break
                                    }
                                }
                            }

                            withContext(Dispatchers.Main) {
                                // Update both translateYN and resolution with server values
                                translateYN = updatedTranslateYN
                                resolution = updatedResolution

                                Log.d("VideoPlayer", "Updated from server:")
                                Log.d("VideoPlayer", "  - translateYN: $translateYN")
                                Log.d("VideoPlayer", "  - resolution: $resolution")

                                // Show/hide translate button based on current status
                                binding.btnTranslateASL.visibility = if (translateYN == "Y") View.GONE else View.VISIBLE

                                // If already translated, fetch subtitles
                                if (translateYN == "Y") {
                                    Log.d("VideoPlayer", "Video is translated, fetching subtitles")
                                    fetchTranslatedVideoData()
                                } else {
                                    Log.d("VideoPlayer", "Video not translated yet")
                                }
                            }
                        } else {
                            Log.e("VideoPlayer", "Failed to get video notes status")
                        }
                    } else {
                        Log.e("VideoPlayer", "Failed to fetch video notes (HTTP ${response.code})")
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoPlayer", "Error fetching video notes: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // Show translate button as fallback if we can't determine status
                    binding.btnTranslateASL.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun fetchTranslatedVideoData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "${BuildConfig.BASE_URL}gettranslatevideo?room_id=$roomId&message_id=$messageId"
                Log.d("VideoPlayer", "Fetching subtitles from: $url")
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()

                response.use {
                    val body = response.body?.string()
                    Log.d("VideoPlayer", "Subtitles response code: ${response.code}")
                    Log.d("VideoPlayer", "Subtitles response body: ${body?.take(500)}")

                    if (response.isSuccessful && body != null) {
                        val json = org.json.JSONObject(body)
                        val status = json.optString("status", "error")

                        if (status == "success") {
                            val data = json.optJSONObject("data")
                            val timestampsArray = data?.optJSONArray("timestamps")
                            val durationString = data?.optString("duration", "0")

                            val videoDuration = ((durationString?.toDoubleOrNull() ?: 0.0) * 1000).toInt()

                            Log.d("VideoPlayer", "Duration string: '$durationString', converted to: ${videoDuration}ms")

                            // Update duration if available
                            if (videoDuration > 0) {
                                withContext(Dispatchers.Main) {
                                    duration = videoDuration
                                    binding.seekBar.max = duration
                                    binding.tvDuration.text = formatTime(duration)
                                    Log.d("VideoPlayer", "Updated duration to: ${duration}ms")
                                }
                            }

                            // Parse subtitles from timestamps array
                            if (timestampsArray != null && timestampsArray.length() > 0) {
                                subtitleItems.clear()
                                Log.d("VideoPlayer", "Found ${timestampsArray.length()} subtitle items")

                                for (i in 0 until timestampsArray.length()) {
                                    val item = timestampsArray.getJSONObject(i)
                                    val second = item.optDouble("second", 0.0)
                                    val text = item.optString("text", "")

                                    // Validate and fix timing if needed
                                    val correctedSecond = if (second < 0) 0.0 else second

                                    subtitleItems.add(SubtitleItem(correctedSecond, text))

                                    Log.d("VideoPlayer", "Subtitle $i: time=${correctedSecond}s, text='$text'")
                                }

                                // Sort by time to ensure proper order
                                subtitleItems.sortBy { it.second }

                                withContext(Dispatchers.Main) {
                                    Log.d("VideoPlayer", "Subtitle items loaded: ${subtitleItems.size}")

                                    // Clear any existing subtitle
                                    currentSubtitle = null
                                    binding.subtitleTextView.text = ""
                                    binding.subtitleTextView.visibility = View.GONE

                                    if (subtitleItems.isNotEmpty()) {
                                        Toast.makeText(
                                            this@VideoPlayerActivity,
                                            "Subtitles loaded: ${subtitleItems.size} items",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Log.d("VideoPlayer", "No timestamps array found in response")
                                    Toast.makeText(
                                        this@VideoPlayerActivity,
                                        "No subtitles available",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } else {
                            val error = json.optString("error", "Failed to fetch subtitles")
                            Log.e("VideoPlayer", "Error fetching subtitles: $error")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@VideoPlayerActivity,
                                    "Error: $error",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        Log.e("VideoPlayer", "Failed to fetch subtitles (HTTP ${response.code})")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@VideoPlayerActivity,
                                "Failed to fetch subtitles (HTTP ${response.code})",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoPlayer", "Error fetching translated data: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@VideoPlayerActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun translateASL() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnTranslateASL.isEnabled = false // Disable button while processing

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Log what we're sending
                Log.d("VideoPlayer", "Sending translate request with:")
                Log.d("VideoPlayer", "  - video_url: $videoUrl")
                Log.d("VideoPlayer", "  - room_id: $roomId")
                Log.d("VideoPlayer", "  - frame_rate: $frameRate")
                Log.d("VideoPlayer", "  - resolution: $resolution")
                Log.d("VideoPlayer", "  - message_id: $messageId")

                val json = org.json.JSONObject().apply {
                    put("video_url", videoUrl)
                    put("room_id", roomId)
                    put("frame_rate", frameRate)
                    put("resolution", resolution)
                    put("message_id", messageId)
                    put("generate_srt", false)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("${BuildConfig.BASE_URL}translateasl")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                Log.d("VideoPlayer", "Request URL: ${request.url}")
                Log.d("VideoPlayer", "Request body: $json")

                val response = httpClient.newCall(request).execute()
                val responseCode = response.code
                val responseBody = response.body?.string()

                Log.d("VideoPlayer", "Response code: $responseCode")
                Log.d("VideoPlayer", "Response body: $responseBody")

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnTranslateASL.isEnabled = true // Re-enable button

                    if (response.isSuccessful) {
                        if (responseBody != null) {
                            try {
                                val jsonResponse = org.json.JSONObject(responseBody)
                                val status = jsonResponse.optString("status", "error")

                                if (status == "success") {
                                    Log.d("VideoPlayer", "✅ Translation started successfully")
                                    Toast.makeText(
                                        this@VideoPlayerActivity,
                                        "Translation started. Please wait...",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    // DON'T change translateYN here - wait for server to update
                                    // DON'T hide button - let the periodic check handle it

                                    // Start periodic checking for translation completion
                                    startCheckingTranslationStatus()
                                } else {
                                    val error = jsonResponse.optString("error", "Translation failed")
                                    Log.e("VideoPlayer", "❌ Translation failed: $error")
                                    Toast.makeText(
                                        this@VideoPlayerActivity,
                                        "Translation failed: $error",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    // Button stays visible and enabled
                                }
                            } catch (e: Exception) {
                                Log.e("VideoPlayer", "❌ JSON parsing error: ${e.message}", e)
                                Toast.makeText(
                                    this@VideoPlayerActivity,
                                    "Failed to parse response",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        Log.e("VideoPlayer", "❌ HTTP Error: $responseCode")

                        // Try to parse error response
                        val errorMessage = if (responseBody != null) {
                            try {
                                val errorJson = org.json.JSONObject(responseBody)
                                errorJson.optString("error", "Unknown error")
                            } catch (e: Exception) {
                                "HTTP $responseCode"
                            }
                        } else {
                            "HTTP $responseCode"
                        }

                        Toast.makeText(
                            this@VideoPlayerActivity,
                            "Server error: $errorMessage",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    response.close()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnTranslateASL.isEnabled = true
                    Log.e("VideoPlayer", "❌ Network error: ${e.message}", e)
                    Toast.makeText(
                        this@VideoPlayerActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startCheckingTranslationStatus() {
        // Check every 3 seconds for translation completion
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchVideoNotesForTranslationStatus()

                // Keep checking if translation is not yet complete and button is still visible
                if (binding.btnTranslateASL.visibility == View.VISIBLE) {
                    handler.postDelayed(this, 3000) // Check every 3 seconds
                }
            }
        }, 3000)
    }

    override fun onPause() {
        super.onPause()
        updateSubtitleRunnable?.let { handler.removeCallbacks(it) }
        updateSeekbarRunnable?.let { handler.removeCallbacks(it) }
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        updateSubtitleRunnable?.let { handler.removeCallbacks(it) }
        updateSeekbarRunnable?.let { handler.removeCallbacks(it) }
    }
}
