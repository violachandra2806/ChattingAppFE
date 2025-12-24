package com.chattingapp.ui.profile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.chattingapp.ui.bio.EditBioActivity

// import com.chattingapp.ui.login.LoginActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvProfilePicture: TextView
    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etDob: EditText
    private lateinit var etPassword: EditText

    private lateinit var btnEditProfile: LinearLayout
    private lateinit var btnEditBio: LinearLayout
    private lateinit var btnLogout: LinearLayout

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_settings)

        // Initialize Views
        tvTitle = findViewById(R.id.tvTitle)
        tvProfilePicture = findViewById(R.id.profilePicture)
        etUsername = findViewById(R.id.etUsername)
        etEmail = findViewById(R.id.etEmail)
        etDob = findViewById(R.id.etDob)
        etPassword = findViewById(R.id.etPassword)

        btnEditProfile = findViewById(R.id.layoutEditProfile)
        btnEditBio = findViewById(R.id.layoutEditBio)
        btnLogout = findViewById(R.id.layoutLogout)

        sharedPreferences = getSharedPreferences("UserData", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getString("user_id", null)

        if (userId != null) {
            fetchUserProfile(userId)
        } else {
            Toast.makeText(this, "User ID tidak ditemukan. Silakan login kembali.", Toast.LENGTH_SHORT).show()
            logout()
        }

        setupListeners()
    }

    private fun setupListeners() {
        btnEditProfile.setOnClickListener {
            Toast.makeText(this, "Fitur Edit Profile akan segera hadir", Toast.LENGTH_SHORT).show()
            // val intent = Intent(this, EditProfileActivity::class.java)
            // startActivity(intent)
        }

        btnEditBio.setOnClickListener {
            Toast.makeText(this, "Fitur Edit Bio akan segera hadir", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, EditBioActivity::class.java)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun fetchUserProfile(userId: String) {
        val url = "${BuildConfig.BASE_URL}chattingapp/getuserdetailsbyid?user_id=$userId"

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val status = response.optString("status")
                    if (status == "success") {
                        val dataArray = response.optJSONArray("data")
                        if (dataArray != null && dataArray.length() > 0) {
                            val userObj = dataArray.getJSONObject(0)

                            val username = userObj.optString("username", "N/A")
                            val email = userObj.optString("user_email", "N/A")
                            val dob = userObj.optString("dob", "null")

                            // Update UI
                            etUsername.setText(username)
                            etEmail.setText(email)

                            if (dob != "null" && dob.isNotEmpty()) {
                                etDob.setText(dob)
                            } else {
                                etDob.setText("-")
                            }

                            // Set Initials for Profile Picture (TextView based on XML)
                            if (username.isNotEmpty()) {
                                tvProfilePicture.text = username.take(2).uppercase()
                            }
                        }
                    } else {
                        Log.e("ProfileActivity", "Failed status: $status")
                        Toast.makeText(this, "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ProfileActivity", "Error parsing JSON", e)
                }
            },
            { error ->
                Log.e("ProfileActivity", "Volley Error: ${error.message}")
                Toast.makeText(this, "Terjadi kesalahan jaringan", Toast.LENGTH_SHORT).show()
            }
        )

        Volley.newRequestQueue(this).add(request)
    }

    private fun logout() {
        sharedPreferences.edit().clear().apply()
        Toast.makeText(this, "Berhasil keluar", Toast.LENGTH_SHORT).show()
        // val intent = Intent(this, LoginActivity::class.java)
        // intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // startActivity(intent)
        finish()
    }
}
