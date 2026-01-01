package com.example.morp_prj.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask
import com.example.morp_prj.utils.PreferenceManager
import java.util.Calendar

class AssignedTeamTasksFragment : Fragment() {

    private val viewModel: TeamTaskViewModel by viewModels()
    private lateinit var adapter: AssignedTeamTasksAdapter

    // Lưu danh sách gốc của user (sau khi lọc theo userId)
    private var myTasks: List<TeamTask> = emptyList()

    // Trạng thái filter hiện tại: "OVERDUE", "TODAY", "UPCOMING", "COMPLETED", hoặc null (ALL)
    private var currentFilter: String? = null

    // Map lưu reference đến các CardView để đổi màu
    private var cardMap: Map<String, View>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_assigned_team_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvTasks = view.findViewById<RecyclerView>(R.id.rvTasks)
        adapter = AssignedTeamTasksAdapter(onArrowClick = { task, itemView ->
            // animate highlight on the row then navigate
            itemView.isClickable = false
            itemView.animate().alpha(0.85f).scaleX(0.995f).scaleY(0.995f).setDuration(120).withEndAction {
                // revert the animation quickly and navigate
                itemView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).withEndAction {
                    val bundle = Bundle().apply { putString("taskId", task.id) }
                    val navOptions = androidx.navigation.navOptions {
                        anim {
                            enter = R.anim.slide_in_right
                            exit = R.anim.slide_out_left
                            popEnter = R.anim.fade_in
                            popExit = R.anim.fade_out
                        }
                    }
                    findNavController().navigate(R.id.teamTaskDetailFragment, bundle, navOptions)
                }.start()
            }.start()
        })
        rvTasks.layoutManager = LinearLayoutManager(context)
        rvTasks.adapter = adapter

        // Setup click listeners for Summary Cards
        setupCardListeners(view)

        val teamId = arguments?.getString("teamId") ?: ""

        observeViewModel(view)

        if (teamId.isNotEmpty()) {
            viewModel.fetchTasks(teamId)
        }
    }

    private fun setupCardListeners(view: View) {
        val cardOverdue = view.findViewById<View>(R.id.cardOverdue)
        val cardToday = view.findViewById<View>(R.id.cardToday)
        val cardUpcoming = view.findViewById<View>(R.id.cardUpcoming)
        val cardCompleted = view.findViewById<View>(R.id.cardCompleted)

        cardMap = mapOf(
            "OVERDUE" to cardOverdue,
            "TODAY" to cardToday,
            "UPCOMING" to cardUpcoming,
            "COMPLETED" to cardCompleted
        )

        cardOverdue.setOnClickListener { toggleFilter("OVERDUE") }
        cardToday.setOnClickListener { toggleFilter("TODAY") }
        cardUpcoming.setOnClickListener { toggleFilter("UPCOMING") }
        cardCompleted.setOnClickListener { toggleFilter("COMPLETED") }
    }

    private fun toggleFilter(filterType: String) {
        if (currentFilter == filterType) {
            // Click lại thẻ đang chọn -> Bỏ chọn (Hiện tất cả)
            currentFilter = null
        } else {
            // Click thẻ mới -> Chọn filter đó
            currentFilter = filterType
        }

        updateCardVisuals()
        applyFilter()
    }

    private fun updateCardVisuals() {
        // Reset tất cả về màu mặc định (Trắng hoặc màu gốc)
        cardMap?.values?.forEach { view ->
            if (view is CardView) {
                view.setCardBackgroundColor(Color.WHITE)
            } else {
                view.setBackgroundColor(Color.WHITE)
            }
        }

        currentFilter?.let { filter ->
            val selectedCard = cardMap?.get(filter)
            if (selectedCard is CardView) {
                selectedCard.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
            } else {
                selectedCard?.setBackgroundColor(Color.LTGRAY)
            }
        }

        currentFilter?.let { filter ->
            val selectedCard = cardMap?.get(filter)
            if (selectedCard is CardView) {
                selectedCard.setCardBackgroundColor(Color.parseColor("#E3F2FD")) // Light Blue tint
            }
        }
    }

    private fun applyFilter() {
        if (myTasks.isEmpty()) {
            adapter.submitList(emptyList())
            return
        }

        val now = System.currentTimeMillis()
        val filteredList = if (currentFilter == null) {
            myTasks
        } else {
            myTasks.filter { task ->
                val isDone = task.status.uppercase() == "DONE" || task.status.uppercase() == "COMPLETED"
                when (currentFilter) {
                    "COMPLETED" -> isDone
                    "OVERDUE" -> !isDone && (task.status.uppercase() == "OVERDUE" || (task.dueDate != null && task.dueDate < now))
                    "TODAY" -> !isDone && !(task.status.uppercase() == "OVERDUE" || (task.dueDate != null && task.dueDate < now)) && (task.dueDate != null && isToday(task.dueDate))
                    "UPCOMING" -> !isDone && !(task.status.uppercase() == "OVERDUE" || (task.dueDate != null && task.dueDate < now)) && !(task.dueDate != null && isToday(task.dueDate)) && (task.dueDate != null && task.dueDate > now)
                    else -> true
                }
            }
        }
        adapter.submitList(filteredList)
    }

    private fun observeViewModel(view: View) {
        viewModel.tasks.observe(viewLifecycleOwner) { taskList ->
            // 1. Lọc lấy tasks của user hiện tại
            val userId = PreferenceManager(requireContext()).getUserId()
            myTasks = if (userId != null) {
                taskList.filter { task ->
                    task.assignees.any { it.id == userId }
                }
            } else {
                emptyList()
            }

            // 2. Tính toán Summary (luôn tính trên toàn bộ tasks của user)
            calculateMySummary(view, myTasks)

            // 3. Hiển thị danh sách (có áp dụng filter nếu đang chọn)
            applyFilter()
        }
    }

    private fun calculateMySummary(view: View, tasks: List<TeamTask>) {
        val now = System.currentTimeMillis()
        var overdue = 0
        var today = 0
        var upcoming = 0
        var completed = 0

        tasks.forEach { task ->
            when {
                task.status.uppercase() == "DONE" || task.status.uppercase() == "COMPLETED" -> completed++
                task.status.uppercase() == "OVERDUE" || (task.dueDate != null && task.dueDate < now) -> overdue++
                task.dueDate != null && isToday(task.dueDate) -> today++
                task.dueDate != null && task.dueDate > now -> upcoming++
            }
        }

        updateSummaryCard(view, R.id.cardOverdue, "Overdue", overdue)
        updateSummaryCard(view, R.id.cardToday, "Today", today)
        updateSummaryCard(view, R.id.cardUpcoming, "Upcoming", upcoming)
        updateSummaryCard(view, R.id.cardCompleted, "Completed", completed)
    }

    private fun isToday(timestamp: Long): Boolean {
        val taskDate = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayDate = Calendar.getInstance()
        return taskDate.get(Calendar.YEAR) == todayDate.get(Calendar.YEAR) &&
                taskDate.get(Calendar.DAY_OF_YEAR) == todayDate.get(Calendar.DAY_OF_YEAR)
    }

    private fun updateSummaryCard(rootView: View, cardId: Int, label: String, count: Int) {
        val card = rootView.findViewById<View>(cardId)
        if (card != null) {
            card.findViewById<TextView>(R.id.tvSummaryLabel).text = label
            card.findViewById<TextView>(R.id.tvSummaryCount).text = count.toString()
        }
    }
}