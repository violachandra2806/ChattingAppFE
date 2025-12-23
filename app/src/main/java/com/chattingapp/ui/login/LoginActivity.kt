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
import com.chattingapp.ui.dashboard.DashboardActivity
import com.chattingapp.ui.forgotpassword.ForgotPasswordActivity
import com.chattingapp.ui.friendlist.FriendListFragment
import com.chattingapp.ui.friendlist.friendrequest.FriendRequestActivity
import com.chattingapp.ui.register.RegisterActivity
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerText: TextView
    private lateinit var forgotPasswordText: TextView
    private lateinit var togglePasswordVisibility: ImageView
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailInput = findViewById(R.id.editTextEmailUsername)
        passwordInput = findViewById(R.id.editTextPassword)
        loginButton = findViewById(R.id.buttonLogin)
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
                Toast.makeText(this, getString(R.string.msg_login_fields_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginUser(email, password)
        }

        registerText.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
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

    private fun loginUser(email: String, password: String) {
        val url = "${BuildConfig.BASE_URL}loginuser"
        Log.d("Login", "=== LOGIN DEBUG ===")
        Log.d("Login", "URL: $url")
        Log.d("Login", "Email: $email")
        Log.d("Login", "BASE_URL: ${BuildConfig.BASE_URL}")

        val requestBody = JSONObject().apply {
            put("identifier", email)
            put("password", password)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, requestBody,
            { response ->
                try {
                    Log.d("Login", "Response received: $response")
                    when (response.getString("status")) {
                        "success" -> {
                            Log.d("Login", "Login successful, processing user data...")
                            val userData = response.getJSONArray("data").getJSONObject(0)
                            val userId = userData.getString("user_id")
                            val username = userData.getString("username")
                            val email = userData.getString("user_email")

                            Log.d("Login", "User ID: $userId, Username: $username")

                            // FIX: Use SharedPreferencesManager instead of direct SharedPreferences
                            val sharedPreferencesManager = com.chattingapp.utils.SharedPreferencesManager(this)
                            sharedPreferencesManager.setUserId(userId)

                            // Also save to the old location for backward compatibility
                            val sharedPref = getSharedPreferences("UserData", MODE_PRIVATE)
                            val editor = sharedPref.edit()
                            editor.putString("user_id", userId)
                            editor.putString("username", username)
                            editor.putString("email", email)
                            editor.apply()

                            // Verify the data was saved
                            val savedUserId = sharedPreferencesManager.getUserId()
                            Log.d("Login", "Verified saved User ID from SharedPreferencesManager: $savedUserId")

                            Toast.makeText(this, getString(R.string.msg_login_success), Toast.LENGTH_SHORT).show()

                            Log.d("Login", "Starting DashboardActivity...")
                            // Navigate to DashboardActivity (main app)
                            val intent = Intent(this, DashboardActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            finish()
                        }
                        else -> {
                            Log.d("Login", "Login failed: ${response.getString("message")}")
                            Toast.makeText(
                                this,
                                response.getString("message"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Login", "JSON Parsing Error: ${e.message}", e)
                    Toast.makeText(this, getString(R.string.msg_generic_error), Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("Login", "Volley Error: ${error.message}", error)
                val errorMsg = error.networkResponse?.let {
                    String(it.data, Charsets.UTF_8)
                } ?: error.message ?: getString(R.string.msg_unknown_error)
                Toast.makeText(this, getString(R.string.msg_login_failed, errorMsg), Toast.LENGTH_SHORT).show()
            }
        )

        Volley.newRequestQueue(this).add(request)
    }

}