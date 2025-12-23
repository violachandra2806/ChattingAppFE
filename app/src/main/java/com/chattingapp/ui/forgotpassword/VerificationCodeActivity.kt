package com.chattingapp.ui.forgotpassword

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
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

class VerificationCodeActivity : AppCompatActivity() {

    private lateinit var code1: EditText
    private lateinit var code2: EditText
    private lateinit var code3: EditText
    private lateinit var code4: EditText
    private lateinit var newPassword: EditText
    private lateinit var confirmPassword: EditText
    private lateinit var buttonSubmit: Button
    private lateinit var buttonResend: Button

    private var userEmail: String? = null
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_code)

        code1 = findViewById(R.id.code1)
        code2 = findViewById(R.id.code2)
        code3 = findViewById(R.id.code3)
        code4 = findViewById(R.id.code4)
        newPassword = findViewById(R.id.editNewPassword)
        confirmPassword = findViewById(R.id.editConfirmPassword)
        buttonSubmit = findViewById(R.id.buttonSubmitCode)
        buttonResend = findViewById(R.id.buttonResend)

        userEmail = intent.getStringExtra("user_email")
        userId = intent.getStringExtra("user_id")

        buttonSubmit.setOnClickListener { handleSubmit() }

        // disable resend for 20 seconds initially and start countdown
        startResendCountdown()

        buttonResend.setOnClickListener {
            // resend the verification email
            resendEmail()
        }
    }

    private fun handleSubmit() {
        val code = (code1.text.toString().trim() + code2.text.toString().trim() + code3.text.toString().trim() + code4.text.toString().trim())
        val npass = newPassword.text.toString()
        val cpass = confirmPassword.text.toString()

        if (code.length != 4) {
            Toast.makeText(this, "Masukkan 4 kode verifikasi", Toast.LENGTH_SHORT).show()
            return
        }

        if (npass.length < 8 || !npass.matches(Regex("(?=.*[0-9])(?=.*[A-Za-z]).{8,}"))) {
            Toast.makeText(this, "Password harus >=8 karakter dan mengandung huruf & angka", Toast.LENGTH_LONG).show()
            return
        }

        if (npass != cpass) {
            Toast.makeText(this, "Konfirmasi password tidak sama", Toast.LENGTH_SHORT).show()
            return
        }

        // send to /chattingapp/verificatepassword
        Thread {
            try {
                val url = URL("${BuildConfig.BASE_URL}chattingapp/verificatepassword")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true

                val payload = JSONObject()
                if (userId != null) payload.put("user_id", userId) else payload.put("user_id", JSONObject.NULL)
                payload.put("user_email", userEmail ?: "")
                payload.put("verification_code", code)
                payload.put("new_password", npass)

                conn.outputStream.use { os -> OutputStreamWriter(os, "UTF-8").use { it.write(payload.toString()) } }

                val codeResp = conn.responseCode
                val resp = conn.inputStream.bufferedReader().use { it.readText() }

                runOnUiThread {
                    if (codeResp in 200..299) {
                        Toast.makeText(this, "Password berhasil diubah", Toast.LENGTH_SHORT).show()
                            try {
                                val respJson = JSONObject(resp)
                                val status = respJson.optString("status", "")
                                val message = respJson.optString("message", "")
                                // response: { "code": 0, "data": [], "message": "Password updated successfully", "status": "success" }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        val i = Intent(this, LoginActivity::class.java)
                        i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(i)
                        finish()
                    } else {
                        Toast.makeText(this, "Verifikasi gagal", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this, "Terjadi kesalahan jaringan", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun resendEmail() {
        val email = userEmail ?: return
        buttonResend.isEnabled = false
        // send same payload as ForgotPasswordActivity
        Thread {
            try {
                val url = URL("${BuildConfig.BASE_URL}chattingapp/sendemail")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true

                val payload = JSONObject()
                payload.put("user_email", email)

                conn.outputStream.use { os -> OutputStreamWriter(os, "UTF-8").use { it.write(payload.toString()) } }

                val codeResp = conn.responseCode
                conn.inputStream.bufferedReader().use { it.readText() }

                runOnUiThread {
                    if (codeResp in 200..299) {
                        Toast.makeText(this, "Email terkirim ulang", Toast.LENGTH_SHORT).show()
                            try {
                                // response has data array with send_result and verification_code_set
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        startResendCountdown()
                    } else {
                        Toast.makeText(this, "Gagal mengirim ulang", Toast.LENGTH_LONG).show()
                        buttonResend.isEnabled = true
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Terjadi kesalahan jaringan", Toast.LENGTH_SHORT).show()
                    buttonResend.isEnabled = true
                }
            }
        }.start()
    }

    private var resendTimer: CountDownTimer? = null

    private fun startResendCountdown() {
        resendTimer?.cancel()
        val totalMs = 20_000L
        buttonResend.isEnabled = false
        resendTimer = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000L
                buttonResend.text = "Resend (${seconds}s)"
            }

            override fun onFinish() {
                buttonResend.text = "Resend"
                buttonResend.isEnabled = true
            }
        }
        resendTimer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }
}
