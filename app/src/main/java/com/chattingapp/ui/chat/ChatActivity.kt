package com.chattingapp.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chattingapp.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class ChatActivity : AppCompatActivity() {

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var recordButton: Button
    private lateinit var audioFile: File
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var playButton: Button

    // OkHttp client shared
    private val httpClient = OkHttpClient()

    companion object {
        private const val REQUEST_MIC_PERMISSION = 111
        // replace with your real server IP + port
        private const val BASE_SERVER = "http://192.168.38.180:8080"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recordButton = findViewById(R.id.btnRecord)
        playButton = findViewById(R.id.btnPlay)

        // Start / Stop recording
        recordButton.setOnClickListener {
            if (!isRecording) {
                checkPermissionAndStart()
            } else {
                stopRecording()
                // upload AFTER stopRecording so audioFile exists & finalized
                uploadVoiceNote()
            }
        }

        playButton.setOnClickListener {
            playAudio()
        }
    }

    /** Permission check */
    private fun checkPermissionAndStart() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_MIC_PERMISSION)
        } else {
            startRecording()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_MIC_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            Toast.makeText(this, "Mic permission required!", Toast.LENGTH_SHORT).show()
        }
    }

    /** Start recording */
    private fun startRecording() {
        try {
            audioFile = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            recordButton.text = "Stop Recording"
            Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to record: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Stop recording */
    private fun stopRecording() {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false

            recordButton.text = "Start Recording"

            Toast.makeText(this, "Size: ${audioFile.length()} bytes", Toast.LENGTH_LONG).show()
            Toast.makeText(this, "Recording saved: ${audioFile.name}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Stop error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Upload voice note to backend as multipart/form-data */
    private fun uploadVoiceNote() {
        if (!this::audioFile.isInitialized || !audioFile.exists()) {
            Toast.makeText(this, "No audio file to upload", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "$BASE_SERVER/chattingapp/voicenote/upload"

        // create multipart body
        val fileBody = audioFile.asRequestBody("audio/m4a".toMediaType())
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("voice", audioFile.name, fileBody)
            // TODO: replace with actual sender/room IDs from your app session
            .addFormDataPart("sender_id", "U00004")
            .addFormDataPart("room_id", "R00001")
            .addFormDataPart("duration_sec", "3")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(multipart)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "Upload error: $bodyStr", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                try {
                    val json = JSONObject(bodyStr ?: "{}")
                    // adapt parsing depending on your backend response format
                    // example assumes {"status":"success","message_id":"...","media_url":"..."}
                    val messageId = json.optString("message_id", json.optString("data", ""))
                    val mediaUrl = json.optString("media_url", json.optString("media_url", ""))

                    // if backend wraps result in data array, adapt accordingly
                    if (messageId.isNullOrEmpty()) {
                        // try to find inside data array
                        val dataArr = json.optJSONArray("data")
                        if (dataArr != null && dataArr.length() > 0) {
                            val obj = dataArr.getJSONObject(0)
                            if (obj.has("message_id")) {
                                // depends on your wrapper
                                // message_id may be inside obj or result structure — adjust as needed
                            }
                        }
                    }

                    // If you have messageId and mediaUrl, call transcribe
                    if (messageId.isNotEmpty() && mediaUrl.isNotEmpty()) {
                        callTranscribe(messageId, mediaUrl)
                    }

                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "Upload success", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "Upload parse error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    /** Call transcribe endpoint */
    private fun callTranscribe(messageId: String, mediaUrl: String) {
        val url = "$BASE_SERVER/chattingapp/voicenote/transcribe"
        val json = JSONObject().apply {
            put("message_id", messageId)
            put("media_url", mediaUrl)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Transcribe failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "Transcribe error: $bodyStr", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Transcribe success: $bodyStr", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    /** Play back local recorded audio */
    private fun playAudio() {
        if (!this::audioFile.isInitialized || !audioFile.exists()) {
            Toast.makeText(this, "No audio recorded yet!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "Playing audio...", Toast.LENGTH_SHORT).show()
            mediaPlayer?.setOnCompletionListener {
                Toast.makeText(this, "Playback completed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Play error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            // stop without uploading here because we prefer to upload on STOP click
            stopRecording()
        }
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
