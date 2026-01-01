package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask
import com.example.morp_prj.databinding.FragmentTeamTaskManagementBinding
import com.google.android.material.chip.Chip
import androidx.navigation.fragment.findNavController

class TeamTaskManagementFragment : Fragment() {

    private var _binding: FragmentTeamTaskManagementBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TeamTaskViewModel
    private lateinit var adapter: TeamTaskManagementAdapter
    private var originalTasks: List<TeamTask> = emptyList()
    private var teamId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nhận teamId từ arguments. Ví dụ: Bundle.getString("teamId")
        teamId = arguments?.getString("teamId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamTaskManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupViewModel()
        setupFilters()

        if (teamId != null) {
            loadData(teamId!!)
        } else {
            Toast.makeText(context, "Team ID not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = TeamTaskManagementAdapter { task ->
            val bundle = Bundle().apply {
                putString("taskId", task.id)
            }
            findNavController().navigate(R.id.action_teamTaskManagement_to_taskDetail, bundle)
        }
        binding.rvTasks.layoutManager = LinearLayoutManager(context)
        binding.rvTasks.adapter = adapter
    }

    private fun setupViewModel() {
        //viewModel = ViewModelProvider(this)[TeamTaskViewModel::class.java]
        viewModel = ViewModelProvider(requireActivity())[TeamTaskViewModel::class.java]
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            originalTasks = tasks
            applyFilter() // Apply current filter to new data
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show/hide loading indicator if available in layout
        }
    }

    private fun loadData(id: String) {
        viewModel.fetchTasks(id)
    }

    private fun setupFilters() {
        // ChipGroup logic
        binding.chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                // Nếu không chọn gì, hiển thị All
                filterTasks("All")
            } else {
                val chip = group.findViewById<Chip>(checkedIds[0])
                val filterText = chip.text.toString()
                filterTasks(filterText)
            }
        }
    }

    private fun filterTasks(status: String) {
        val now = System.currentTimeMillis()
        if (status == "All") {
            adapter.submitList(originalTasks)
        } else if (status == "Overdue") {
            // Lọc các task quá hạn và chưa hoàn thành
            val filtered = originalTasks.filter { task ->
                val isOverdue = task.dueDate != null && task.dueDate < now && task.status.uppercase() != "DONE"
                isOverdue
            }
            adapter.submitList(filtered)
        } else {
            // Lọc theo status thông thường
            val filtered = originalTasks.filter {
                it.status.equals(status.replace(" ", "_"), ignoreCase = true) ||
                        it.status.equals(status, ignoreCase = true)
            }
            adapter.submitList(filtered)
        }
    }
    
    private fun applyFilter() {
        // Lấy chip đang check
        val checkedChipId = binding.chipGroupFilter.checkedChipId
        if (checkedChipId != View.NO_ID) {
            val chip = binding.chipGroupFilter.findViewById<Chip>(checkedChipId)
            filterTasks(chip.text.toString())
        } else {
            filterTasks("All")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}