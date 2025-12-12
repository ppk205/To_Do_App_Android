package com.example.morp_prj.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.morp_prj.R
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.card.MaterialCardView

class HomeFragment : Fragment() {

    private lateinit var preferenceManager: PreferenceManager
    private var isGuest = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())
        isGuest = preferenceManager.isGuest()

        // Setup user info display
        setupUserInfo(view)

        // Setup FAB (chỉ cho phép thêm task nếu không phải guest)
        val fabAdd = view.findViewById<MaterialCardView>(R.id.card_fab)
        if (isGuest) {
            fabAdd.setOnClickListener {
                Toast.makeText(requireContext(), "Bạn phải đăng nhập để thêm công việc", Toast.LENGTH_SHORT).show()
            }
        } else {
            fabAdd.setOnClickListener {
                // Thêm công việc
                Toast.makeText(requireContext(), "Thêm công việc mới", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupUserInfo(view: View) {
        val headerTitle = view.findViewById<TextView>(R.id.tv_header_title)

        if (isGuest) {
            // Guest mode
            headerTitle.text = "Guest - Công việc"
        } else {
            // Logged in mode
            val displayName = preferenceManager.getDisplayName() ?: "User"
            headerTitle.text = "$displayName - Công việc"
        }
    }
}