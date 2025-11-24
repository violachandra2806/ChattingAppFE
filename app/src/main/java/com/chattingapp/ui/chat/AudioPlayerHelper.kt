package com.chattingapp.ui.chat

import android.content.Context
import android.media.MediaPlayer

class AudioPlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null


    fun playFromFile(filePath: String, onComplete: (() -> Unit)? = null) {
        release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()
            setOnCompletionListener {
                onComplete?.invoke()
                release()
            }
        }
    }


    fun playFromUrl(url: String, onComplete: (() -> Unit)? = null) {
        release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            prepareAsync()
            setOnPreparedListener { mp ->
                mp.start()
            }
            setOnCompletionListener {
                onComplete?.invoke()
                release()
            }
        }
    }


    fun pause() {
        mediaPlayer?.pause()
    }


    fun release() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

