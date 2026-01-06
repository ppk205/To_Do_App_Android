package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.CalendarDate
import com.example.morp_prj.data.model.TeamTask
import com.example.morp_prj.data.model.TeamMember
import com.example.morp_prj.utils.DateUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeamTaskManagementFragment : Fragment(R.layout.fragment_team_task_management) {

    private lateinit var viewModel: TeamTaskViewModel
    private lateinit var taskAdapter: TeamTaskManagementAdapter
    private lateinit var dateAdapter: CalendarDateAdapter

    // UI Components
    private lateinit var rvDateSelector: RecyclerView
    private lateinit var rvTasks: RecyclerView
    private lateinit var btnPickDate: MaterialButton
    private lateinit var chipViewAll: Chip
    private lateinit var btnFilter: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnSort: ImageButton
    private lateinit var fabCreateTask: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var chipAssignee: Chip

    // Data State
    private var allTasks: List<TeamTask> = emptyList()
    private var dateList = mutableListOf<CalendarDate>()
    private var selectedDateMillis: Long? = System.currentTimeMillis()
    private var statusFilter: String? = null
    private var assigneeFilterId: String? = null
    private var memberList: List<TeamMember> = emptyList()

    // Sort Logic
    enum class SortType { TIME, PRIORITY }
    enum class SortOrder { NONE, DESC, ASC }
    private var currentSortType: SortType = SortType.TIME
    private var currentSortOrder: SortOrder = SortOrder.NONE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvDateSelector = view.findViewById(R.id.rvDateSelector)
        rvTasks = view.findViewById(R.id.rvTeamTasks)
        btnPickDate = view.findViewById(R.id.btnPickDate)
        chipViewAll = view.findViewById(R.id.chipViewAll)
        btnFilter = view.findViewById(R.id.btnFilter)
        btnMenu = view.findViewById(R.id.btnMenu)
        btnSort = view.findViewById(R.id.btnSort)
        fabCreateTask = view.findViewById(R.id.fabCreateTask)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmpty = view.findViewById(R.id.tvEmptyState)
        chipAssignee = view.findViewById(R.id.chipAssignee)

        viewModel = ViewModelProvider(requireActivity())[TeamTaskViewModel::class.java]

        setupDateSelector()
        setupTaskRecyclerView()
        setupControls()

        // Init Data
        val teamId = arguments?.getString("teamId")
        if (teamId != null) {
            observeTasks(teamId)
            loadMembers(teamId)

            // Fab
            fabCreateTask.setOnClickListener {
                val bundle = Bundle().apply { putString("teamId", teamId) }
                findNavController().navigate(R.id.createTeamTaskFragment, bundle)
            }
        } else {
            Toast.makeText(context, "Team ID missing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDateSelector() {
        dateList.clear()
        val calendar = Calendar.getInstance()
        val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.ENGLISH)

        for (i in 0 until 14) {
            val dayOfWeek = dayOfWeekFormat.format(calendar.time)
            val dayNumber = calendar.get(Calendar.DAY_OF_MONTH).toString()
            val dateMillis = calendar.timeInMillis
            val isSelected = (selectedDateMillis != null && DateUtils.isSameDay(dateMillis, selectedDateMillis!!))
            dateList.add(CalendarDate(dateMillis, dayNumber, dayOfWeek, isSelected))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        dateAdapter = CalendarDateAdapter(dateList) { selectedDate ->
            selectedDateMillis = selectedDate.date
            chipViewAll.isChecked = false
            dateList.forEach { it.isSelected = (it.date == selectedDate.date) }
            dateAdapter.notifyDataSetChanged()
            applyFiltersAndSort()
        }

        rvDateSelector.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvDateSelector.adapter = dateAdapter
    }

    private fun setupTaskRecyclerView() {
        taskAdapter = TeamTaskManagementAdapter(
            onTaskClick = { task ->
                val bundle = Bundle().apply {
                    putString("taskId", task.id)
                    putString("teamId", task.teamId)
                }
                findNavController().navigate(R.id.teamTaskDetailFragment, bundle)
            },
            onStatusChange = { task, newStatus ->
                viewModel.updateTaskStatus(task.id, newStatus)
            },

            onEditClick = { task ->
                val bundle = Bundle().apply {
                    putString("teamId", task.teamId)
                    putString("taskId", task.id)
                    putString("taskTitle", task.title)
                    putString("taskDesc", task.description)
                    putString("taskPriority", task.priority)
                    putLong("taskDueDate", task.dueDate ?: 0L)

                    // Xử lý Tags (List -> ArrayList để truyền qua Bundle)
                    if (task.tags != null) {
                        putStringArrayList("taskTags", ArrayList(task.tags))
                    }

                    if (!task.assignees.isNullOrEmpty()) {
                        val assigneeIds = task.assignees.map { it.id }
                        putStringArrayList("taskAssigneeIds", ArrayList(assigneeIds))
                    }
                }
                findNavController().navigate(R.id.createTeamTaskFragment, bundle)
            },

            onDeleteClick = { task ->
                showDeleteConfirmationDialog(task)
            }
        )
        rvTasks.layoutManager = LinearLayoutManager(context)
        rvTasks.adapter = taskAdapter
    }

    private fun showDeleteConfirmationDialog(task: TeamTask) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete '${task.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteTask(task.id) {
                    Toast.makeText(context, "Task deleted successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupControls() {
        // Pick Date
        btnPickDate.setOnClickListener {
            val picker = CalendarPickerBottomSheet(allTasks, selectedDateMillis) { newDate ->
                selectedDateMillis = newDate
                chipViewAll.isChecked = false
                updateHorizontalDateSelector(newDate)
                applyFiltersAndSort()
            }
            picker.show(parentFragmentManager, "CalendarPicker")
        }

        // View All
        chipViewAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedDateMillis = null
                dateList.forEach { it.isSelected = false }
                dateAdapter.notifyDataSetChanged()
                applyFiltersAndSort()
            } else if (selectedDateMillis == null) {
                val today = System.currentTimeMillis()
                selectedDateMillis = today
                updateHorizontalDateSelector(today)
                applyFiltersAndSort()
            }
        }

        // Filter
        btnFilter.setOnClickListener {
            val popup = PopupMenu(context, btnFilter)
            popup.menu.add("All Statuses")
            popup.menu.add("TODO")
            popup.menu.add("IN_PROGRESS")
            popup.menu.add("DONE")
            popup.menu.add("OVERDUE")
            popup.setOnMenuItemClickListener { item ->
                statusFilter = if (item.title == "All Statuses") null else item.title.toString()
                applyFiltersAndSort()
                true
            }
            popup.show()
        }

        // Sort
        btnSort.setOnClickListener { cycleSortOrder() }
        btnSort.setOnLongClickListener { showSortTypeMenu(); true }

        // Menu
        btnMenu.setOnClickListener {
            requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)?.openDrawer(GravityCompat.END)
        }

        chipAssignee.setOnClickListener { showAssigneeMenu() }
    }

    // Helper Functions Logic
    private fun cycleSortOrder() {
        currentSortOrder = when (currentSortOrder) {
            SortOrder.NONE -> SortOrder.DESC
            SortOrder.DESC -> SortOrder.ASC
            SortOrder.ASC -> SortOrder.NONE
        }
        updateSortIcon()
        applyFiltersAndSort()

        val typeText = if (currentSortType == SortType.TIME) "Time" else "Priority"
        val orderText = when(currentSortOrder) {
            SortOrder.DESC -> if (currentSortType == SortType.PRIORITY) "Desc (High->Low)" else "Desc (Soon -> Late)"
            SortOrder.ASC -> if (currentSortType == SortType.PRIORITY) "Asc (Low->High)" else "Asc (Late -> Soon)"
            SortOrder.NONE -> "Default"
        }
        if (currentSortOrder != SortOrder.NONE) {
            Toast.makeText(context, "Sorted by $typeText: $orderText", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSortTypeMenu() {
        val popup = PopupMenu(context, btnSort)
        popup.menu.add("Sort by Time (Due Date)")
        popup.menu.add("Sort by Priority")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Sort by Time (Due Date)" -> { currentSortType = SortType.TIME; currentSortOrder = SortOrder.DESC }
                "Sort by Priority" -> { currentSortType = SortType.PRIORITY; currentSortOrder = SortOrder.DESC }
            }
            updateSortIcon()
            applyFiltersAndSort()
            Toast.makeText(context, "Switched to ${item.title}", Toast.LENGTH_SHORT).show()
            true
        }
        popup.show()
    }

    private fun updateSortIcon() {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.brand_blue)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        when (currentSortOrder) {
            SortOrder.NONE -> { btnSort.setColorFilter(inactiveColor); btnSort.rotation = 0f }
            SortOrder.DESC -> { btnSort.setColorFilter(activeColor); btnSort.rotation = 0f }
            SortOrder.ASC -> { btnSort.setColorFilter(activeColor); btnSort.rotation = 180f }
        }
    }

    private fun observeTasks(teamId: String) {
        progressBar.visibility = View.VISIBLE
        viewModel.fetchTasks(teamId)
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            progressBar.visibility = View.GONE
            allTasks = tasks
            applyFiltersAndSort()
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                progressBar.visibility = View.GONE
                //Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMembers(teamId: String) {
        RetrofitClient.teamApiService.getTeamMembers(teamId).enqueue(object : Callback<List<TeamMember>> {
            override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                if (response.isSuccessful) {
                    memberList = response.body() ?: emptyList()
                }
            }
            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) {
                // no-op
            }
        })
    }

    private fun showAssigneeMenu() {
        val popup = PopupMenu(context, chipAssignee)
        popup.menu.add(0, 0, 0, "Assignee: All")
        memberList.forEachIndexed { index, member ->
            popup.menu.add(0, index + 1, index + 1, member.displayName ?: member.email ?: "(no name)")
        }
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 0) {
                assigneeFilterId = null
                chipAssignee.text = "Assignee: All"
            } else {
                val member = memberList.getOrNull(item.itemId - 1)
                assigneeFilterId = member?.id
                chipAssignee.text = "${member?.displayName ?: "(no name)"}"
            }
            applyFiltersAndSort()
            true
        }
        popup.show()
    }

    private fun applyFiltersAndSort() {
        var resultList = allTasks

        if (selectedDateMillis != null) {
            resultList = resultList.filter { task ->
                task.dueDate != null && DateUtils.isSameDay(task.dueDate, selectedDateMillis!!)
            }
        }

        if (statusFilter != null) {
            resultList = resultList.filter { task ->
                if (statusFilter == "OVERDUE") {
                    task.dueDate != null && task.dueDate < System.currentTimeMillis() && task.status != "DONE" && task.status != "COMPLETED"
                } else {
                    task.status.equals(statusFilter, ignoreCase = true)
                }
            }
        }

        if (assigneeFilterId != null) {
            resultList = resultList.filter { task ->
                task.assignees?.any { it.id == assigneeFilterId } == true
            }
        }

        if (currentSortOrder != SortOrder.NONE) {
            resultList = resultList.sortedWith(Comparator { t1, t2 ->
                val p1 = getPriorityValue(t1.priority)
                val p2 = getPriorityValue(t2.priority)
                val d1 = t1.dueDate ?: Long.MAX_VALUE
                val d2 = t2.dueDate ?: Long.MAX_VALUE

                var comparison = 0
                if (currentSortType == SortType.PRIORITY) {
                    comparison = p1.compareTo(p2)
                    if (comparison == 0) comparison = d1.compareTo(d2)
                } else {
                    comparison = d1.compareTo(d2)
                    if (comparison == 0) comparison = p1.compareTo(p2)
                }

                if (currentSortType == SortType.PRIORITY){
                    if (currentSortOrder == SortOrder.DESC) {
                        comparison * -1
                    } else {
                        comparison
                    }
                } else {
                    if (currentSortOrder == SortOrder.ASC) {
                        comparison * -1
                    } else {
                        comparison
                    }
                }
            })
        }

        if (resultList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvTasks.visibility = View.GONE
            tvEmpty.text = "No tasks found"
        } else {
            tvEmpty.visibility = View.GONE
            rvTasks.visibility = View.VISIBLE
            taskAdapter.submitList(resultList)
        }
    }

    private fun getPriorityValue(priority: String): Int {
        return when (priority.uppercase()) {
            "HIGH" -> 3; "MEDIUM" -> 2; "LOW" -> 1; else -> 0
        }
    }

    private fun updateHorizontalDateSelector(targetDate: Long) {
        var found = false
        dateList.forEach {
            val isSame = DateUtils.isSameDay(it.date, targetDate)
            it.isSelected = isSame
            if (it.isSelected) found = true
        }
        if (!found) dateList.forEach { it.isSelected = false }
        dateAdapter.notifyDataSetChanged()
    }
}