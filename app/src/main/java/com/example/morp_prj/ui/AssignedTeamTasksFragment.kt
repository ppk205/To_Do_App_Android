package com.example.morp_prj.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.model.CalendarDate
import com.example.morp_prj.data.model.TeamTask
import com.example.morp_prj.utils.DateUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AssignedTeamTasksFragment : Fragment(R.layout.fragment_assigned_team_tasks) {

    private lateinit var viewModel: TeamTaskViewModel
    private lateinit var taskAdapter: AssignedTeamTasksAdapter
    private lateinit var dateAdapter: CalendarDateAdapter

    // UI Components
    private lateinit var rvDateSelector: RecyclerView
    private lateinit var rvTasks: RecyclerView
    private lateinit var btnPickDate: MaterialButton
    private lateinit var chipViewAll: Chip
    private lateinit var btnFilter: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnSort: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    // Data State
    private var allTasks: List<TeamTask> = emptyList()
    private var dateList = mutableListOf<CalendarDate>()
    private var selectedDateMillis: Long? = System.currentTimeMillis()
    private var statusFilter: String? = null

    // sort
    enum class SortType { TIME, PRIORITY }
    enum class SortOrder { NONE, DESC, ASC }

    private var currentSortType: SortType = SortType.TIME
    private var currentSortOrder: SortOrder = SortOrder.NONE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Init UI References
        rvDateSelector = view.findViewById(R.id.rvDateSelector)
        rvTasks = view.findViewById(R.id.rvAssignedTasks)
        btnPickDate = view.findViewById(R.id.btnPickDate)
        chipViewAll = view.findViewById(R.id.chipViewAll)
        btnFilter = view.findViewById(R.id.btnFilter)
        btnMenu = view.findViewById(R.id.btnMenu)
        btnSort = view.findViewById(R.id.btnSort)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmpty = view.findViewById(R.id.tvEmptyState)

        // Setup Logic
        setupDateSelector()
        setupTaskRecyclerView()
        setupControls()

        // Init ViewModel & Data
        viewModel = ViewModelProvider(requireActivity())[TeamTaskViewModel::class.java]
        val teamId = arguments?.getString("teamId")
        if (teamId != null) {
            observeTasks(teamId)
        } else {
            Toast.makeText(context, "Team ID missing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDateSelector() {
        dateList.clear()
        val calendar = Calendar.getInstance()
        val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.ENGLISH)

        // Tạo thanh ngày 14 ngày tới
        for (i in 0 until 14) {
            val dayOfWeek = dayOfWeekFormat.format(calendar.time)
            val dayNumber = calendar.get(Calendar.DAY_OF_MONTH).toString()
            val dateMillis = calendar.timeInMillis

            // Highlight nếu trùng với ngày đang chọn
            val isSelected = (selectedDateMillis != null && DateUtils.isSameDay(dateMillis, selectedDateMillis!!))

            dateList.add(CalendarDate(dateMillis, dayNumber, dayOfWeek, isSelected))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        dateAdapter = CalendarDateAdapter(dateList) { selectedDate ->
            selectedDateMillis = selectedDate.date
            chipViewAll.isChecked = false

            // Update UI highlight
            dateList.forEach { it.isSelected = (it.date == selectedDate.date) }
            dateAdapter.notifyDataSetChanged()

            applyFiltersAndSort()
        }

        rvDateSelector.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvDateSelector.adapter = dateAdapter
    }

    private fun setupTaskRecyclerView() {
        taskAdapter = AssignedTeamTasksAdapter(
            onTaskClick = { task ->
                val bundle = Bundle().apply {
                    putString("taskId", task.id)
                    putString("teamId", task.teamId)
                }
                findNavController().navigate(R.id.teamTaskDetailFragment, bundle)
            },
            onStatusChange = { task, newStatus ->
                viewModel.updateTaskStatus(task.id, newStatus)
            }
        )
        rvTasks.layoutManager = LinearLayoutManager(context)
        rvTasks.adapter = taskAdapter
    }

    private fun setupControls() {
        // Pick Date logic
        btnPickDate.setOnClickListener {
            val picker = CalendarPickerBottomSheet(allTasks, selectedDateMillis) { newDate ->
                selectedDateMillis = newDate
                chipViewAll.isChecked = false
                updateHorizontalDateSelector(newDate)
                applyFiltersAndSort()
            }
            picker.show(parentFragmentManager, "CalendarPicker")
        }

        // View All logic
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

        // Filter Status logic
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

        // Logic Sort
        btnSort.setOnClickListener {
            cycleSortOrder()
        }

        btnSort.setOnLongClickListener {
            showSortTypeMenu()
            true
        }

        // Menu Sidebar
        btnMenu.setOnClickListener {
            requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)?.openDrawer(GravityCompat.END)
        }
    }

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
                "Sort by Time (Due Date)" -> {
                    currentSortType = SortType.TIME
                    currentSortOrder = SortOrder.DESC
                }
                "Sort by Priority" -> {
                    currentSortType = SortType.PRIORITY
                    currentSortOrder = SortOrder.DESC
                }
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
            SortOrder.NONE -> {
                btnSort.setColorFilter(inactiveColor)
                btnSort.rotation = 0f
            }
            SortOrder.DESC -> {
                btnSort.setColorFilter(activeColor)
                btnSort.rotation = 0f
            }
            SortOrder.ASC -> {
                btnSort.setColorFilter(activeColor)
                btnSort.rotation = 180f
            }
        }
    }

    private fun observeTasks(teamId: String) {
        progressBar.visibility = View.VISIBLE
        viewModel.fetchAssignedTasks(teamId)
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            progressBar.visibility = View.GONE
            allTasks = tasks
            applyFiltersAndSort()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun applyFiltersAndSort() {
        var resultList = allTasks

        // Filter by Date
        if (selectedDateMillis != null) {
            resultList = resultList.filter { task ->
                task.dueDate != null && DateUtils.isSameDay(task.dueDate, selectedDateMillis!!)
            }
        }

        // Filter by Status
        if (statusFilter != null) {
            resultList = resultList.filter { task ->
                if (statusFilter == "OVERDUE") {
                    task.dueDate != null && task.dueDate < System.currentTimeMillis() && task.status != "DONE" && task.status != "COMPLETED"
                } else {
                    task.status.equals(statusFilter, ignoreCase = true)
                }
            }
        }

        //Apply Sorting
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

        // Update UI
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
            "HIGH" -> 3
            "MEDIUM" -> 2
            "LOW" -> 1
            else -> 0
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