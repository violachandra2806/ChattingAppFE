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
import com.chattingapp.MainActivity
import com.chattingapp.ui.forgotpassword.ForgotPasswordActivity
import com.chattingapp.ui.friendlist.FriendListActivity
import com.chattingapp.ui.friendrequest.FriendRequestActivity
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

        emailInput = findViewById(R.id.editTextEmailUsername)
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
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan password harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginUser(email, password)
        }

        googleLogin.setOnClickListener { signInWithGoogle() }
        registerText.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        setupGoogleSignIn()
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

    private fun loginUser(email: String, password: String) {
        val url = "${BuildConfig.BASE_URL}loginuser"
        val requestBody = JSONObject().apply {
            put("identifier", email)
            put("password", password)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, requestBody,
            { response ->
                try {
                    when (response.getString("status")) {
                        "success" -> {
                            val userData = response.getJSONArray("data").getJSONObject(0)
                            val userId = userData.getString("user_id")
                            val username = userData.getString("username")
                            val email = userData.getString("user_email")

                            val sharedPref = getSharedPreferences("UserData", MODE_PRIVATE)
                            val editor = sharedPref.edit()
                            editor.putString("user_id", userId)
                            editor.putString("username", username)
                            editor.putString("email", email)
                            editor.apply()
                            Toast.makeText(this, "Login berhasil!", Toast.LENGTH_SHORT).show()

                            startActivity(Intent(this, FriendListActivity::class.java))
                            finish()
                        }
                        else -> Toast.makeText(
                            this,
                            response.getString("message"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("Login", "JSON Parsing Error: ${e.message}")
                    Toast.makeText(this, "Terjadi kesalahan", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                val errorMsg = error.networkResponse?.let {
                    String(it.data, Charsets.UTF_8)
                } ?: error.message ?: "Error tidak diketahui"
                Toast.makeText(this, "Gagal login: $errorMsg", Toast.LENGTH_SHORT).show()
                Log.e("Login", "Volley Error: ${error.message}")
            }
        )

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
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
                Toast.makeText(this, "Login Google berhasil: ${account.email}", Toast.LENGTH_SHORT).show()

            } catch (e: ApiException) {
                Log.e("GoogleSignIn", "Error code: ${e.statusCode}")
                Toast.makeText(this, "Login Google gagal", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val RC_SIGN_IN = 1001
    }
}