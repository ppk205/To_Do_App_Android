package com.example.morp_prj.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.morp_prj.R
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class TeamDetailFragment : Fragment(R.layout.fragment_team_detail), NavigationView.OnNavigationItemSelectedListener {

    private var teamId: String = ""
    private var teamName: String = ""
    private var userRole: String = "member"

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            teamId = it.getString("teamId", "")
            teamName = it.getString("teamName", "Team Detail")
            userRole = it.getString("role", "member")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        drawerLayout = view.findViewById(R.id.drawer_layout)
        navView = view.findViewById(R.id.nav_view)

        // Setup Header
        view.findViewById<TextView>(R.id.tvTeamName).text = teamName
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            findNavController().popBackStack()
        }

        // Phân quyền cho nút Settings và sidebar
        val btnSettings = view.findViewById<ImageView>(R.id.imgSettings)
        if (userRole.equals("manager", ignoreCase = true)) {
            btnSettings.setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.END)
            }
            navView.setNavigationItemSelectedListener(this)
        } else {
            btnSettings.visibility = View.GONE // Ẩn nút settings cho member
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED) // Khóa sidebar
        }

        // Setup ViewPager & Tabs
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val adapter = TeamPagerAdapter(this, teamId, userRole)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "Tasks"
                1 -> tab.text = "Members"
            }
        }.attach()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Xử lý khi một mục trong sidebar được chọn
        when (item.itemId) {
            R.id.nav_member_management -> toast("Member Management selected")
            R.id.nav_join_requests -> toast("Join Requests selected")
            R.id.nav_create_task -> toast("Create Team Task selected")
            R.id.nav_assigned_tasks -> toast("Assigned Tasks selected")
            R.id.nav_task_management -> toast("Team Task Management selected")
            R.id.nav_manager_dashboard -> toast("Dashboard selected")
        }

        // Đóng sidebar sau khi chọn
        drawerLayout.closeDrawer(GravityCompat.END)
        return true
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    class TeamPagerAdapter(
        fragment: Fragment, 
        private val teamId: String,
        private val role: String
    ) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            val args = Bundle().apply { 
                putString("teamId", teamId)
                putString("role", role)
            }
            
            val fragment = when (position) {
                0 -> TeamTasksFragment()
                1 -> TeamMembersFragment()
                else -> TeamTasksFragment()
            }
            fragment.arguments = args
            return fragment
        }
    }
}