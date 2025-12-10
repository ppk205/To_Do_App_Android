package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.google.android.material.button.MaterialButton

class LoginFragment : Fragment(R.layout.fragment_login) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnLogin = view.findViewById<MaterialButton>(R.id.btn_login)
        val txtRegisterLink = view.findViewById<TextView>(R.id.txt_register_link)

        btnLogin.setOnClickListener {
            // TODO: authenticate; on success navigate to home
            findNavController().navigate(R.id.action_login_to_home)
        }

        txtRegisterLink.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        view.findViewById<View?>(R.id.btn_back)?.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}
