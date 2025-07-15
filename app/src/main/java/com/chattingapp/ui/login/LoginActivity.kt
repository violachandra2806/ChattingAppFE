package com.chattingapp.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.chattingapp.ui.register.RegisterActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var googleLogin: LinearLayout
    private lateinit var registerText: TextView
    private lateinit var forgotPasswordText: TextView
    private lateinit var togglePasswordVisibility: ImageView
    private var isPasswordVisible = false

    private lateinit var googleSignInClient: GoogleSignInClient

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
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Isi email dan password", Toast.LENGTH_SHORT).show()
            }
        }

        googleLogin.setOnClickListener {
            signInWithGoogle()
        }

        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        forgotPasswordText.setOnClickListener {
            Toast.makeText(this, "Navigasi ke Lupa Kata Sandi", Toast.LENGTH_SHORT).show()
        }

        setupGoogleSignIn()
    }

    private fun togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_on)
        } else {
            passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
        }
        passwordInput.setSelection(passwordInput.text.length)
    }

    private fun loginUser(email: String, password: String) {
        val url = BuildConfig.BASE_URL + "loginuser" + "?identifier=" + email + "&password=" + password

        val requestBody = JSONObject()
        requestBody.put("identifier", email)
        requestBody.put("password", password)

        val request = JsonObjectRequest(Request.Method.POST, url, requestBody,
            { response ->
                Toast.makeText(this, "Login sukses!", Toast.LENGTH_SHORT).show()
                Log.d("Login", "Response: $response")
            },
            { error ->
                val errorMessage = error.message ?: "Terjadi kesalahan saat login"
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                Log.e("Login", "Error: $error")
            })

        Volley.newRequestQueue(this).add(request)
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                Toast.makeText(this, "Login Google: ${account.email}", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                Log.w("GoogleSignIn", "signInResult:failed code=" + e.statusCode)
                Toast.makeText(this, "Login Google gagal", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val RC_SIGN_IN = 1001
    }
}