package com.chattingapp.ui.editprofile

import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.chattingapp.ui.forgotpassword.ForgotPasswordActivity
import android.util.Log
import com.chattingapp.utils.AvatarUtils
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class EditProfileActivity : AppCompatActivity() {

    private fun formatDobDateOnly(dobString: String?): String {
        val raw = dobString?.trim().orEmpty()
        if (raw.isBlank()) return ""
        if (raw.equals("null", true)) return ""

        // Already in desired format
        if (Regex("\\d{2}/\\d{2}/\\d{4}").matches(raw)) return raw

        val utc = TimeZone.getTimeZone("UTC")
        val possibleFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        ).onEach { it.timeZone = utc }

        for (format in possibleFormats) {
            try {
                val date = format.parse(raw)
                if (date != null) {
                    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                        timeZone = utc
                    }.format(date)
                }
            } catch (_: Exception) {
            }
        }

        return ""
    }

    private fun formatDobForBackend(dobString: String?): String {
        val raw = dobString?.trim().orEmpty()
        if (raw.isBlank()) return ""
        if (raw.equals("null", true)) return ""

        // Already in a common backend-friendly format
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(raw)) return raw

        val utc = TimeZone.getTimeZone("UTC")
        val possibleFormats = listOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        ).onEach { it.timeZone = utc }

        for (format in possibleFormats) {
            try {
                val date = format.parse(raw)
                if (date != null) {
                    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = utc
                    }.format(date)
                }
            } catch (_: Exception) {
            }
        }

        // Fallback: send raw so existing behavior isn't worse if backend accepts it
        return raw
    }

    private lateinit var backIcon: ImageView
    private lateinit var profileImage: ImageView
    private lateinit var editUsername: EditText
    private lateinit var editEmail: EditText
    private lateinit var editDob: EditText
    private lateinit var editPassword: EditText
    private lateinit var buttonSave: Button
    private lateinit var changePassword: TextView

    private var originalUsername: String? = null
    private var originalEmail: String? = null
    private var originalDob: String? = null

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        // Use the same preference file that LoginActivity writes to
        prefs = getSharedPreferences("UserData", MODE_PRIVATE)

        backIcon = findViewById(R.id.backIcon)
        profileImage = findViewById(R.id.profileImage)
        editUsername = findViewById(R.id.editUsername)
        editEmail = findViewById(R.id.editEmail)
        editDob = findViewById(R.id.editDob)
        editPassword = findViewById(R.id.editPassword)
        buttonSave = findViewById(R.id.buttonSave)
        changePassword = findViewById(R.id.textChangePassword)

        editPassword.isEnabled = false
        editPassword.setText(getString(R.string.placeholder_password_mask))

        // DOB should be picked from a calendar (no manual typing)
        editDob.keyListener = null
        editDob.isFocusable = false
        editDob.isFocusableInTouchMode = false
        editDob.isCursorVisible = false
        editDob.setOnClickListener { showDobPicker() }

        backIcon.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        buttonSave.isEnabled = false
        buttonSave.alpha = 0.5f

        addChangeListeners()

        val userId = prefs.getString("user_id", null)
        if (userId != null) fetchUserDetails(userId) else Toast.makeText(this, getString(R.string.msg_user_not_logged_in), Toast.LENGTH_SHORT).show()

        buttonSave.setOnClickListener { handleSave() }

        changePassword.setOnClickListener {
            val i = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(i)
        }
    }

    private fun showDobPicker() {
        val utc = TimeZone.getTimeZone("UTC")

        val cal = Calendar.getInstance(utc)
        val currentText = editDob.text?.toString().orEmpty().trim()
        if (currentText.isNotBlank() && !currentText.equals("null", true)) {
            try {
                val parsed = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                    timeZone = utc
                }.parse(currentText)
                if (parsed != null) cal.time = parsed
            } catch (_: Exception) {
            }
        }

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance(utc)
                selected.set(year, month, dayOfMonth)
                val formatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                    timeZone = utc
                }.format(selected.time)
                editDob.setText(formatted)
                toggleSaveIfChanged()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun addChangeListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { toggleSaveIfChanged() }
            override fun afterTextChanged(s: Editable?) {}
        }
        editUsername.addTextChangedListener(watcher)
        editEmail.addTextChangedListener(watcher)
        editDob.addTextChangedListener(watcher)
    }

    private fun toggleSaveIfChanged() {
        val changed = (originalUsername != editUsername.text.toString()) ||
                (originalEmail != editEmail.text.toString()) ||
                (originalDob != editDob.text.toString())
        buttonSave.isEnabled = changed
        buttonSave.alpha = if (changed) 1.0f else 0.5f
    }

    private fun fetchUserDetails(userId: String) {
        Thread {
            try {
                val base = if (BuildConfig.BASE_URL.endsWith("/")) BuildConfig.BASE_URL else BuildConfig.BASE_URL + "/"
                val url = URL("${base}getuserdetailsbyid?user_id=$userId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                val code = conn.responseCode
                val stream: InputStream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                Log.d("EditProfile", "getuserdetailsbyid response: $root")

                val userObj = if (root.has("data") && root.optJSONArray("data") != null && root.optJSONArray("data")!!.length() > 0) {
                    root.optJSONArray("data")!!.getJSONObject(0)
                } else {
                    root
                }

                val username = userObj.optString("username", "").trim()
                val userEmail = userObj.optString("user_email", "").trim()

                fun looksLikeUrl(s: String?): Boolean {
                    val v = s?.trim().orEmpty()
                    return v.startsWith("http://", ignoreCase = true) || v.startsWith("https://", ignoreCase = true)
                }

                fun looksLikeDateLikeString(s: String?): Boolean {
                    val v = s?.trim().orEmpty()
                    if (v.isBlank()) return false
                    if (v.equals("null", true)) return false
                    if (v.equals("true", true) || v.equals("false", true)) return false
                    if (looksLikeUrl(v)) return false
                    // Your API returns strings like "Fri, 11 Jul 2025 00:00:00 GMT"
                    return v.contains("GMT", ignoreCase = true) || v.contains(",") || v.contains("-")
                }

                val rawDob = userObj.optString("dob", "").trim()
                val dob = if (looksLikeDateLikeString(rawDob)) {
                    rawDob
                } else {
                    // Some responses appear to have fields swapped; use bio as fallback if it looks like a date
                    val rawBio = userObj.optString("bio", "").trim()
                    if (looksLikeDateLikeString(rawBio)) rawBio else rawDob
                }

                val dobDisplay = formatDobDateOnly(dob)

                val profilePictureCandidate = userObj.optString("profile_picture", "").trim()
                val blockedUserCandidate = userObj.optString("blocked_user", "").trim()
                val profilePicture = when {
                    looksLikeUrl(profilePictureCandidate) -> profilePictureCandidate
                    looksLikeUrl(blockedUserCandidate) -> blockedUserCandidate
                    else -> ""
                }

                runOnUiThread {
                    originalUsername = username
                    originalEmail = userEmail
                    originalDob = dobDisplay

                    editUsername.setText(username)
                    editEmail.setText(userEmail)
                    editDob.setText(dobDisplay)

                    // Load profile picture; fallback to initial with random (stable) background
                    AvatarUtils.loadInto(profileImage, profilePicture, username)
                    toggleSaveIfChanged()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this, getString(R.string.msg_failed_load_user_data), Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun handleSave() {
        val userId = prefs.getString("user_id", null) ?: run {
            Toast.makeText(this, getString(R.string.msg_user_not_logged_in), Toast.LENGTH_SHORT).show()
            return
        }

        val dobRawUi = editDob.text.toString().trim()
        val dobBackend = formatDobForBackend(dobRawUi).trim()

        val payload = JSONObject()
        payload.put("user_id", userId)
        payload.put("username", editUsername.text.toString().trim())
        payload.put("user_email", editEmail.text.toString().trim())
        payload.put("dob", dobBackend)

        Log.d("EditProfile", "Saving profile: dob_ui='$dobRawUi' dob_backend='$dobBackend'")
        Log.d("EditProfile", "Payload: ${payload}")

        buttonSave.isEnabled = false

        Thread {
            try {
                val urlString = "${BuildConfig.BASE_URL}editprofile"
                Log.d("EditProfile", "POST $urlString")
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                conn.outputStream.use { os -> os.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val respCode = conn.responseCode
                val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
                val resp = stream.bufferedReader().use { it.readText() }

                Log.d("EditProfile", "Response code=$respCode")
                Log.d("EditProfile", "Response body=$resp")

                runOnUiThread {
                    if (respCode in 200..299) {
                        Toast.makeText(this, getString(R.string.msg_profile_updated), Toast.LENGTH_SHORT).show()
                        originalUsername = editUsername.text.toString()
                        originalEmail = editEmail.text.toString()
                        originalDob = editDob.text.toString()
                        toggleSaveIfChanged()
                    } else {
                        Toast.makeText(this, getString(R.string.msg_save_failed), Toast.LENGTH_LONG).show()
                        buttonSave.isEnabled = true
                    }
                }


            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("EditProfile", "Save failed", e)
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.msg_network_error), Toast.LENGTH_SHORT).show()
                    buttonSave.isEnabled = true
                }
            }
        }.start()
    }

    private fun loadImageFromUrl(urlString: String, imageView: ImageView) {
        Thread {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.doInput = true
                conn.connect()
                val input = conn.inputStream
                val bitmap = BitmapFactory.decodeStream(input)
                runOnUiThread { imageView.setImageBitmap(bitmap) }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
}
