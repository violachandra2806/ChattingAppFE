package com.chattingapp.ui.startapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chattingapp.R

class SplashScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        val appNameTextView = findViewById<TextView>(R.id.appNameTextView)
        val rootLayout = findViewById<RelativeLayout>(R.id.splashRootLayout)

        val circleIn = AnimationUtils.loadAnimation(this, R.anim.rotate_scale_in)
        val shine = AnimationUtils.loadAnimation(this, R.anim.shine_effect)
        val swipeUp = AnimationUtils.loadAnimation(this, R.anim.swipe_up_whole)

        appNameTextView.startAnimation(circleIn)

        circleIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                appNameTextView.startAnimation(shine)
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
                startActivity(Intent(this@SplashScreen, StartAppActivity::class.java))
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }
}
