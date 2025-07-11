package com.chattingapp.ui.Login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.chattingapp.R

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var googleLogin: LinearLayout
    private lateinit var registerText: TextView
    private lateinit var forgotPasswordText: TextView
    private lateinit var togglePasswordVisibility: ImageView
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.editTextEmail)
        passwordInput = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.buttonLogin)
        googleLogin = findViewById(R.id.buttonGoogle)
        registerText = findViewById(R.id.textRegister)
        forgotPasswordText = findViewById(R.id.forgotPassword)
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility)

        // Password visibility toggle setup
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                togglePasswordVisibility.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        togglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility()
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                Toast.makeText(this, "Login dengan $email", Toast.LENGTH_SHORT).show()
                // TODO: Handle login logic
            } else {
                Toast.makeText(this, "Isi email dan password", Toast.LENGTH_SHORT).show()
            }
        }

        googleLogin.setOnClickListener {
            Toast.makeText(this, "Google login belum diimplementasi", Toast.LENGTH_SHORT).show()
        }

        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        forgotPasswordText.setOnClickListener {
            Toast.makeText(this, "Navigasi ke Lupa Kata Sandi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_on)
        } else {
            passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
        }
        // Move cursor to the end of the text
        passwordInput.setSelection(passwordInput.text.length)
    }
}