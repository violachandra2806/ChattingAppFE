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

        Log.d("VideoPlayer", "Intent extras: ${intent.extras}")
        Log.d("VideoPlayer", "videoUrl: ${intent.getStringExtra(EXTRA_VIDEO_URL)}")
        Log.d("VideoPlayer", "messageId: ${intent.getStringExtra(EXTRA_MESSAGE_ID)}")
        Log.d("VideoPlayer", "roomId: ${intent.getStringExtra(EXTRA_ROOM_ID)}")
        Log.d("VideoPlayer", "translateYN: ${intent.getStringExtra(EXTRA_TRANSLATE_YN)}")

        // Make fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Hide system bars
        hideSystemBars()

        // Get extras
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: ""
        roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
        translateYN = intent.getStringExtra(EXTRA_TRANSLATE_YN) ?: "N"
        frameRate = intent.getIntExtra(EXTRA_FRAME_RATE, 30)
        resolution = intent.getStringExtra(EXTRA_RESOLUTION) ?: ""
        duration = intent.getIntExtra(EXTRA_DURATION, 0)

        Log.d("VideoPlayer", "Starting with: url=$videoUrl, translateYN=$translateYN, duration=$duration")

        setupVideoPlayer()
        setupClickListeners()
        setupSeekbar()

        // If already translated, fetch subtitles
        if (translateYN == "Y") {
            fetchTranslatedVideoData()
        } else {
            // Show translate button if not translated
            binding.btnTranslateASL.visibility = View.VISIBLE
        }
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
            } else if (duration == 0) {
                // If still 0, set a default
                duration = 10000 // 10 seconds default
            }

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
            }
        })
    }

    private fun togglePlayPause() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            isPlaying = false
        } else {
            // If at end, reset to beginning
            if (binding.videoView.currentPosition >= binding.videoView.duration - 100) {
                binding.videoView.seekTo(0)
                binding.seekBar.progress = 0
            }

            binding.videoView.start()
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            isPlaying = true
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
                if (binding.videoView.isPlaying) {
                    val currentPosition = binding.videoView.currentPosition / 1000.0
                    val subtitle = subtitleItems.find {
                        currentPosition >= it.second - 0.5 && currentPosition <= it.second + 2.0
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
                handler.postDelayed(this, 100)
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

    private fun fetchTranslatedVideoData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "${BuildConfig.BASE_URL}gettranslatevideo?room_id=$roomId&message_id=$messageId"
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()

                response.use {
                    val body = response.body?.string()
                    Log.d("VideoPlayer", "Translated data response: ${body?.take(200)}")

                    if (response.isSuccessful && body != null) {
                        val json = org.json.JSONObject(body)
                        if (json.optString("status") == "success") {
                            val data = json.optJSONObject("data")
                            val timestampsJson = data?.optString("timestamps_json")

                            // Parse subtitles
                            if (!timestampsJson.isNullOrEmpty()) {
                                val jsonArray = JSONArray(timestampsJson)
                                subtitleItems.clear()

                                for (i in 0 until jsonArray.length()) {
                                    val item = jsonArray.getJSONObject(i)
                                    val second = item.optDouble("second", 0.0)
                                    val text = item.optString("text", "")
                                    subtitleItems.add(SubtitleItem(second, text))
                                }

                                withContext(Dispatchers.Main) {
                                    // Start subtitle tracking if video is playing
                                    if (isPlaying) {
                                        startSubtitleTracking()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VideoPlayer", "Error fetching translated data: ${e.message}", e)
            }
        }
    }

    private fun translateASL() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnTranslateASL.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = org.json.JSONObject().apply {
                    put("video_url", videoUrl)
                    put("room_id", roomId)
                    put("frame_rate", frameRate)
                    put("resolution", resolution)
                    put("generate_srt", false)
                    put("message_id", messageId)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = json.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("${BuildConfig.BASE_URL}translateasl")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val jsonResponse = org.json.JSONObject(body)
                            if (jsonResponse.optString("status") == "success") {
                                // Fetch the translated data
                                fetchTranslatedVideoData()
                            }
                        }
                    }
                    response.close()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnTranslateASL.visibility = View.VISIBLE
                    Log.e("VideoPlayer", "Error translating: ${e.message}", e)
                }
            }
        }
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