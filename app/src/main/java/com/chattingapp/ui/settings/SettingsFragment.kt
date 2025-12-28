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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.chattingapp.BuildConfig
import com.chattingapp.R
import com.chattingapp.ui.bio.EditBioActivity
import com.chattingapp.ui.editprofile.EditProfileActivity
import com.chattingapp.ui.login.LoginActivity
import com.chattingapp.utils.AvatarUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SettingsFragment : Fragment() {

    private fun formatDobDateOnly(dobString: String?): String {
        val raw = dobString?.trim().orEmpty()
        if (raw.isBlank()) return "-"
        if (raw.equals("null", true)) return "-"

        // Already in desired format
        if (Regex("\\d{2}/\\d{2}/\\d{4}").matches(raw)) return raw

        val utc = TimeZone.getTimeZone("UTC")
        val possibleFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        ).onEach { it.timeZone = utc }

        for (format in possibleFormats) {
            try {
                val date = format.parse(raw)
                if (date != null) {
                    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                        timeZone = utc
                    }.format(date)
                }
            } catch (_: Exception) {
            }
        }

        return "-"
    }

    private var tvTitle: TextView? = null
    private var tvProfilePicture: TextView? = null
    private var etUsername: EditText? = null
    private var etEmail: EditText? = null
    private var etDob: EditText? = null
    private var etPassword: EditText? = null

    private var btnEditProfile: LinearLayout? = null
    private var btnEditBio: LinearLayout? = null
    private var btnLogout: LinearLayout? = null

    private var loadingOverlay: View? = null

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

        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        sharedPreferences = requireContext().getSharedPreferences("UserData", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getString("user_id", null)

        if (userId != null) {
            fetchUserProfile(userId)
        } else {
            Toast.makeText(requireContext(), "User ID tidak ditemukan. Silakan login kembali.", Toast.LENGTH_SHORT).show()
            setLoading(false)
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
            val intent = Intent(requireContext(), EditBioActivity::class.java)
            startActivity(intent)
        }

        btnLogout?.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showLogoutDialog() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi")
            .setMessage("Apakah Anda yakin ingin keluar dari akun ini?")
            .setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Keluar") { _, _ -> doLogout() }
            .show()
    }

    private fun fetchUserProfile(userId: String) {
        setLoading(true)
        val base = if (BuildConfig.BASE_URL.endsWith("/")) BuildConfig.BASE_URL else BuildConfig.BASE_URL + "/"
        val url = "${base}getuserdetailsbyid?user_id=$userId"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val status = response.optString("status")
                    if (status == "success") {
                        val userObj = if (response.has("data") && response.optJSONArray("data") != null && response.optJSONArray("data")!!.length() > 0) {
                            response.optJSONArray("data")!!.getJSONObject(0)
                        } else {
                            response
                        }

                        val username = userObj.optString("username", "").trim()
                        val email = userObj.optString("user_email", "").trim()

                        fun looksLikeUrl(s: String?): Boolean {
                            val v = s?.trim().orEmpty()
                            return v.startsWith("http://", ignoreCase = true) || v.startsWith("https://", ignoreCase = true)
                        }

                        fun looksLikeDateLikeString(s: String?): Boolean {
                            val v = s?.trim().orEmpty()
                            if (v.isBlank()) return false
                            if (v.equals("null", true)) return false
                            if (v.equals("true", true) || v.equals("false", true)) return false
                            if (looksLikeUrl(v)) return false
                            return v.contains("GMT", ignoreCase = true) || v.contains(",") || v.contains("-")
                        }

                        val rawDob = userObj.optString("dob", "").trim()
                        val dob = if (looksLikeDateLikeString(rawDob)) {
                            rawDob
                        } else {
                            val rawBio = userObj.optString("bio", "").trim()
                            if (looksLikeDateLikeString(rawBio)) rawBio else rawDob
                        }

                        val profilePictureCandidate = userObj.optString("profile_picture", "").trim()
                        val blockedUserCandidate = userObj.optString("blocked_user", "").trim()
                        val profilePictureUrl = when {
                            looksLikeUrl(profilePictureCandidate) -> profilePictureCandidate
                            looksLikeUrl(blockedUserCandidate) -> blockedUserCandidate
                            else -> null
                        }

                        // Update UI
                        if (!isAdded) return@JsonObjectRequest
                        etUsername?.setText(username)
                        etEmail?.setText(email)
                        etDob?.setText(formatDobDateOnly(dob))

                        // Set Initials for Profile Picture (TextView based on XML)
                        tvProfilePicture?.let { AvatarUtils.applyTo(it, username) }

                        // (Optional) If your layout later uses an ImageView for profile picture, you can load profilePictureUrl there.
                        Log.d("SettingsFragment", "Profile loaded. user_id=$userId username=$username email=$email dob=$dob profilePic=$profilePictureUrl")
                        setLoading(false)
                    } else {
                        Log.e("SettingsFragment", "Failed status: $status")
                        Toast.makeText(requireContext(), "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                        setLoading(false)
                    }
                } catch (e: Exception) {
                    Log.e("SettingsFragment", "Error parsing JSON", e)
                    setLoading(false)
                }
            },
            { error ->
                Log.e("SettingsFragment", "Volley Error: ${error.message}")
                Toast.makeText(requireContext(), "Terjadi kesalahan jaringan", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        )

        Volley.newRequestQueue(requireContext()).add(request)
    }

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay?.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun doLogout() {
        sharedPreferences.edit().clear().apply()
        Toast.makeText(requireContext(), "Berhasil keluar", Toast.LENGTH_SHORT).show()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun logout() {
        // Backward-compatible helper for existing call sites.
        // Used when we must force the user back to Login.
        doLogout()
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
        loadingOverlay = null
    }
}