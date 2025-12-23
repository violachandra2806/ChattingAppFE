package com.chattingapp.ui.forgotpassword

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.chattingapp.ui.login.LoginActivity
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var backIcon: ImageView
    private lateinit var editTextEmail: EditText
    private lateinit var buttonSendInstructions: Button
    private lateinit var textViewInstructions: TextView
    private lateinit var textNoAccount: TextView
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        backIcon = findViewById(R.id.backIcon)
        editTextEmail = findViewById(R.id.editTextEmail)
        buttonSendInstructions = findViewById(R.id.buttonVerification)
        textViewInstructions = findViewById(R.id.textInstructions)
        textNoAccount = findViewById(R.id.textNoAccount)

        backIcon.setOnClickListener {
            finish()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            onBackPressedDispatcher.onBackPressed()
        }

        val userId = prefs.getString("user_id", null)
        if (userId != null) {
            textNoAccount.visibility = TextView.GONE
        } else {
            textNoAccount.visibility = TextView.VISIBLE
        }

        buttonSendInstructions.setOnClickListener { handleSendInstructions() }
    }

    private fun handleSendInstructions() {
        val email = editTextEmail.text.toString().trim()

        if (email.isEmpty()) {
            val message = getString(R.string.msg_email_required)
            editTextEmail.error = message
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            val message = getString(R.string.msg_email_invalid)
            editTextEmail.error = message
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }

        // Send payload to /chattingapp/sendemail
        Thread {
            try {
            val url = URL("${BuildConfig.BASE_URL}sendemail")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000

                val payload = JSONObject()
                payload.put("user_email", email)

                conn.outputStream.use { os ->
                    OutputStreamWriter(os, "UTF-8").use { it.write(payload.toString()) }
                }

                val code = conn.responseCode
                val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                runOnUiThread {
                    if (code in 200..299) {
                        Toast.makeText(this, getString(R.string.msg_email_sent), Toast.LENGTH_SHORT).show()
                            try {
                                val respJson = JSONObject(resp)
                                val status = respJson.optString("status", "")
                                val message = respJson.optString("message", "")
                                // response has data array with send_result and verification_code_set
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        // go to verification screen
                        val intent = Intent(this, VerificationCodeActivity::class.java)
                        intent.putExtra("user_email", email)
                        // pass user_id if exists
                        val userId = prefs.getString("user_id", null)
                        if (userId != null) intent.putExtra("user_id", userId)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, getString(R.string.msg_email_send_failed), Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.msg_network_error), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
