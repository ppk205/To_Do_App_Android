package com.example.morp_prj.ui

import android.os.Bundle
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
        teamNavController.setGraph(R.navigation.team_nav_graph, startDestinationArgs)

        teamNavController.addOnDestinationChangedListener { _, destination, _ ->
            view.post { setupToolbarForDestination(destination.id) }
        }

        navView.menu.clear()
        if (userRole.equals("manager", ignoreCase = true)) {
            navView.inflateMenu(R.menu.manager_drawer_menu)
        } else if (userRole.equals("member", ignoreCase = true)) {
            navView.inflateMenu(R.menu.member_drawer_menu)
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

        when (item.itemId) {
            // For Manager
            R.id.nav_member_management -> teamNavController.navigate(R.id.memberManagementFragment, args)
            R.id.nav_join_requests -> teamNavController.navigate(R.id.joinRequestFragment, args)
            R.id.nav_manager_dashboard -> teamNavController.navigate(R.id.teamDashboardFragment, args)
            R.id.nav_create_task -> teamNavController.navigate(R.id.createTeamTaskFragment, args)
            R.id.nav_team_task_management -> teamNavController.navigate(R.id.teamTaskManagementFragment, args)

            // For Member + Manager
            R.id.nav_assigned_tasks -> teamNavController.navigate(R.id.assignedTasksFragment, args)


            else -> Toast.makeText(context, "Feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        drawerLayout.closeDrawer(GravityCompat.END)
        return true
    }
}