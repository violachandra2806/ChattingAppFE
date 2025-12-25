package com.chattingapp.ui.forgotpassword

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
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

        setupOtpInputs()

        buttonSubmit.setOnClickListener { handleSubmit() }

        // disable resend for 20 seconds initially and start countdown
        startResendCountdown()

        buttonResend.setOnClickListener {
            // resend the verification email
            resendEmail()
        }
    }

    private fun setupOtpInputs() {
        val fields = listOf(code1, code2, code3, code4)

        // Override XML maxLength=1 so paste (e.g., "1234") can be captured and distributed.
        // We'll still enforce one digit per box via TextWatcher.
        fields.forEach { it.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(fields.size)) }

        var isProgrammaticChange = false

        fun distributeFrom(startIndex: Int, raw: String) {
            val digits = raw.filter { it.isDigit() }
            if (digits.isEmpty()) return

            isProgrammaticChange = true
            try {
                var idx = startIndex
                for (ch in digits) {
                    if (idx >= fields.size) break
                    fields[idx].setText(ch.toString())
                    fields[idx].setSelection(fields[idx].text?.length ?: 0)
                    idx++
                }

                // Focus next empty field (or last)
                val nextEmpty = fields.indexOfFirst { it.text?.toString().orEmpty().isBlank() }
                val focusIndex = if (nextEmpty == -1) fields.size - 1 else nextEmpty
                fields[focusIndex].requestFocus()
            } finally {
                isProgrammaticChange = false
            }
        }

        fields.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isProgrammaticChange) return
                    val text = s?.toString().orEmpty()

                    when {
                        text.length > 1 -> {
                            // Paste case (or IME inserted multiple chars)
                            distributeFrom(index, text)
                        }
                        text.length == 1 -> {
                            // Normal typing: move to next box
                            if (index < fields.size - 1) fields[index + 1].requestFocus()
                        }
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isProgrammaticChange) return
                    // Keep only 1 digit in each box
                    val t = s?.toString().orEmpty()
                    if (t.length <= 1) return
                    val firstDigit = t.firstOrNull { it.isDigit() }?.toString().orEmpty()
                    isProgrammaticChange = true
                    try {
                        editText.setText(firstDigit)
                        editText.setSelection(editText.text?.length ?: 0)
                    } finally {
                        isProgrammaticChange = false
                    }
                }
            })

            // Backspace: if empty, go to previous and clear it
            editText.setOnKeyListener { v: View, keyCode: Int, event: KeyEvent ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    val current = (v as EditText).text?.toString().orEmpty()
                    if (current.isEmpty() && index > 0) {
                        val prev = fields[index - 1]
                        prev.requestFocus()
                        prev.setText("")
                        return@setOnKeyListener true
                    }
                }
                false
            }

            // Convenient: tap focuses and selects existing digit
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) editText.selectAll()
            }
        }

        code1.requestFocus()
    }

    private fun handleSubmit() {
        val code = (code1.text.toString().trim() + code2.text.toString().trim() + code3.text.toString().trim() + code4.text.toString().trim())
        val npass = newPassword.text.toString()
        val cpass = confirmPassword.text.toString()

        if (code.length != 4) {
            Toast.makeText(this, getString(R.string.msg_verification_code_required), Toast.LENGTH_SHORT).show()
            return
        }

        if (npass.length < 8 || !npass.matches(Regex("(?=.*[0-9])(?=.*[A-Za-z]).{8,}"))) {
            Toast.makeText(this, getString(R.string.msg_password_policy), Toast.LENGTH_LONG).show()
            return
        }

        if (npass != cpass) {
            Toast.makeText(this, getString(R.string.msg_password_confirmation_mismatch), Toast.LENGTH_SHORT).show()
            return
        }

        // send to /chattingapp/verificatepassword
        Thread {
            try {
                val url = URL("${BuildConfig.BASE_URL}verificatepassword")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000

                val payload = JSONObject()
                if (userId != null) payload.put("user_id", userId) else payload.put("user_id", JSONObject.NULL)
                payload.put("user_email", userEmail ?: "")
                payload.put("verification_code", code)
                payload.put("new_password", npass)

                conn.outputStream.use { os -> OutputStreamWriter(os, "UTF-8").use { it.write(payload.toString()) } }

                val codeResp = conn.responseCode
                val resp = (if (codeResp in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                runOnUiThread {
                    if (codeResp in 200..299) {
                        Toast.makeText(this, getString(R.string.msg_password_changed), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this, getString(R.string.msg_verification_failed), Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { Toast.makeText(this, getString(R.string.msg_network_error), Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun resendEmail() {
        val email = userEmail ?: return
        buttonResend.isEnabled = false
        // send same payload as ForgotPasswordActivity
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

                conn.outputStream.use { os -> OutputStreamWriter(os, "UTF-8").use { it.write(payload.toString()) } }

                val codeResp = conn.responseCode
                (if (codeResp in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }

                runOnUiThread {
                    if (codeResp in 200..299) {
                        Toast.makeText(this, getString(R.string.msg_email_resent), Toast.LENGTH_SHORT).show()
                            try {
                                // response has data array with send_result and verification_code_set
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        startResendCountdown()
                    } else {
                        Toast.makeText(this, getString(R.string.msg_resend_failed), Toast.LENGTH_LONG).show()
                        buttonResend.isEnabled = true
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.msg_network_error), Toast.LENGTH_SHORT).show()
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
                buttonResend.text = getString(R.string.resend_with_seconds, seconds)
            }

            override fun onFinish() {
                buttonResend.text = getString(R.string.resend)
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
