package com.chattingapp.ui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.chattingapp.ui.bio.EditBioActivity
import com.chattingapp.ui.editprofile.EditProfileActivity

class SettingsFragment : Fragment() {

    private var tvTitle: TextView? = null
    private var tvProfilePicture: TextView? = null
    private var etUsername: EditText? = null
    private var etEmail: EditText? = null
    private var etDob: EditText? = null
    private var etPassword: EditText? = null

    private var btnEditProfile: LinearLayout? = null
    private var btnEditBio: LinearLayout? = null
    private var btnLogout: LinearLayout? = null

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views (same IDs as fragment_settings)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvProfilePicture = view.findViewById(R.id.profilePicture)
        etUsername = view.findViewById(R.id.etUsername)
        etEmail = view.findViewById(R.id.etEmail)
        etDob = view.findViewById(R.id.etDob)
        etPassword = view.findViewById(R.id.etPassword)

        btnEditProfile = view.findViewById(R.id.layoutEditProfile)
        btnEditBio = view.findViewById(R.id.layoutEditBio)
        btnLogout = view.findViewById(R.id.layoutLogout)

        sharedPreferences = requireContext().getSharedPreferences("UserData", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getString("user_id", null)

        if (userId != null) {
            fetchUserProfile(userId)
        } else {
            Toast.makeText(requireContext(), "User ID tidak ditemukan. Silakan login kembali.", Toast.LENGTH_SHORT).show()
            logout()
        }

        setupListeners()
    }

    private fun setupListeners() {
        btnEditProfile?.setOnClickListener {
            val intent = Intent(requireContext(), EditProfileActivity::class.java)
            startActivity(intent)
        }

        btnEditBio?.setOnClickListener {
            Toast.makeText(requireContext(), "Fitur Edit Bio akan segera hadir", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), EditBioActivity::class.java)
            startActivity(intent)
        }

        btnLogout?.setOnClickListener {
            logout()
        }
    }

    private fun fetchUserProfile(userId: String) {
        val url = "${BuildConfig.BASE_URL}chattingapp/getuserdetailsbyid?user_id=$userId"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
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
                            etUsername?.setText(username)
                            etEmail?.setText(email)

                            if (dob != "null" && dob.isNotEmpty()) {
                                etDob?.setText(dob)
                            } else {
                                etDob?.setText("-")
                            }

                            // Set Initials for Profile Picture (TextView based on XML)
                            if (username.isNotEmpty()) {
                                tvProfilePicture?.text = username.take(2).uppercase()
                            }
                        }
                    } else {
                        Log.e("SettingsFragment", "Failed status: $status")
                        Toast.makeText(requireContext(), "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("SettingsFragment", "Error parsing JSON", e)
                }
            },
            { error ->
                Log.e("SettingsFragment", "Volley Error: ${error.message}")
                Toast.makeText(requireContext(), "Terjadi kesalahan jaringan", Toast.LENGTH_SHORT).show()
            }
        )

        Volley.newRequestQueue(requireContext()).add(request)
    }

    private fun logout() {
        sharedPreferences.edit().clear().apply()
        Toast.makeText(requireContext(), "Berhasil keluar", Toast.LENGTH_SHORT).show()
        // val intent = Intent(requireContext(), LoginActivity::class.java)
        // intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvTitle = null
        tvProfilePicture = null
        etUsername = null
        etEmail = null
        etDob = null
        etPassword = null
        btnEditProfile = null
        btnEditBio = null
        btnLogout = null
    }
}