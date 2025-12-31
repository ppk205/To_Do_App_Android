package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R

class AssignedTeamTasksFragment : Fragment() {

    // 1. Khai báo ViewModel (Yêu cầu thêm dependency 'androidx.fragment:fragment-ktx' trong build.gradle)
    private val viewModel: TeamTaskViewModel by viewModels()
    private lateinit var adapter: AssignedTeamTasksAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_assigned_team_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. Thiết lập RecyclerView
        val rvTasks = view.findViewById<RecyclerView>(R.id.rvTasks)
        adapter = AssignedTeamTasksAdapter()
        rvTasks.layoutManager = LinearLayoutManager(context)
        rvTasks.adapter = adapter

        // 3. Lấy teamId từ Arguments (truyền từ màn hình trước đó)
        val teamId = arguments?.getString("teamId") ?: ""

        // 4. Quan sát dữ liệu từ ViewModel
        observeViewModel(view)

        // 5. Gọi API lấy dữ liệu
        if (teamId.isNotEmpty()) {
            viewModel.fetchTasks(teamId)
        }
    }

    private fun observeViewModel(view: View) {
        // Quan sát danh sách công việc và cập nhật vào Adapter
        viewModel.tasks.observe(viewLifecycleOwner) { taskList ->
            adapter.submitList(taskList)
        }

        // Quan sát các biến đếm để cập nhật Summary Cards (Cần gán ID cho include trong XML)
        viewModel.overdueCount.observe(viewLifecycleOwner) { count ->
            updateSummaryCard(view, R.id.cardOverdue, "Overdue", count)
        }

        viewModel.todayCount.observe(viewLifecycleOwner) { count ->
            updateSummaryCard(view, R.id.cardToday, "Today", count)
        }

        viewModel.upcomingCount.observe(viewLifecycleOwner) { count ->
            updateSummaryCard(view, R.id.cardUpcoming, "Upcoming", count)
        }

        viewModel.completedCount.observe(viewLifecycleOwner) { count ->
            updateSummaryCard(view, R.id.cardCompleted, "Completed", count)
        }
    }

    private fun updateSummaryCard(rootView: View, cardId: Int, label: String, count: Int) {
        val card = rootView.findViewById<View>(cardId)
        if (card != null) {
            card.findViewById<TextView>(R.id.tvSummaryLabel).text = label
            card.findViewById<TextView>(R.id.tvSummaryCount).text = count.toString()
        }
    }
}