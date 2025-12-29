package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.HandleJoinRequest
import com.example.morp_prj.data.model.TeamMember
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class JoinRequestFragment : Fragment(R.layout.fragment_join_requests) {

    private var teamId: String? = null
    private lateinit var requestAdapter: JoinRequestAdapter
    private var joinRequests = mutableListOf<TeamMember>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            teamId = it.getString("teamId")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            findNavController().popBackStack()
        }

        val rvRequests = view.findViewById<RecyclerView>(R.id.rvJoinRequests)
        rvRequests.layoutManager = LinearLayoutManager(context)
        requestAdapter = JoinRequestAdapter(joinRequests, 
            onApprove = { member -> handleRequest(member, "approve") },
            onReject = { member -> handleRequest(member, "reject") }
        )
        rvRequests.adapter = requestAdapter

        loadJoinRequests()
    }

    private fun loadJoinRequests() {
        val currentTeamId = teamId ?: return
        
        // Gọi API lấy danh sách member với status là "pending"
        RetrofitClient.teamApiService.getTeamMembers(currentTeamId, "pending").enqueue(object : Callback<List<TeamMember>> {
            override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                if (response.isSuccessful) {
                    joinRequests = (response.body() ?: emptyList()).toMutableList()
                    requestAdapter.updateData(joinRequests)
                } else {
                    Log.e("JoinRequest", "Failed to load requests: ${response.code()}")
                    Toast.makeText(context, "Failed to load requests", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
                Log.e("JoinRequest", "Error loading requests", t)
            }
        })
    }

    private fun handleRequest(member: TeamMember, action: String) {
        val currentTeamId = teamId ?: return
        val request = HandleJoinRequest(currentTeamId, member.id, action)

        RetrofitClient.teamApiService.handleJoinRequest(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Request ${action}ed successfully", Toast.LENGTH_SHORT).show()
                    // Xóa item khỏi danh sách và cập nhật adapter
                    joinRequests.remove(member)
                    requestAdapter.updateData(joinRequests)
                } else {
                    Toast.makeText(context, "Failed to handle request", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}