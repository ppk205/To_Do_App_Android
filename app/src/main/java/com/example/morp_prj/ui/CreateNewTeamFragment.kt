package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.CreateTeamRequest
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.utils.PreferenceManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CreateNewTeamFragment : Fragment() {

    private lateinit var etTeamName: EditText
    private lateinit var etTeamDescription: EditText
    private lateinit var btnCreateTeam: Button
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_new_team, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        etTeamName = view.findViewById(R.id.etTeamName)
        etTeamDescription = view.findViewById(R.id.etTeamDescription)
        val etTeamTags = view.findViewById<EditText>(R.id.etTeamTags)
        btnCreateTeam = view.findViewById(R.id.btnCreateTeam)

        btnCreateTeam.setOnClickListener {
            val name = etTeamName.text.toString().trim()
            val desc = etTeamDescription.text.toString().trim()
            val tags = etTeamTags.text.toString().trim()

            if (name.isEmpty()) {
                etTeamName.error = "Team Name is required"
                return@setOnClickListener
            }

            createTeam(name, desc, if (tags.isEmpty()) null else tags)
        }
    }

    private fun createTeam(name: String, description: String, tagsCsv: String?) {
        val userId = preferenceManager.getUserId()
        
        if (userId.isNullOrEmpty()) {
            Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateTeamRequest(name, description, userId, tagsCsv)

        // Vô hiệu hóa nút để tránh double click
        btnCreateTeam.isEnabled = false
        btnCreateTeam.text = "Creating..."

        RetrofitClient.teamApiService.createTeam(request).enqueue(object : Callback<Team> {
            override fun onResponse(call: Call<Team>, response: Response<Team>) {
                if (response.isSuccessful) {
                    onTeamCreatedSuccess()
                } else {
                    btnCreateTeam.isEnabled = true
                    btnCreateTeam.text = "Create Team"
                    val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(context, "Failed: ${response.code()} - $errorMsg", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<Team>, t: Throwable) {
                btnCreateTeam.isEnabled = true
                btnCreateTeam.text = "Create Team"
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun onTeamCreatedSuccess() {
        Toast.makeText(requireContext(), "Team created successfully!", Toast.LENGTH_LONG).show()

        // Gửi kết quả về cho màn hình trước (TeamFragment) để nó biết cần reload
        parentFragmentManager.setFragmentResult("team_created", Bundle.EMPTY)
        
        // Quay lại màn hình Team
        findNavController().popBackStack()
    }
}