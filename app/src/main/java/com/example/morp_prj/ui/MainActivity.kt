package com.example.morp_prj.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.example.morp_prj.data.remote.SocketManager
import com.example.morp_prj.data.repository.NotificationRealtimeRepository

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var notificationRealtimeRepository: NotificationRealtimeRepository? = null
    private val sessionExpiredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // When session expires, navigate to onboarding and clear backstack
            try {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val navController = navHostFragment.navController
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, true)
                    .build()
                // Navigate to onboarding when session expires (NOT login)
                navController.navigate(R.id.onboarding_fragment, null, navOptions)

                SecureTokenStorage(context!!).clearTokens()
                disconnectSocket()

                android.widget.Toast.makeText(this@MainActivity, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error handling session expired broadcast", e)
            }
        }
    }

    @SuppressLint("UnprotectedBroadcastReceiver")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        setTheme(R.style.Theme_MORPPRJ)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        bottomNav = findViewById(R.id.bottom_nav_view)
        bottomNav.setupWithNavController(navController)

        // Register session expired receiver. Use API-guarded overload to avoid NoSuchMethodError on older devices
        val filter = IntentFilter(MyApplication.ACTION_SESSION_EXPIRED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionExpiredReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            // For older devices, use ContextCompat which handles the flag correctly
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                sessionExpiredReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        connectSocket()

        // Navigation logic:
        // 1. If user is guest -> clear guest mode and stay on onboarding (guest must see onboarding each time)
        // 2. If user is actually logged in -> go to home
        // 3. Otherwise (not logged in, not guest) -> stay on onboarding
        try {
            val preferenceManager = PreferenceManager(this)
            val tokenStorage = SecureTokenStorage(this)
            val hasSeenOnboarding = preferenceManager.hasSeenOnboarding()
            val isActuallyLoggedIn = preferenceManager.isLoggedIn() || tokenStorage.hasValidRefreshToken()
            val isGuest = preferenceManager.isGuest()

            android.util.Log.d("MainActivity", "========== NAVIGATION DEBUG ==========")
            android.util.Log.d("MainActivity", "hasSeenOnboarding=$hasSeenOnboarding")
            android.util.Log.d("MainActivity", "isLoggedIn=${preferenceManager.isLoggedIn()}")
            android.util.Log.d("MainActivity", "hasValidRefreshToken=${tokenStorage.hasValidRefreshToken()}")
            android.util.Log.d("MainActivity", "isGuest=$isGuest")
            android.util.Log.d("MainActivity", "isActuallyLoggedIn=$isActuallyLoggedIn")
            android.util.Log.d("MainActivity", "=======================================")

            when {
                isGuest -> {
                    // If user was in guest mode, clear it so they have to go through onboarding again
                    android.util.Log.d("MainActivity", "Clearing guest mode - guest must see onboarding on each app launch")
                    preferenceManager.clearLoginData()
                    // Stay on onboarding (start destination) - no navigation needed
                    android.util.Log.d("MainActivity", "Staying on ONBOARDING (guest mode cleared)")
                }
                isActuallyLoggedIn -> {
                    // User is actually logged in -> go to home
                    android.util.Log.d("MainActivity", "Navigating to HOME (user logged in)")
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(navController.graph.startDestinationId, true)
                        .build()
                    navController.navigate(R.id.menu_home, null, navOptions)
                }
                else -> {
                    // Not logged in, not guest -> stay on onboarding
                    // This includes: fresh install, after logout, or during login/register process
                    android.util.Log.d("MainActivity", "Staying on ONBOARDING (not logged in)")
                }
            }
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
                R.id.taskFragment,
                R.id.create_new_team_fragment,
                R.id.teamChatFragment,
                R.id.teamDetailFragment,
                R.id.change_password_fragment -> bottomNav.visibility = View.GONE
                else -> bottomNav.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(sessionExpiredReceiver)
        } catch (_: Exception) {
            // ignore
        }
        notificationRealtimeRepository = null
    }

    fun connectSocket() {
        val secureStorage = SecureTokenStorage(this)
        val token = secureStorage.getAccessToken()
        if (!token.isNullOrEmpty()) {
            SocketManager.connect(token)

            // Start realtime notifications (persist to Room + show device notification)
            notificationRealtimeRepository = NotificationRealtimeRepository(this).also {
                it.start(showDeviceNotifications = true)
            }
        }
    }

    fun disconnectSocket() {
        SocketManager.disconnect()
        Log.d("SocketManager","Close socket")
    }
}