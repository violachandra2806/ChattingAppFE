package com.chattingapp.ui.startapp

import android.content.Intent
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chattingapp.R
import com.chattingapp.ui.dashboard.DashboardActivity

class SplashScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        val appNameImageView = findViewById<ImageView>(R.id.appNameImageView)
        val rootLayout = findViewById<RelativeLayout>(R.id.splashRootLayout)

        val circleIn = AnimationUtils.loadAnimation(this, R.anim.rotate_scale_in)
        val shine = AnimationUtils.loadAnimation(this, R.anim.shine_effect)
        val swipeUp = AnimationUtils.loadAnimation(this, R.anim.swipe_up_whole)

        appNameImageView.startAnimation(circleIn)

        circleIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                appNameImageView.startAnimation(shine)
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        shine.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                rootLayout.startAnimation(swipeUp)
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        swipeUp.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
                val userId = prefs.getString("user_id", null)
                val next = if (!userId.isNullOrBlank()) {
                    Intent(this@SplashScreen, DashboardActivity::class.java)
                } else {
                    Intent(this@SplashScreen, StartAppActivity::class.java)
                }
                next.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(next)
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }
}
