package com.example.morp_prj.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.morp_prj.MyApplication
import com.example.morp_prj.R
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.morp_prj.security.SecureTokenStorage

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private val sessionExpiredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Navigate to login and clear backstack
            try {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val navController = navHostFragment.navController
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, true)
                    .build()
                navController.navigate(R.id.login_fragment, null, navOptions)
                android.widget.Toast.makeText(this@MainActivity, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error handling session expired broadcast", e)
            }
        }
    }

    @SuppressLint("UnprotectedBroadcastReceiver")
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

        // Register session expired receiver. Use API-guarded overload to avoid NoSuchMethodError on older devices
        val filter = IntentFilter(MyApplication.ACTION_SESSION_EXPIRED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionExpiredReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // Older overload (no flags)
            registerReceiver(sessionExpiredReceiver, filter)
        }

        // Navigation logic:
        // 1. If user has NOT seen onboarding -> stay on onboarding (start destination)
        // 2. If user has seen onboarding AND (logged in OR guest) -> go to home
        // 3. If user has seen onboarding but NOT logged in/guest -> go to login
        try {
            val preferenceManager = PreferenceManager(this)
            val tokenStorage = SecureTokenStorage(this)
            val hasSeenOnboarding = preferenceManager.hasSeenOnboarding()
            val isLoggedInOrGuest = preferenceManager.isLoggedIn() || tokenStorage.hasValidRefreshToken() || preferenceManager.isGuest()

            if (hasSeenOnboarding) {
                if (isLoggedInOrGuest) {
                    // Navigate to home and clear start destination from backstack so user can't navigate back to onboarding/login
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(navController.graph.startDestinationId, true)
                        .build()
                    navController.navigate(R.id.menu_home, null, navOptions)
                } else {
                    // User has seen onboarding but not logged in -> go to login
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(navController.graph.startDestinationId, true)
                        .build()
                    navController.navigate(R.id.login_fragment, null, navOptions)
                }
            }
            // If !hasSeenOnboarding, stay on onboarding (the start destination)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking login state", e)
        }

        val prefs = PreferenceManager(this)

        // intercept bottom navigation selections to block guest users from restricted tabs
        bottomNav.setOnItemSelectedListener { item ->
            val restricted = when (item.itemId) {
                R.id.menu_team, R.id.menu_profile -> true
                else -> false
            }
            if (restricted && prefs.isGuest()) {
                // redirect to guest prompt instead of navigating
                navController.navigate(R.id.guest_prompt_fragment)
                return@setOnItemSelectedListener true
            }
            // fallback to default behavior
            navController.navigate(item.itemId)
            true
        }

        // Hide bottom navigation on destinations that shouldn't show it (e.g. onboarding, login, register)
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
                R.id.forgot_password_fragment,
                R.id.verify_reset_otp_fragment,
                R.id.reset_password_fragment,
                R.id.reset_password_success_fragment,
                R.id.taskFragment  -> bottomNav.visibility = View.GONE
                R.id.create_new_team_fragment,
                R.id.teamDetailFragment -> bottomNav.visibility = View.GONE
                else -> bottomNav.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(sessionExpiredReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }
}