package com.chattingapp.ui.editprofile

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
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class EditProfileActivity : AppCompatActivity() {

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

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

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

        backIcon.setOnClickListener { finish() }

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
                val url = URL("${BuildConfig.BASE_URL}getuserdetailsbyid?user_id=$userId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                val code = conn.responseCode
                val stream: InputStream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                val profilePicture = if (json.has("profile_picture")) json.getString("profile_picture") else ""
                val username = if (json.has("username")) json.getString("username") else ""
                val userEmail = if (json.has("user_email")) json.getString("user_email") else ""
                val dob = if (json.has("dob")) json.getString("dob") else ""

                runOnUiThread {
                    originalUsername = username
                    originalEmail = userEmail
                    originalDob = dob

                    editUsername.setText(username)
                    editEmail.setText(userEmail)
                    editDob.setText(dob)

                    if (profilePicture.isNotBlank()) loadImageFromUrl(profilePicture, profileImage)
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

        val payload = JSONObject()
        payload.put("user_id", userId)
        payload.put("username", editUsername.text.toString().trim())
        payload.put("user_email", editEmail.text.toString().trim())
        payload.put("dob", editDob.text.toString().trim())

        buttonSave.isEnabled = false

        Thread {
            try {
                val url = URL("${BuildConfig.BASE_URL}updateuser")
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
