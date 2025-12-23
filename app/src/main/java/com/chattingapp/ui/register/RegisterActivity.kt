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
    private lateinit var editTextPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
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
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextConfirmPassword = findViewById(R.id.confirmTextPassword)
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility)
        toggleConfirmPasswordVisibility = findViewById(R.id.toggleConfirmPasswordVisibility)
        buttonRegister = findViewById(R.id.buttonRegister)
        alreadyHaveAccount = findViewById(R.id.alreadyHaveAccount)

        // 🔹 Date picker
        editTextDateOfBirth.setOnClickListener { showDatePickerDialog() }

        // 🔹 Password visibility toggle
        setupPasswordToggle(editTextPassword, togglePasswordVisibility) { visible ->
            isPasswordVisible = visible
        }
        setupPasswordToggle(editTextConfirmPassword, toggleConfirmPasswordVisibility) { visible ->
            isConfirmPasswordVisible = visible
        }

        buttonRegister.setOnClickListener { handleRegister() }

        alreadyHaveAccount.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

    }

    private fun setupPasswordToggle(
        passwordField: EditText,
        toggleView: ImageView,
        updateVisibilityState: (Boolean) -> Unit
    ) {
        passwordField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                toggleView.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        toggleView.setOnClickListener {
            val currentlyVisible = passwordField.transformationMethod == null
            if (currentlyVisible) {
                passwordField.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleView.setImageResource(R.drawable.ic_visibility_off)
            } else {
                passwordField.transformationMethod = null
                toggleView.setImageResource(R.drawable.ic_visibility_on)
            }
            passwordField.setSelection(passwordField.text.length)
            updateVisibilityState(!currentlyVisible)
        }

        toggleView.visibility = if (passwordField.text.isNullOrEmpty()) View.GONE else View.VISIBLE
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
        val password = editTextPassword.text.toString()
        val confirmPassword = editTextConfirmPassword.text.toString()

        if (email.isEmpty() || username.isEmpty() || dateOfBirth.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_all_fields_required), Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.error = getString(R.string.msg_email_invalid)
            return
        }

        if (password.length < 8) {
            editTextPassword.error = getString(R.string.msg_password_min_8)
            return
        }

        if (password != confirmPassword) {
            editTextConfirmPassword.error = getString(R.string.msg_password_confirmation_mismatch)
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
                        Toast.makeText(this, getString(R.string.msg_register_success), Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finishAffinity()
                    } else {
                        Toast.makeText(this, getString(R.string.msg_failed_with_reason, message), Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.msg_parsing_error_data), Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            },
            { error ->
                val errorMsg = error.networkResponse?.let {
                    String(it.data, Charsets.UTF_8)
                } ?: error.message ?: getString(R.string.msg_unknown_error)
                Toast.makeText(this, getString(R.string.msg_register_failed, errorMsg), Toast.LENGTH_LONG).show()
            }
        )

        Volley.newRequestQueue(this).add(request)
    }
}
