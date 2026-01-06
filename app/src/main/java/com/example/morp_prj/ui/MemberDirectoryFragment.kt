package com.example.morp_prj.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.TeamMember
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class MemberDirectoryFragment : Fragment(R.layout.fragment_member_directory) {

    private lateinit var rvMembers: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var memberAdapter: MemberDirectoryAdapter

    private var allMembers: List<TeamMember> = emptyList()
    private var teamId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            teamId = it.getString("teamId")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvMembers = view.findViewById(R.id.rvMembers)
        etSearch = view.findViewById(R.id.etSearch)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        setupRecyclerView()
        setupSearch()

        teamId?.let { loadMembers(it) } ?: run {
            Toast.makeText(context, "Team ID missing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        memberAdapter = MemberDirectoryAdapter { member ->
            openProfile(member)
        }
        rvMembers.layoutManager = LinearLayoutManager(context)
        rvMembers.adapter = memberAdapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMembers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadMembers(teamId: String) {
        progressBar.visibility = View.VISIBLE
        RetrofitClient.teamApiService.getTeamMembers(teamId).enqueue(object : Callback<List<TeamMember>> {
            override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    allMembers = response.body() ?: emptyList()
                    filterMembers(etSearch.text.toString())
                } else if (response.code() == 403) {
                    // Permission denied
                    tvEmptyState.text = "Access denied. Member directory is disabled by manager."
                    tvEmptyState.visibility = View.VISIBLE
                    rvMembers.visibility = View.GONE
                    Toast.makeText(context, "Member directory access denied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to load members", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun filterMembers(query: String) {
        val filtered = if (query.isEmpty()) {
            allMembers
        } else {
            val lowerQuery = query.lowercase(Locale.getDefault())
            allMembers.filter {
                (it.displayName ?: "").lowercase().contains(lowerQuery) ||
                        (it.email ?: "").lowercase().contains(lowerQuery)
            }
        }

        if (filtered.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvMembers.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvMembers.visibility = View.VISIBLE
            memberAdapter.submitList(filtered)
        }
    }

    private fun openProfile(member: TeamMember) {
        val args = Bundle().apply {
            putString("userId", member.id)
            putBoolean("readOnly", true)
            putString("displayName", member.displayName)
            putString("email", member.email)
            putString("avatarUrl", member.avatarUrl)
            putString("role", member.role)
        }
        findNavController().navigate(R.id.profileFragment, args)
    }
}

