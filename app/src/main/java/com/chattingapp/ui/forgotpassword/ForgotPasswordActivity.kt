package com.chattingapp.ui.forgotpassword

import android.content.Intent
import android.os.Bundle
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

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var backIcon: ImageView
    private lateinit var pageTitle: TextView
    private lateinit var editTextEmail: EditText
    private lateinit var buttonSendInstructions: Button
    private lateinit var textViewInstructions: TextView
    private lateinit var googleLogin: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        backIcon = findViewById(R.id.backIcon)
        editTextEmail = findViewById(R.id.editTextEmail)
        buttonSendInstructions = findViewById(R.id.buttonVerification)
        googleLogin = findViewById(R.id.buttonGoogle)

        backIcon.setOnClickListener {
            finish()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            onBackPressedDispatcher.onBackPressed()
        }

        buttonSendInstructions.setOnClickListener {
            handleSendInstructions()
        }

        googleLogin.setOnClickListener {
            Toast.makeText(this, "Google login belum diimplementasi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSendInstructions() {
        val email = editTextEmail.text.toString().trim()

        if (email.isEmpty()) {
            editTextEmail.error = "Email tidak boleh kosong"
            Toast.makeText(this, "Masukkan email Anda", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.error = "Format email tidak valid"
            Toast.makeText(this, "Format email tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        // TODO: Implementasikan logika pengiriman instruksi reset kata sandi di sini
        // (Contoh: Panggil API ke server Anda untuk mengirim email reset)
        // Ini hanya simulasi
        Toast.makeText(this, "Instruksi telah dikirim ke $email (Simulasi)", Toast.LENGTH_LONG).show()
         editTextEmail.text.clear()
         val intent = Intent(this, LoginActivity::class.java)
         intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
         startActivity(intent)
         finish()
    }
}
