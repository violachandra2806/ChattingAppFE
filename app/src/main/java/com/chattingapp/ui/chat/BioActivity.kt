package com.chattingapp.ui.chat

import android.os.Bundle
import android.util.Log
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDobIndo(dobString: String): String {
    if (dobString.isBlank() || dobString == "null") return "-"

    val localeId = Locale("id", "ID")

    val possibleFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
    )

    for (format in possibleFormats) {
        try {
            val date = format.parse(dobString)
            if (date != null) {
                return SimpleDateFormat(
                    "EEEE, dd MMMM yyyy",
                    localeId
                ).format(date).replaceFirstChar { it.uppercase() }
            }
        } catch (_: Exception) {}
    }

    Log.e("DOB_PARSE", "Failed to parse dob: $dobString")
    return "-"
}

class BioActivity : AppCompatActivity() {

    private lateinit var btnClose: ImageView
    private lateinit var tvProfileInitials: TextView
    private lateinit var ivProfilePicture: ImageView
    private lateinit var tvUsername: TextView
    private lateinit var etBio: EditText
    private lateinit var tvDob: TextView
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bio)

        btnClose = findViewById(R.id.btnClose)
        tvProfileInitials = findViewById(R.id.tvProfileInitials)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)
        tvUsername = findViewById(R.id.tvUsername)
        etBio = findViewById(R.id.etBio)
        tvDob = findViewById(R.id.tvDob)

        userId = intent.getStringExtra("user_id")

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
    }

    private fun fetchUserProfile(userId: String) {
        val url = "${BuildConfig.BASE_URL}getuserdetailsbyid?user_id=$userId"

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
                                tvProfileInitials.text = username.take(1).uppercase()
                            }

                            // Set Bio
                            if (bio.isNotEmpty() && bio != "null") {
                                etBio.setText(bio)
                            } else {
                                etBio.setText("")
                                etBio.hint = getString(R.string.label_bio_not_set)
                            }

                            // Set Tgl Lahir
                            if (dob.isNotEmpty() && dob != "null") {
                                Log.d("DOB_DEBUG", "dob raw = $dob")
                                tvDob.text = formatDobIndo(dob)
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
