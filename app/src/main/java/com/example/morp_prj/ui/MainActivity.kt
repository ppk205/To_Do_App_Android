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
import com.example.morp_prj.utils.PreferenceManager
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

        // Intercept tab selections to enforce guest-mode behavior
        bottomNav.setOnItemSelectedListener { item ->
            val isGuest = PreferenceManager.isGuest(this)
            when (item.itemId) {
                R.id.menu_team, R.id.menu_profile -> {
                    if (isGuest) {
                        // Navigate to guest prompt fragment for guests
                        navController.navigate(R.id.guest_prompt_fragment)
                        return@setOnItemSelectedListener true
                    }
                }
            }
            // Default behavior: let NavController handle selection
            navController.navigate(item.itemId)
            true
        }

        // Hide bottom nav on screens that shouldn't show it (e.g., Create Task)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.visibility = when (destination.id) {
                R.id.taskFragment -> View.GONE
                else -> View.VISIBLE
            }
            when (destination.id) {
                R.id.onboarding_fragment,
                R.id.login_fragment,
                R.id.register_fragment,
                R.id.verify_otp_fragment,
                R.id.otp_resend_required_fragment,
                R.id.register_success_fragment,
                R.id.guest_prompt_fragment -> bottomNav.visibility = View.GONE

                else -> bottomNav.visibility = View.VISIBLE
            }
        }
    }
}
