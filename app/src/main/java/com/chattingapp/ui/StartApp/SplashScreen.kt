package com.chattingapp.ui.StartApp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start fade-in animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, StartAppActivity::class.java))
            finish()
        }, 1500) // 1.5 seconds delay
    }
}