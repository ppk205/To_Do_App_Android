package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.repository.EncryptionRepository
import com.example.morp_prj.security.CryptoManager
import com.example.morp_prj.security.KeyManager
import com.example.morp_prj.security.PasswordManager
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class TeamDetailFragment : Fragment(R.layout.fragment_team_detail), NavigationView.OnNavigationItemSelectedListener {

    private var teamId: String = ""
    private var teamName: String = ""
    private var userRole: String = "member"
    private var inviteCode: String = ""

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var teamNavController: NavController

    private val TAG = "TeamDetailFragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            teamId = it.getString("teamId", "")
            teamName = it.getString("teamName", "Team Detail")
            userRole = it.getString("role", "member")
            inviteCode = it.getString("inviteCode", "")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        drawerLayout = view.findViewById(R.id.drawer_layout)
        navView = view.findViewById(R.id.nav_view)

        // 🔐 Initialize encryption key for team
        initializeTeamEncryption()

        // Setup Nested Navigation Controller
        val navHostFragment = childFragmentManager.findFragmentById(R.id.team_nav_host_fragment) as NavHostFragment
        teamNavController = navHostFragment.navController

        val navGraph = teamNavController.navInflater.inflate(R.navigation.team_nav_graph)

        val startDestinationArgs = bundleOf(
            "teamId" to teamId,
            "teamName" to teamName,
            "role" to userRole,
            "inviteCode" to inviteCode
        )

        teamNavController.setGraph(navGraph, startDestinationArgs)

        navView.setNavigationItemSelectedListener(this)

        setupDrawerMenu()
    }

    /**
     * Initialize team encryption key
     * Tries to get from server first, falls back to deterministic generation
     */
    private fun initializeTeamEncryption() {
        lifecycleScope.launch {
            try {
                val keyManager = KeyManager(requireContext())
                val encryptionRepo = EncryptionRepository(requireContext())
                val passwordManager = PasswordManager(requireContext())
                val preferenceManager = PreferenceManager(requireContext())

                // Check if team key already exists
                if (!keyManager.hasTeamKey(teamId)) {
                    Log.d(TAG, "🔐 No encryption key found for team $teamId, fetching...")

                    val userId = preferenceManager.getUserId() ?: ""
                    val password = passwordManager.getPassword(userId)

                    // Try to get from server with password
                    val teamKey = encryptionRepo.getOrCreateTeamKey(teamId, userId, password)

                    // Key is already cached by repository
                    Log.d(TAG, "✅ Team encryption key ready for team $teamId")
                    Log.d(TAG, "🔑 Key preview: ${teamKey.take(20)}...")
                } else {
                    val existingKey = keyManager.getCachedTeamKey(teamId)
                    Log.d(TAG, "✅ Team encryption key already exists for team $teamId")
                    Log.d(TAG, "🔑 Key preview: ${existingKey?.take(20)}...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize team encryption", e)
                Toast.makeText(context, "Failed to setup encryption", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDrawerMenu() {
        navView.menu.clear()
        while (navView.headerCount > 0) {
            navView.removeHeaderView(navView.getHeaderView(0))
        }

        val headerView : View
        if (userRole.equals("manager", ignoreCase = true) || userRole.equals("co-manager", ignoreCase = true)) {
            navView.inflateMenu(R.menu.manager_drawer_menu)
            headerView = navView.inflateHeaderView(R.layout.nav_header_manager)
        } else {
            navView.inflateMenu(R.menu.member_drawer_menu)
            headerView = navView.inflateHeaderView(R.layout.nav_header_member)
        }

        val tvTeamName = headerView.findViewById<TextView>(R.id.tvTeamName)
        val tvRole = headerView.findViewById<TextView>(R.id.tvMemberRole)

        if (tvTeamName != null) {
            tvTeamName.text = teamName
        }
        if (tvRole != null) {
            tvRole.text = userRole.uppercase()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val args = bundleOf(
            "teamId" to teamId,
            "teamName" to teamName,
            "role" to userRole,
            "inviteCode" to inviteCode
        )

        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()

        when (item.itemId) {
            // --- SECTION 1: MANAGER CONTROLS ---
            R.id.nav_team_dashboard -> {
                tryNavigate(R.id.teamDashboardFragment, args, navOptions)
            }
            R.id.nav_member_management -> {
                tryNavigate(R.id.memberManagementFragment, args, navOptions)
            }
            R.id.nav_team_settings -> {
                tryNavigate(R.id.teamSettingsFragment, args, navOptions)
            }
            R.id.nav_tasks_management -> {
                tryNavigate(R.id.teamTaskManagementFragment, args, navOptions)
            }

            // --- SECTION 2: MEMBER WORKSPACE ---
            R.id.nav_assigned_tasks -> {
                tryNavigate(R.id.assignedTasksFragment, args, navOptions)
            }
            R.id.nav_team_chat -> {
                tryNavigate(R.id.teamChatFragment, args, navOptions)
            }
            R.id.nav_team_info -> {
                tryNavigate(R.id.teamInfoFragment, args, navOptions)
            }
            R.id.nav_member_directory -> {
                tryNavigate(R.id.memberDirectoryFragment, args, navOptions)
            }


            // --- SECTION 3: SYSTEM ---
            R.id.nav_leave_team -> {
                findNavController().navigateUp()
            }

            else -> {
                Toast.makeText(context, "Feature under development", Toast.LENGTH_SHORT).show()
            }
        }

        drawerLayout.closeDrawer(GravityCompat.END)
        return true
    }

    private fun tryNavigate(resId: Int, args: Bundle, navOptions: NavOptions) {
        try {
            teamNavController.navigate(resId, args, navOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Navigation failed: ${e.message}")
            Toast.makeText(context, "Error: Destination not found inside team_nav_graph", Toast.LENGTH_SHORT).show()
        }
    }
}