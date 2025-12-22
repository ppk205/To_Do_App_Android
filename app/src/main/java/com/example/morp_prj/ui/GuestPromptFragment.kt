package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.google.android.material.button.MaterialButton

class GuestPromptFragment : Fragment(R.layout.fragment_guest_prompt) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btn_register_now)?.setOnClickListener {
            // Chuyển đến trang đăng ký
            findNavController().navigate(R.id.action_guestPrompt_to_register)
        }

        view.findViewById<MaterialButton>(R.id.btn_back_home)?.setOnClickListener {
            // Quay lại home
            findNavController().navigateUp()
        }
    }
}


