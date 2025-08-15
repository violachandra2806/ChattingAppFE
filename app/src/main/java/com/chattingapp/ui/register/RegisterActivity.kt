package com.chattingapp.ui.register

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.text
import com.chattingapp.R
import com.chattingapp.ui.login.LoginActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    private lateinit var googleLogin: LinearLayout

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        editTextEmail = findViewById(R.id.editTextEmail)
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextDateOfBirth = findViewById(R.id.editTextDateOfBirth)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextConfirmPassword = findViewById(R.id.confirmTextPassword)
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility)
        toggleConfirmPasswordVisibility = findViewById(R.id.toggleConfirmPasswordVisibility)
        buttonRegister = findViewById(R.id.buttonRegister)
        alreadyHaveAccount = findViewById(R.id.alreadyHaveAccount)
        googleLogin = findViewById(R.id.buttonGoogle)

        editTextDateOfBirth.setOnClickListener {
            showDatePickerDialog()
        }

        setupPasswordToggle(editTextPassword, togglePasswordVisibility) { visible ->
            isPasswordVisible = visible
        }
        setupPasswordToggle(editTextConfirmPassword, toggleConfirmPasswordVisibility) { visible ->
            isConfirmPasswordVisible = visible
        }

        buttonRegister.setOnClickListener {
            handleRegister()
        }

        alreadyHaveAccount.setOnClickListener {
             startActivity(Intent(this, LoginActivity::class.java))
             finish()
         }

        googleLogin.setOnClickListener {
            Toast.makeText(this, "Google login belum diimplementasi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPasswordToggle(passwordField: EditText, toggleView: ImageView, updateVisibilityState: (Boolean) -> Unit) {
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
                passwordField.transformationMethod = null // Show password
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
            Toast.makeText(this, "Semua field harus diisi", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.error = "Format email tidak valid"
            Toast.makeText(this, "Format email tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) { // Contoh validasi panjang password
            editTextPassword.error = "Password minimal 6 karakter"
            Toast.makeText(this, "Password minimal 6 karakter", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            editTextConfirmPassword.error = "Konfirmasi password tidak cocok"
            Toast.makeText(this, "Konfirmasi password tidak cocok", Toast.LENGTH_SHORT).show()
            return
        }

        // TODO: Implementasikan logika registrasi sebenarnya di sini
        // (Contoh: kirim data ke server, simpan ke database lokal, dll.)
        Toast.makeText(this, "Registrasi berhasil (Simulasi)", Toast.LENGTH_LONG).show()

         val intent = Intent(this, LoginActivity::class.java)
         startActivity(intent)
         finishAffinity()
    }
}
