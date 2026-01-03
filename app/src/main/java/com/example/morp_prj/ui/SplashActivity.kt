package com.example.morp_prj.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.morp_prj.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Taskly_Splash)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splash_logo)
        val appName = findViewById<TextView>(R.id.splash_app_name)
        val slogan = findViewById<TextView>(R.id.splash_slogan)

        val logoAnim = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
        }
        val nameAnim = ObjectAnimator.ofFloat(appName, "translationY", 20f, 0f).apply {
            duration = 400
            startDelay = 300
            interpolator = AccelerateDecelerateInterpolator()
        }
        val nameAlpha = ObjectAnimator.ofFloat(appName, "alpha", 0f, 1f).apply {
            duration = 400
            startDelay = 300
            interpolator = AccelerateDecelerateInterpolator()
        }
        val sloganAnim = ObjectAnimator.ofFloat(slogan, "translationY", 20f, 0f).apply {
            duration = 400
            startDelay = 450
            interpolator = AccelerateDecelerateInterpolator()
        }
        val sloganAlpha = ObjectAnimator.ofFloat(slogan, "alpha", 0f, 1f).apply {
            duration = 400
            startDelay = 450
            interpolator = AccelerateDecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(logoAnim, nameAnim, nameAlpha, sloganAnim, sloganAlpha)
            start()
        }

        window.decorView.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1200)
    }
}
