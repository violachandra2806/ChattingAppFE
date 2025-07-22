package com.chattingapp.ui.startapp

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.chattingapp.R
import com.chattingapp.databinding.ActivityStartAppBinding
import com.chattingapp.ui.login.LoginActivity

class StartAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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