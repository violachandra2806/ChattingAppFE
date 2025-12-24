package com.chattingapp.ui.bio

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

class EditBioActivity : AppCompatActivity() {

    private lateinit var btnClose: ImageView
    private lateinit var tvProfileInitials: TextView
    private lateinit var ivProfilePicture: ImageView
    private lateinit var tvUsername: TextView
    private lateinit var etBio: EditText
    private lateinit var tvDob: TextView
    private lateinit var btnEdit: MaterialButton

    private var isEditing = false
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_bio)

        btnClose = findViewById(R.id.btnClose)
        tvProfileInitials = findViewById(R.id.tvProfileInitials)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)
        tvUsername = findViewById(R.id.tvUsername)
        etBio = findViewById(R.id.etBio)
        tvDob = findViewById(R.id.tvDob)
        btnEdit = findViewById(R.id.btnEdit)

        val sharedPreferences = getSharedPreferences("UserData", Context.MODE_PRIVATE)
        userId = sharedPreferences.getString("user_id", null)

        if (userId != null) {
            fetchUserProfile(userId!!)
        } else {
            Toast.makeText(this, "User tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupListeners()
    }

    private fun setupListeners() {
        btnClose.setOnClickListener {
            finish()
        }

        btnEdit.setOnClickListener {
            if (!isEditing) {
                startEditing()
            } else {
                saveBio()
            }
        }
    }

    private fun startEditing() {
        isEditing = true
        btnEdit.text = "Save"

        etBio.isEnabled = true
        etBio.requestFocus()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etBio, InputMethodManager.SHOW_IMPLICIT)
        // etBio.setBackgroundResource(android.R.drawable.edit_text)
    }

    private fun stopEditing() {
        isEditing = false
        btnEdit.text = "Edit"

        etBio.isEnabled = false
        etBio.clearFocus()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etBio.windowToken, 0)

        // etBio.background = null // Restore transparent background
    }

    private fun saveBio() {
        val newBio = etBio.text.toString().trim()

        btnEdit.isEnabled = false
        btnEdit.text = "Saving..."

        val url = "${BuildConfig.BASE_URL}chattingapp/updateuserdetails?user_id=$userId"

        val jsonBody = JSONObject()
        jsonBody.put("bio", newBio)

        val request = JsonObjectRequest(Request.Method.PUT, url, jsonBody,
            { response ->
                btnEdit.isEnabled = true

                val status = response.optString("status")
                if (status == "success" || response.has("message")) {
                    Toast.makeText(this, "Bio updated!", Toast.LENGTH_SHORT).show()
                    stopEditing()
                } else {
                    Toast.makeText(this, "Failed to update bio", Toast.LENGTH_SHORT).show()
                    btnEdit.text = "Save" // Revert button text
                }
            },
            { error ->
                btnEdit.isEnabled = true
                btnEdit.text = "Save" // Revert button text
                Log.e("EditBio", "Update error: ${error.message}")
                Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show()
            }
        )

        Volley.newRequestQueue(this).add(request)
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

                            val username = userObj.optString("username", "User")
                            val bio = userObj.optString("bio", "")
                            val dob = userObj.optString("dob", "")

                            // Set UI
                            tvUsername.text = username

                            // Set PFP Initials
                            if (username.isNotEmpty()) {
                                tvProfileInitials.text = username.take(2).uppercase()
                            }

                            // Set Bio
                            if (bio.isNotEmpty() && bio != "null") {
                                etBio.setText(bio)
                            } else {
                                etBio.setText("")
                                etBio.hint = "No bio yet (Tap Edit to add)"
                            }

                            // Set Tgl Lahir
                            if (dob.isNotEmpty() && dob != "null") {
                                tvDob.text = dob
                            } else {
                                tvDob.text = "-"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EditBioActivity", "Parsing error", e)
                }
            },
            { error ->
                Log.e("EditBioActivity", "Network error: ${error.message}")
            }
        )
        Volley.newRequestQueue(this).add(request)
    }
}
