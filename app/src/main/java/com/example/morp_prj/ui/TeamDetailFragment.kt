package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.example.morp_prj.R
import com.google.android.material.navigation.NavigationView

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

        val navHostFragment = childFragmentManager.findFragmentById(R.id.team_nav_host_fragment) as NavHostFragment
        teamNavController = navHostFragment.navController

        val startDestinationArgs = bundleOf(
            "teamId" to teamId,
            "teamName" to teamName,
            "role" to userRole,
            "inviteCode" to inviteCode
        )
        try {
            // set graph with the provided start arguments (safe-guarded)
            val navGraph = teamNavController.navInflater.inflate(R.navigation.team_nav_graph)
            teamNavController.setGraph(navGraph, startDestinationArgs)
            // Ensure the start destination's back stack entry carries our args so the first-created
            // dashboard fragment receives them (prevents an empty initial instance).
            try {
                val entry = teamNavController.currentBackStackEntry
                entry?.arguments?.putAll(startDestinationArgs)
            } catch (attachEx: Throwable) {
                Log.w(TAG, "Failed to attach start args to start destination: ${attachEx.message}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to set nav graph: ${t.message}")
            // fallback: navigate to the dashboard destination with args so the UI opens
            try {
                if (teamNavController.currentDestination == null || teamNavController.currentDestination?.id != R.id.teamDashboardFragment) {
                    teamNavController.navigate(R.id.teamDashboardFragment, startDestinationArgs)
                }
            } catch (navEx: Throwable) {
                Log.e(TAG, "Fallback navigation failed: ${navEx.message}")
            }
        }

        teamNavController.addOnDestinationChangedListener { _, destination, _ ->
            view.post { setupToolbarForDestination(destination.id) }
        }

        navView.menu.clear()

        if (navView.headerCount > 0) {
            navView.removeHeaderView(navView.getHeaderView(0))
        }

        if (userRole.equals("manager", ignoreCase = true)) {
            navView.inflateMenu(R.menu.manager_drawer_menu)
            navView.inflateHeaderView(R.layout.nav_header_manager)
        } else if (userRole.equals("member", ignoreCase = true)) {
            navView.inflateMenu(R.menu.member_drawer_menu)
            navView.inflateHeaderView(R.layout.nav_header_member)
        } else {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }
        navView.setNavigationItemSelectedListener(this)
    }

    private fun setupToolbarForDestination(destinationId: Int) {
        val currentFragmentView = childFragmentManager.fragments.firstOrNull()?.view ?: return

        currentFragmentView.findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            if (!teamNavController.popBackStack()) {
                findNavController().popBackStack()
            }
        }

        val btnMenu = currentFragmentView.findViewById<ImageView>(R.id.btnMenu)
        if (userRole.equals("manager", ignoreCase = true)
            or (userRole.equals("member", ignoreCase = true))) {

            btnMenu?.visibility = View.VISIBLE
            btnMenu?.setOnClickListener { drawerLayout.openDrawer(GravityCompat.END) }
        } else {
            btnMenu?.visibility = View.GONE
        }

        val toolbarTitle = currentFragmentView.findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle?.text = when(destinationId) {
            R.id.memberManagementFragment -> "Member Management"
            R.id.joinRequestFragment -> "Join Requests"
            R.id.createTeamTaskFragment -> "Create New Task"
            R.id.assignedTasksFragment -> "Assigned Tasks"
            R.id.teamTaskManagementFragment -> "Team Task Management"
            R.id.teamDashboardFragment -> "Team Manager Dashboard"
            else -> "Team Dashboard"
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val args = bundleOf(
            "teamId" to teamId,
            "teamName" to teamName,
            "role" to userRole,
            "inviteCode" to inviteCode
        )

        val navOptionsBuilder = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(androidx.navigation.ui.R.anim.nav_default_enter_anim)
            .setExitAnim(androidx.navigation.ui.R.anim.nav_default_exit_anim)
            .setPopEnterAnim(androidx.navigation.ui.R.anim.nav_default_pop_enter_anim)
            .setPopExitAnim(androidx.navigation.ui.R.anim.nav_default_pop_exit_anim)

        when (item.itemId) {
            // For Manager
            R.id.nav_member_management -> teamNavController.navigate(R.id.memberManagementFragment, args, navOptionsBuilder.build())
            R.id.nav_join_requests -> teamNavController.navigate(R.id.joinRequestFragment, args, navOptionsBuilder.build())

            // SPECIAL HANDLING FOR DASHBOARD
            R.id.nav_manager_dashboard -> {
                // Pop everything up to the dashboard to avoid duplicates
                val options = navOptionsBuilder
                    .setPopUpTo(teamNavController.graph.startDestinationId, true)
                    .build()
                teamNavController.navigate(R.id.teamDashboardFragment, args, options)
            }

            R.id.nav_create_task -> teamNavController.navigate(R.id.createTeamTaskFragment, args, navOptionsBuilder.build())
            R.id.nav_team_task_management -> teamNavController.navigate(R.id.teamTaskManagementFragment, args, navOptionsBuilder.build())

            // For Member + Manager
            R.id.nav_assigned_tasks -> teamNavController.navigate(R.id.assignedTasksFragment, args, navOptionsBuilder.build())

            else -> Toast.makeText(context, "Feature coming soon!", Toast.LENGTH_SHORT).show()
        }


        drawerLayout.closeDrawer(GravityCompat.END)
        return true
    }
}