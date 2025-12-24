package com.chattingapp.ui.startapp

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.chattingapp.R
import com.chattingapp.databinding.ActivityStartAppBinding
import com.chattingapp.ui.dashboard.DashboardActivity
import com.chattingapp.ui.login.LoginActivity
import com.chattingapp.ui.register.RegisterActivity

class StartAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)
        if (!userId.isNullOrBlank()) {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityStartAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Staggered animations
        binding.logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in_slow))
        binding.title.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_fast))
        binding.description.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_medium))
        binding.loginButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_slow))
        binding.registerButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_slow))

        binding.loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}