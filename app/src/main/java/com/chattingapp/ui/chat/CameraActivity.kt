package com.chattingapp.ui.chat

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chattingapp.BuildConfig
import com.chattingapp.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.camera.lifecycle.ProcessCameraProvider
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var loadingOverlay: RelativeLayout   // ⬅️ ADDED

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var recordedVideoUri: Uri? = null
    private var isFrontCamera = true

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var videoResolution: String = ""
    private var videoFps: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        // Request ALL permissions at once
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            startCamera()   // ← CAMERA STARTS IMMEDIATELY
        }

        findViewById<ImageView>(R.id.btnRecord).setOnClickListener { captureVideo() }
        findViewById<ImageView>(R.id.btnFlip).setOnClickListener {
            isFrontCamera = !isFrontCamera
            startCamera()
        }
        findViewById<ImageView>(R.id.btnClose).setOnClickListener { finish() }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            // Check if ALL permissions are granted
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                // Reinitialize camera after permissions are granted
                startCamera()
            } else {
                // Check which permission was denied
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }

                if (Manifest.permission.RECORD_AUDIO in deniedPermissions) {
                    Toast.makeText(this, "Audio permission is required for recording", Toast.LENGTH_LONG).show()
                    // Don't finish, let user retry
                }

                if (Manifest.permission.CAMERA in deniedPermissions) {
                    Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
                    finish() // Can't use camera without camera permission
                }
            }
        }
    }

    private fun startCamera() {
        // Check if audio permission is granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Log but don't request here - it's already handled in onCreate/onResume
            Log.e("CAMERA", "Audio permission not granted")
            Toast.makeText(this, "Audio permission required for recording", Toast.LENGTH_LONG).show()
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val provider = providerFuture.get()

            // Unbind first
            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Bind with audio enabled from the start
                provider.bindToLifecycle(this, selector, preview, videoCapture)
                Log.d("CAMERA", "Camera bound successfully - front: $isFrontCamera")

                // Make sure preview is visible
                previewView.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e("CAMERA", "Failed to bind camera: ${e.message}")
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /// ▶️ Metadata extractor (resolution & fps)
    private fun extractMetadata(uri: Uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            videoResolution = "${width}x${height}"

            val fpsString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            videoFps = fpsString?.toFloat()?.toInt() ?: 30

            retriever.release()

            Log.d("VIDEO_META", "Resolution = $videoResolution, FPS = $videoFps")

        } catch (e: Exception) {
            videoResolution = "Unknown"
            videoFps = 30
            Log.e("VIDEO_META", "Metadata extract failed: ${e.localizedMessage}")
        }
    }

    private fun captureVideo() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Audio permission required for recording", Toast.LENGTH_LONG).show()
            return
        }

        val btnRecord = findViewById<ImageView>(R.id.btnRecord)

        if (recording != null) {
            recording?.stop()
            recording = null
            btnRecord.setImageResource(R.drawable.ic_record)
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        }

        val options = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        recording = videoCapture?.output
            ?.prepareRecording(this, options)
            ?.withAudioEnabled()
            ?.apply {
                if (ContextCompat.checkSelfPermission(
                        this@CameraActivity, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                    Log.d("AUDIO", "Audio recording enabled")
                } else {
                    Log.e("AUDIO", "Audio permission not granted")
                }
            }
            ?.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> btnRecord.setImageResource(R.drawable.ic_stop)
                    is VideoRecordEvent.Finalize -> {
                        btnRecord.setImageResource(R.drawable.ic_record)
                        if (!event.hasError()) {
                            recordedVideoUri = event.outputResults.outputUri

                            /// 🆕 Read real metadata
                            extractMetadata(recordedVideoUri!!)

                            Handler(Looper.getMainLooper()).postDelayed({
                                uploadVideo()
                            }, 500)
                        } else Toast.makeText(this, "Recording failed!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("video_", ".mp4", cacheDir)
            val output = FileOutputStream(tempFile)
            inputStream.copyTo(output)
            output.close()
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    private fun uploadVideo() {
        loadingOverlay.visibility = View.VISIBLE

        val uri = recordedVideoUri ?: return hideAndError("Failed preparing video!")
        val file = uriToFile(uri) ?: return hideAndError("Failed preparing video!")

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("video", file.name, file.asRequestBody("video/mp4".toMediaTypeOrNull()))
            .addFormDataPart("resolution", videoResolution)
            .addFormDataPart("frame_rate", videoFps.toString())
            .addFormDataPart("translate_yn", "N")
            .build()

        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}uploadvideonote")
            .post(requestBody)
            .build()

        Log.d("UPLOAD_VIDEO", "Upload URL = ${request.url}")
        Log.d("UPLOAD_VIDEO", "Uploading file: ${file.name}, size=${file.length()} bytes")

        scope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d("UPLOAD_VIDEO", "Response code: ${response.code}")
                Log.d("UPLOAD_VIDEO", "Response body: $responseBody")

                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE

                    if (response.isSuccessful) {
                        Log.i("UPLOAD_VIDEO", "✅ Upload Success")

                        // Get roomId and senderId from intent
                        val roomId = intent.getStringExtra("room_id") ?: ""
                        val senderId = intent.getStringExtra("sender_id") ?: ""

                        // Parse response to get message_id if available
                        var messageId = ""
                        try {
                            if (responseBody != null) {
                                val json = JSONObject(responseBody)
                                messageId = json.optString("message_id", "")
                            }
                        } catch (e: Exception) {
                            Log.e("UPLOAD_VIDEO", "Failed to parse message_id", e)
                        }

                        // Start MediaPreviewActivity with all necessary data
                        val intent = Intent(this@CameraActivity, MediaPreviewActivity::class.java).apply {
                            data = uri
                            putExtra(MediaPreviewActivity.EXTRA_VIDEO_URI, uri)
                            putExtra(MediaPreviewActivity.EXTRA_VIDEO_FILE_PATH, file.absolutePath)
                            putExtra(MediaPreviewActivity.EXTRA_VIDEO_FILE_NAME, file.name)
                            putExtra(MediaPreviewActivity.EXTRA_VIDEO_RESOLUTION, videoResolution)
                            putExtra(MediaPreviewActivity.EXTRA_VIDEO_FPS, videoFps)
                            putExtra(MediaPreviewActivity.EXTRA_ROOM_ID, roomId)
                            putExtra(MediaPreviewActivity.EXTRA_SENDER_ID, senderId)
                            putExtra(MediaPreviewActivity.EXTRA_MESSAGE_ID, messageId)
                        }
                        startActivity(intent)
                        finish()

                    } else {
                        Log.e("UPLOAD_VIDEO", "❌ Upload failed: ${response.message}")
                        showErrorDialog("Upload failed: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("UPLOAD_VIDEO", "❌ Error: ${e.localizedMessage}")
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    showErrorDialog(e.message ?: "Network error")
                }
            }
        }
    }

    private fun hideAndError(msg: String) {
        loadingOverlay.visibility = View.GONE
        showErrorDialog(msg)
    }

    private fun showErrorDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Upload Failed")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ -> uploadVideo() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}