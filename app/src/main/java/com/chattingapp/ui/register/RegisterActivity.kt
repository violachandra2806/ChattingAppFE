package com.chattingapp.ui.register

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.chattingapp.R
import com.chattingapp.BuildConfig
import com.chattingapp.ui.login.LoginActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var editTextEmail: EditText
    private lateinit var editTextUsername: EditText
    private lateinit var editTextDateOfBirth: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var togglePasswordVisibility: ImageView
    private lateinit var toggleConfirmPasswordVisibility: ImageView
    private lateinit var buttonRegister: Button
    private lateinit var alreadyHaveAccount: TextView
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // 🔹 Inisialisasi komponen UI
        editTextEmail = findViewById(R.id.editTextEmail)
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextDateOfBirth = findViewById(R.id.editTextDateOfBirth)
        passwordInput = findViewById(R.id.editTextPassword)
        confirmPasswordInput = findViewById(R.id.confirmTextPassword)
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility)
        toggleConfirmPasswordVisibility = findViewById(R.id.toggleConfirmPasswordVisibility)
        buttonRegister = findViewById(R.id.buttonRegister)
        alreadyHaveAccount = findViewById(R.id.alreadyHaveAccount)

        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                togglePasswordVisibility.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        confirmPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                toggleConfirmPasswordVisibility.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        togglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility()
        }

        toggleConfirmPasswordVisibility.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            toggleConfirmPasswordVisibility()
        }


        // 🔹 Date picker
        editTextDateOfBirth.setOnClickListener { showDatePickerDialog() }

        buttonRegister.setOnClickListener { handleRegister() }

        alreadyHaveAccount.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_on)
        } else {
            passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
        }
        passwordInput.setSelection(passwordInput.text.length)
    }

    private fun toggleConfirmPasswordVisibility() {
        if (isConfirmPasswordVisible) {
            confirmPasswordInput.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            toggleConfirmPasswordVisibility.setImageResource(R.drawable.ic_visibility_on)
        } else {
            confirmPasswordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            toggleConfirmPasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
        }
        confirmPasswordInput.setSelection(confirmPasswordInput.text.length)
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(selectedYear, selectedMonth, selectedDayOfMonth)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                editTextDateOfBirth.setText(dateFormat.format(selectedDate.time))
            },
            year,
            month,
            day
        )
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun handleRegister() {
        val email = editTextEmail.text.toString().trim()
        val username = editTextUsername.text.toString().trim()
        val dateOfBirth = editTextDateOfBirth.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        if (email.isEmpty() || username.isEmpty() || dateOfBirth.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Semua field harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.error = "Format email tidak valid"
            return
        }

        if (password.length < 8) {
            passwordInput.error = "Password minimal 8 karakter"
            return
        }

        if (password != confirmPassword) {
            confirmPasswordInput.error = "Konfirmasi password tidak cocok"
            return
        }

        // 🔹 Kirim ke backend Flask
        registerUserToBackend(email, username, password)
    }

    private fun registerUserToBackend(email: String, username: String, password: String) {
        val url = "${BuildConfig.BASE_URL}registeruser"

        val requestBody = JSONObject().apply {
            put("user_email", email)
            put("username", username)
            put("password", password)
            put("profile_picture", "")
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, requestBody,
            { response ->
                try {
                    val status = response.getString("status")
                    val message = response.getString("message")

                    if (status == "success") {
                        Toast.makeText(this, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finishAffinity()
                    } else {
                        Toast.makeText(this, "Gagal: $message", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    Toast.makeText(this, "Kesalahan parsing data", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            },
            { error ->
                val errorMsg = error.networkResponse?.let {
                    String(it.data, Charsets.UTF_8)
                } ?: error.message ?: "Error tidak diketahui"
                Toast.makeText(this, "Gagal register: $errorMsg", Toast.LENGTH_LONG).show()
            }
        )

        Volley.newRequestQueue(this).add(request)
    }
}
