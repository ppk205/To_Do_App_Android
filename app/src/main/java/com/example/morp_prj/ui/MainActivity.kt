package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.morp_prj.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        bottomNav = findViewById(R.id.bottom_nav_view)
        bottomNav.setupWithNavController(navController)

        // Hide bottom nav on screens that shouldn't show it
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.onboarding_fragment,
                R.id.login_fragment,
                R.id.register_fragment,
                R.id.verify_otp_fragment,
                R.id.otp_resend_required_fragment,
                R.id.register_success_fragment,
                R.id.taskFragment,
                R.id.create_new_team_fragment,
                R.id.teamDetailFragment -> bottomNav.visibility = View.GONE

                else -> bottomNav.visibility = View.VISIBLE
            }
        }
    }
}