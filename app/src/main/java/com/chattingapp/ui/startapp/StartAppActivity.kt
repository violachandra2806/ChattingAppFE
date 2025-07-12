package com.chattingapp.ui.startapp

import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.chattingapp.R
import com.chattingapp.databinding.ActivityStartAppBinding

class StartAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set entrance animations
        binding.logo.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
        binding.title.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
        binding.description.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
        binding.loginButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))

        binding.loginButton.setOnClickListener {
            // Handle login button click
        }
    }
}
