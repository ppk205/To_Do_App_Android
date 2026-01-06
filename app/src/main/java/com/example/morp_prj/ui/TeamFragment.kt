package com.example.morp_prj.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.JoinTeamRequest
import com.example.morp_prj.data.model.Team
import com.example.morp_prj.data.repository.TeamRealtimeRepository
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamFragment : Fragment() {

    // --- UI Components ---
    private lateinit var rvTeams: RecyclerView
    private lateinit var edtSearch: EditText
    private lateinit var filterAll: TextView
    private lateinit var filterPinned: TextView
    private lateinit var filterManager: TextView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var fabAddTeam: FloatingActionButton
    private lateinit var imgLogo: ImageView

    // --- Data & Adapters ---
    private lateinit var teamAdapter: TeamAdapter
    private var allTeams = mutableListOf<Team>()       // Danh sách gốc từ API
    private var displayedTeams = mutableListOf<Team>() // Danh sách đang hiển thị (sau khi lọc)

    // --- Logic Variables ---
    private lateinit var preferenceManager: PreferenceManager
    private val realtimeRepo = TeamRealtimeRepository()
    private var isGuestDialogShowing = false
    private var currentFilterMode = FilterMode.ALL // Trạng thái filter hiện tại

    enum class FilterMode { ALL, PINNED, MANAGER }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_team, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { }
        })

        preferenceManager = PreferenceManager(requireContext())

        initViews(view)

        setupRecyclerView()
        setupEventHandlers()
        loadLogo()
        view.findViewById<ImageView>(R.id.btnNotifications).setOnClickListener {
            findNavController().navigate(R.id.menu_notifications)
        }

        parentFragmentManager.setFragmentResultListener("team_created", viewLifecycleOwner) { _, _ ->
            loadMyTeams()
        }
    }

    override fun onResume() {
        super.onResume()
        checkGuestModeAndLoadData()
    }

    private fun initViews(view: View) {
        rvTeams = view.findViewById(R.id.rvMain)
        edtSearch = view.findViewById(R.id.edtSearch)
        filterAll = view.findViewById(R.id.filterAll)
        filterPinned = view.findViewById(R.id.filterPinned)
        filterManager = view.findViewById(R.id.filterManager)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        fabAddTeam = view.findViewById(R.id.fabAddTeam)
        imgLogo = view.findViewById(R.id.imgLogo)
    }

    private fun setupRecyclerView() {
        teamAdapter = TeamAdapter(
            teams = emptyList(),
            onTeamClick = { team -> navigateToDetail(team) },
            onChatClick = { team -> handleChatClick(team) },
            onPinClick = { team -> togglePin(team) }
        )

        rvTeams.layoutManager = LinearLayoutManager(context)
        rvTeams.adapter = teamAdapter
    }

    private fun setupEventHandlers() {
        // FAB Click
        fabAddTeam.setOnClickListener { showAddTeamBottomSheet() }

        // Search Text Watcher
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters(s.toString(), currentFilterMode)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter Chips Click
        filterAll.setOnClickListener { updateFilterUI(FilterMode.ALL) }
        filterPinned.setOnClickListener { updateFilterUI(FilterMode.PINNED) }
        filterManager.setOnClickListener { updateFilterUI(FilterMode.MANAGER) }
    }

    // --- LOGIC FILTER ---
    private fun updateFilterUI(mode: FilterMode) {
        currentFilterMode = mode
        val searchQuery = edtSearch.text.toString()

        setChipStyle(filterAll, false)
        setChipStyle(filterPinned, false)
        setChipStyle(filterManager, false)

        when (mode) {
            FilterMode.ALL -> setChipStyle(filterAll, true)
            FilterMode.PINNED -> setChipStyle(filterPinned, true)
            FilterMode.MANAGER -> setChipStyle(filterManager, true)
        }

        applyFilters(searchQuery, mode)
    }

    private fun setChipStyle(textView: TextView, isActive: Boolean) {
        val context = requireContext()
        if (isActive) {
            textView.setBackgroundResource(R.drawable.bg_button_primary) // Xanh
            textView.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            textView.setBackgroundResource(R.drawable.bg_tag_grey) // Xám
            textView.setTextColor(ContextCompat.getColor(context, R.color.gray_text)) // Màu xám đậm
        }
    }

    private fun applyFilters(query: String, mode: FilterMode) {
        val filtered = allTeams.filter { team ->
            val matchesSearch = team.name.contains(query, ignoreCase = true) ||
                    (team.description?.contains(query, ignoreCase = true) == true)

            val matchesMode = when (mode) {
                FilterMode.ALL -> true
                FilterMode.PINNED -> team.isPinned
                FilterMode.MANAGER -> team.role == "manager"
            }

            matchesSearch && matchesMode
        }

        displayedTeams = filtered.toMutableList()
        teamAdapter.updateData(displayedTeams)

        // Xử lý Empty State
        if (displayedTeams.isEmpty()) {
            rvTeams.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            rvTeams.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }

    // --- API & DATA LOADING ---
    private fun checkGuestModeAndLoadData() {
        if (preferenceManager.isGuest()) {
            if (!isGuestDialogShowing) {
                isGuestDialogShowing = true
                AlertDialog.Builder(requireContext())
                    .setTitle("Restricted Access").setMessage("Please sign in to use Team Mode features.")
                    .setPositiveButton("Sign In") { d, _ -> d.dismiss(); findNavController().navigate(R.id.login_fragment) }
                    .setNegativeButton("Cancel") { d, _ -> d.dismiss(); if (!findNavController().popBackStack()) findNavController().navigate(R.id.menu_home) }
                    .setOnDismissListener { isGuestDialogShowing = false }.setCancelable(false).show()
            }
        } else {
            loadMyTeams()
        }
    }

    private fun loadMyTeams() {
        val userId = preferenceManager.getUserId() ?: return
        RetrofitClient.teamApiService.getMyTeams(userId).enqueue(object : Callback<List<Team>> {
            override fun onResponse(call: Call<List<Team>>, response: Response<List<Team>>) {
                if (response.isSuccessful) {
                    allTeams = (response.body() ?: emptyList()).toMutableList()
                    // Sau khi load xong, áp dụng lại bộ lọc hiện tại
                    applyFilters(edtSearch.text.toString(), currentFilterMode)
                } else {
                    Log.e("TeamFragment", "API Error: ${response.errorBody()?.string()}")
                    Toast.makeText(context, "Failed to load teams", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<List<Team>>, t: Throwable) {
                Log.e("TeamFragment", "API Failure: ${t.message}", t)
                // Có thể show UI lỗi mạng tại đây nếu cần
            }
        })
    }

    // --- ACTIONS ---
    private fun navigateToDetail(team: Team) {
        val bundle = Bundle().apply {
            putString("teamId", team.id)
            putString("teamName", team.name)
            putString("role", team.role ?: "member")
            putString("inviteCode", team.inviteCode ?: "")
        }
        findNavController().navigate(R.id.action_team_to_detail, bundle)
    }

    private fun handleChatClick(team: Team) {
        // Join room socket
        realtimeRepo.joinTeamRoom(team.id)

        // Navigate to Chat Screen
        val bundle = Bundle().apply {
            putString("teamId", team.id)
            putString("teamName", team.name)
        }
        findNavController().navigate(R.id.teamChatFragment, bundle)
    }

    private fun showAddTeamBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_team_options, null)
        bottomSheetDialog.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btnOptionCreate).setOnClickListener {
            bottomSheetDialog.dismiss()
            findNavController().navigate(R.id.create_new_team_fragment)
        }

        sheetView.findViewById<View>(R.id.btnOptionJoin).setOnClickListener {
            bottomSheetDialog.dismiss()
            showJoinTeamDialog()
        }
        bottomSheetDialog.show()
    }

    private fun showJoinTeamDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_join_team, null)
        val etInviteCode = dialogView.findViewById<EditText>(R.id.etInviteCode)

        AlertDialog.Builder(requireContext())
            .setTitle("Join a Team")
            .setView(dialogView)
            .setPositiveButton("Join") { _, _ ->
                val code = etInviteCode.text.toString().trim()
                if (code.isNotEmpty()) {
                    performJoinTeam(code)
                } else {
                    Toast.makeText(context, "Please enter a code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performJoinTeam(inviteCode: String) {
        val userId = preferenceManager.getUserId() ?: return
        val request = JoinTeamRequest(userId, inviteCode)

        RetrofitClient.teamApiService.joinTeam(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Successfully joined team!", Toast.LENGTH_SHORT).show()
                    loadMyTeams()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Failed to join team"
                    Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Connection Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadLogo() {
        Glide.with(this)
            .load(R.drawable.logotaskly)
            .placeholder(R.drawable.logotaskly)
            .error(R.drawable.logotaskly)
            .dontTransform()
            .into(imgLogo)
        imgLogo.clearColorFilter()
    }

    private fun togglePin(team: Team) {
        val userId = preferenceManager.getUserId() ?: return
        val newPinState = !team.isPinned
        val request = com.example.morp_prj.data.model.PinTeamRequest(userId, team.id, newPinState)
        RetrofitClient.teamApiService.togglePinTeam(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    team.isPinned = newPinState
                    applyFilters(edtSearch.text.toString(), currentFilterMode)
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("TeamFragment", "Pin toggle failed", t)
            }
        })
    }
}