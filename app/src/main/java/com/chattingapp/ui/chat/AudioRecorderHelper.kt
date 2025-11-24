package com.chattingapp.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File


class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null


    fun startRecording(): File {
        audioFile = File(context.cacheDir, "voice_\${System.currentTimeMillis()}.m4a")
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile!!.absolutePath)
            prepare()
            start()
        }
        return audioFile!!
    }


    fun stopRecording(): File? {
        try {
            recorder?.stop()
        } catch (e: Exception) {
// ignore stop errors
        }
        recorder?.release()
        recorder = null
        return audioFile
    }


    fun cancel() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
        }
        recorder?.release()
        recorder = null
        audioFile?.delete()
        audioFile = null
    }
}