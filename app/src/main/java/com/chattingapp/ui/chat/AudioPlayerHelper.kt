package com.chattingapp.ui.chat

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

class AudioPlayerHelper {
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingUrl: String? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onProgressListener: ((progress: Float, currentPos: Int, duration: Int) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            try {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val current = player.currentPosition
                        val total = player.duration
                        if (total > 0) {
                            val progress = current.toFloat() / total.toFloat()
                            onProgressListener?.invoke(progress, current, total)
                        }
                        handler.postDelayed(this, 100)
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e("AudioPlayer", "Progress update - invalid state: ${e.message}")
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Progress update error: ${e.message}")
            }
        }
    }

    fun play(
        url: String,
        onProgress: (Float, Int, Int) -> Unit = { _, _, _ -> },
        onComplete: () -> Unit
    ) {
        try {
            // ✅ FIX: Safe cleanup before creating new player
            cleanupMediaPlayer()

            currentPlayingUrl = url
            onCompletionListener = onComplete
            onProgressListener = onProgress

            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener {
                    try {
                        start()
                        handler.post(progressRunnable)
                        Log.d("AudioPlayer", "Started playing: $url")
                    } catch (e: IllegalStateException) {
                        Log.e("AudioPlayer", "Start failed: ${e.message}")
                        onCompletionListener?.invoke()
                        cleanupMediaPlayer()
                    }
                }
                setOnCompletionListener {
                    handler.removeCallbacks(progressRunnable)
                    Log.d("AudioPlayer", "Playback completed")
                    val callback = onCompletionListener
                    cleanupMediaPlayer()
                    callback?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    handler.removeCallbacks(progressRunnable)
                    Log.e("AudioPlayer", "Error: what=$what, extra=$extra")
                    val callback = onCompletionListener
                    cleanupMediaPlayer()
                    callback?.invoke()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio: ${e.message}", e)
            onCompletionListener?.invoke()
            cleanupMediaPlayer()
        }
    }

    fun stop() {
        try {
            handler.removeCallbacks(progressRunnable)
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                        Log.d("AudioPlayer", "Stopped playback")
                    } else {}
                } catch (e: IllegalStateException) {
                    Log.e("AudioPlayer", "Stop - invalid state: ${e.message}")
                }
            }
            cleanupMediaPlayer()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping audio: ${e.message}", e)
        }
    }

    fun release() {
        cleanupMediaPlayer()
    }

    private fun cleanupMediaPlayer() {
        try {
            handler.removeCallbacks(progressRunnable)

            mediaPlayer?.let { player ->
                try {
                    player.reset()
                    Log.d("AudioPlayer", "MediaPlayer reset")
                } catch (e: IllegalStateException) {
                    Log.w("AudioPlayer", "Reset failed: ${e.message}")
                }

                try {
                    player.release()
                    Log.d("AudioPlayer", "MediaPlayer released")
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Release error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Cleanup error: ${e.message}", e)
        } finally {
            mediaPlayer = null
            currentPlayingUrl = null
            onCompletionListener = null
            onProgressListener = null
        }
    }

    fun isPlaying(url: String): Boolean {
        return try {
            currentPlayingUrl == url && mediaPlayer?.isPlaying == true
        } catch (e: IllegalStateException) {
            Log.e("AudioPlayer", "isPlaying check failed: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e("AudioPlayer", "isPlaying unexpected error: ${e.message}")
            false
        }
    }
}