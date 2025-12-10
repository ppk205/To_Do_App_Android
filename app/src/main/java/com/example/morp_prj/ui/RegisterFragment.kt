package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.google.android.material.button.MaterialButton

class RegisterFragment : Fragment(R.layout.fragment_register) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnRegister = view.findViewById<MaterialButton>(R.id.btn_register)
        btnRegister.setOnClickListener {
            // TODO: register flow; on success navigate to home
            findNavController().navigate(R.id.action_register_to_home)
        }
    }
}
