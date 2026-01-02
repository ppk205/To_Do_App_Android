package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask
import com.example.morp_prj.databinding.FragmentTeamTaskDetailBinding
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.*

class TeamTaskDetailFragment : Fragment() {
    private var _binding: FragmentTeamTaskDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TeamTaskViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamTaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Kết nối vào Shared ViewModel
        viewModel = ViewModelProvider(requireActivity())[TeamTaskViewModel::class.java]

        // Lấy taskId và optional teamId từ bundle
        val taskId = arguments?.getString("taskId")
        val teamId = arguments?.getString("teamId")

        // Quan sát danh sách task
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            val task = tasks.find { it.id == taskId }

            // If found, render full details using the helper
            if (task != null) {
                displayTaskDetails(task)
            } else {
                // If not found and we have a teamId, attempt to fetch tasks for that team
                if (!teamId.isNullOrBlank()) {
                    viewModel.fetchTasks(teamId)
                }
            }
        }

        // If initial dataset is empty, we also trigger a fetch when teamId is provided
        if (viewModel.tasks.value.isNullOrEmpty() && !teamId.isNullOrBlank()) {
            viewModel.fetchTasks(teamId)
        }
    }

    private fun displayTaskDetails(task: TeamTask) {
        binding.tvTitle.text = task.title
        binding.tvDescription.text = task.description ?: "No description provided."

        // Date formatting
        task.dueDate?.let {
            val sdf = SimpleDateFormat("dd-MM-yyyy  |  HH:MM", Locale.getDefault())
            binding.tvDueDate.text = sdf.format(Date(it))
        } ?: run {
            binding.tvDueDate.text = "No deadline"
        }

        // Priority & Status Styling
        setupBadges(task)

        // Tags: render tagsCsv into chips
        binding.chipGroupTags.removeAllViews()
        task.tagsCsv?.let { csv ->
            val tags = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            for (tag in tags) {
                val chip = Chip(requireContext())
                chip.text = tag
                chip.isClickable = false
                chip.isCheckable = false
                chip.setTextColor(Color.DKGRAY)
                chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
                chip.textSize = 14f
                binding.chipGroupTags.addView(chip)
            }
        }

        binding.rvAssignees.layoutManager = LinearLayoutManager(context)
        val assigneeAdapter = AssigneeAdapter(task.assignees) // task.assignees là List<User>
        binding.rvAssignees.adapter = assigneeAdapter
    }

    private fun setupBadges(task: TeamTask) {
        binding.tvPriority.text = task.priority
        when (task.priority.uppercase()) {
            "HIGH" -> binding.tvPriority.setBackgroundResource(R.drawable.bg_priority_high)
            "MEDIUM" -> binding.tvPriority.setBackgroundResource(R.drawable.bg_priority_medium)
            "LOW" -> binding.tvPriority.setBackgroundResource(R.drawable.bg_priority_low)
            else -> binding.tvPriority.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
        }

        binding.tvStatus.text = task.status
        when (task.status.uppercase()) {
            "DONE" -> binding.tvStatus.setBackgroundResource(R.drawable.bg_circle_green)
            "IN_PROGRESS" -> binding.tvStatus.setBackgroundResource(R.drawable.bg_status_progress)
            else -> binding.tvStatus.setBackgroundResource(R.drawable.bg_todo_tag)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}