package com.example.morp_prj.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.morp_prj.R
import com.example.morp_prj.data.TaskRepository
import com.example.morp_prj.data.db.AppDatabase
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.charts.PieChart
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.os.bundleOf
import androidx.navigation.Navigation
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Observer
import com.example.morp_prj.ui.TeamTaskViewModel
import androidx.navigation.fragment.findNavController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TeamManagementDashboardFragment : Fragment() {

    private lateinit var viewModel: TaskDashboardViewModel
    private lateinit var recentAdapter: TaskRecentAdapter

    // --- fields for remote filtering ---
    private var remoteTasks: List<com.example.morp_prj.data.model.TeamTask> = emptyList()
    private var selectedAssigneeId: String? = null // null means All
    private var selectedTimeGranularity: String = "DAY" // HOUR/DAY/MONTH/YEAR
    private var selectedTimeValue: String? = null
    private val assigneeDisplayToId = mutableMapOf<String, String?>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_task_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // ViewModel factory using existing TaskRepository
            val db = AppDatabase.getInstance(requireContext())
            val repo = TaskRepository(db.taskDao())

            val factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return TaskDashboardViewModel(repo) as T
                }
            }

            viewModel = ViewModelProvider(this, factory).get(TaskDashboardViewModel::class.java)

            // lookup views (avoid synthetic imports)
            val rvRecent = view.findViewById<RecyclerView>(R.id.rvRecent)
            val rvSummary = view.findViewById<RecyclerView>(R.id.rvSummary)
            val actAssignee = view.findViewById<AutoCompleteTextView>(R.id.actAssignee)
            val chipGroupTime = view.findViewById<ChipGroup>(R.id.chipGroupTime)
            val pieChart = view.findViewById<PieChart>(R.id.pieChart)

            // Determine if we're operating inside a team (TeamDetailFragment sets argument `teamId`)
            var resolvedTeamId = arguments?.getString("teamId")

            if (resolvedTeamId.isNullOrBlank()) {
                try {
                    resolvedTeamId = findNavController().currentBackStackEntry?.arguments?.getString("teamId")
                } catch (e: Exception) { /* Ignore */ }
            }
            if (resolvedTeamId.isNullOrBlank()) {
                resolvedTeamId = parentFragment?.arguments?.getString("teamId")
            }
            if (resolvedTeamId.isNullOrBlank()) {
                resolvedTeamId = requireActivity().intent.getStringExtra("teamId")
            }

            val teamIdArg = resolvedTeamId?.trim()

            android.util.Log.d("TaskDashboardFragment", "Resolved teamIdArg=$teamIdArg (raw=$teamIdArg)")

            if (!teamIdArg.isNullOrBlank()) {
                // Use remote TeamTaskViewModel to fetch tasks for the team
                val remoteVm = androidx.lifecycle.ViewModelProvider(requireActivity()).get(TeamTaskViewModel::class.java)

                // Adapter for remote TeamTask model
                val teamAdapter = TeamTaskRecentAdapter(
                    onArrowClick = { task, v ->
                        v.isPressed = true
                        v.postDelayed({ v.isPressed = false }, 120)
                        val bundle = bundleOf("taskId" to task.id)
                        Navigation.findNavController(v).navigate(R.id.action_teamDashboard_to_taskDetail, bundle)
                    },
                    onStatusChange = { task, checked ->
                        // map checked -> status string
                        val newStatus = if (checked) "DONE" else "IN_PROGRESS"
                        remoteVm.updateTaskStatus(task.id, newStatus) { success, err ->
                            // onComplete: refresh or rollback handled inside the VM
                        }
                    }
                )

                rvRecent.adapter = teamAdapter

                // helper to populate assignee selector
                fun populateAssignees(tasks: List<com.example.morp_prj.data.model.TeamTask>) {
                    val entries = mutableListOf("All")
                    assigneeDisplayToId.clear()
                    assigneeDisplayToId["All"] = null
                    for (t in tasks) {
                        for (a in t.assignees) {
                            val display = a.displayName ?: a.username ?: a.email ?: a.id
                            if (!assigneeDisplayToId.containsKey(display)) {
                                assigneeDisplayToId[display] = a.id
                                entries.add(display)
                            }
                        }
                    }
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, entries)
                    actAssignee.setAdapter(adapter)
                    // keep current selection if possible
                    val currentDisplay = assigneeDisplayToId.entries.find { it.value == selectedAssigneeId }?.key
                    if (currentDisplay != null) actAssignee.setText(currentDisplay, false) else actAssignee.setText("All", false)
                }

                // helper to populate time selector based on granularity
                fun populateTimeOptions(granularity: String) {
                    val list = mutableListOf<String>()
                    val cal = Calendar.getInstance()
                    when (granularity) {
                        "HOUR" -> {
                            for (h in 0..23) list.add(String.format(Locale.getDefault(), "%02d:00", h))
                        }
                        "DAY" -> {
                            // last 30 days
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            for (i in 0..29) {
                                val c = Calendar.getInstance()
                                c.add(Calendar.DAY_OF_YEAR, -i)
                                list.add(sdf.format(c.time))
                            }
                        }
                        "MONTH" -> {
                            val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                            for (i in 0..11) {
                                val c = Calendar.getInstance()
                                c.add(Calendar.MONTH, -i)
                                list.add(sdf.format(c.time))
                            }
                        }
                        "YEAR" -> {
                            val year = cal.get(Calendar.YEAR)
                            for (i in 0..4) list.add((year - i).toString())
                        }
                    }
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list)
                    val actTimeSelector = view.findViewById<AutoCompleteTextView>(R.id.actTimeSelector)
                    actTimeSelector.setAdapter(adapter)
                    if (list.isNotEmpty()) {
                        selectedTimeValue = list[0]
                        actTimeSelector.setText(list[0], false)
                    } else {
                        selectedTimeValue = null
                        actTimeSelector.setText("", false)
                    }
                }

                // apply filters to remoteTasks and update UI
                fun applyFiltersAndUpdate() {
                    val filtered = remoteTasks.filter { task ->
                        // assignee filter
                        val assigneeOk = selectedAssigneeId?.let { id ->
                            task.assignees.any { it.id == id }
                        } ?: true

                        if (!assigneeOk) return@filter false

                        // time filter
                        if (selectedTimeValue.isNullOrBlank()) return@filter true

                        val due = task.dueDate ?: return@filter false
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = due

                        when (selectedTimeGranularity) {
                            "HOUR" -> {
                                val hourStr = String.format(Locale.getDefault(), "%02d:00", cal.get(Calendar.HOUR_OF_DAY))
                                return@filter hourStr == selectedTimeValue
                            }
                            "DAY" -> {
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                return@filter sdf.format(cal.time) == selectedTimeValue
                            }
                            "MONTH" -> {
                                val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                                return@filter sdf.format(cal.time) == selectedTimeValue
                            }
                            "YEAR" -> {
                                return@filter cal.get(Calendar.YEAR).toString() == selectedTimeValue
                            }
                            else -> return@filter true
                        }
                    }

                    // compute summary state with OVERDUE included when status == OVERDUE OR dueDate < now and not DONE
                    val now = System.currentTimeMillis()
                    val total = filtered.size
                    val done = filtered.count { it.status.equals("DONE", true) }
                    val pending = filtered.count { it.status.equals("TODO", true) }
                    val overdue = filtered.count { it.status.equals("OVERDUE", true) || (it.dueDate != null && it.dueDate < now && !it.status.equals("DONE", true)) }

                    val state = TaskDashboardUiState(total = total, done = done, pending = pending, overdue = overdue, recent = emptyList())
                    (rvSummary.adapter as? SummaryAdapter)?.submit(state)
                    updateChart(pieChart, state)

                    teamAdapter.submitList(filtered)
                }

                // Observe LiveData tasks from TeamTaskViewModel
                android.util.Log.d("TaskDashboardFragment", "Fetching team tasks for teamId=$teamIdArg")
                remoteVm.tasks.observe(viewLifecycleOwner, Observer { list ->
                    android.util.Log.d("TaskDashboardFragment", "Remote tasks returned count=${list.size}")
                    remoteTasks = list

                    // populate assignees and time options
                    populateAssignees(list)
                    // set granularity from current chips
                    val checked = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupTime).checkedChipId
                    selectedTimeGranularity = when (checked) {
                        R.id.chipHour -> "HOUR"
                        R.id.chipDay -> "DAY"
                        R.id.chipMonth -> "MONTH"
                        R.id.chipYear -> "YEAR"
                        else -> "DAY"
                    }
                    populateTimeOptions(selectedTimeGranularity)

                    // set listeners for selectors
                    actAssignee.setOnItemClickListener { parent, _, position, _ ->
                        val sel = parent.getItemAtPosition(position) as String
                        selectedAssigneeId = assigneeDisplayToId[sel]
                        applyFiltersAndUpdate()
                    }

                    val actTimeSelectorView = view.findViewById<AutoCompleteTextView>(R.id.actTimeSelector)
                    actTimeSelectorView.setOnItemClickListener { parent, _, position, _ ->
                        selectedTimeValue = parent.getItemAtPosition(position) as String
                        applyFiltersAndUpdate()
                    }

                    // chip change should update options
                    val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupTime)
                    chipGroup.setOnCheckedChangeListener { _, checkedId ->
                        selectedTimeGranularity = when (checkedId) {
                            R.id.chipHour -> "HOUR"
                            R.id.chipDay -> "DAY"
                            R.id.chipMonth -> "MONTH"
                            R.id.chipYear -> "YEAR"
                            else -> "DAY"
                        }
                        populateTimeOptions(selectedTimeGranularity)
                        applyFiltersAndUpdate()
                    }

                    // initial filter apply
                    applyFiltersAndUpdate()
                })

                remoteVm.errorMessage.observe(viewLifecycleOwner, Observer { err ->
                    if (!err.isNullOrBlank()) {
                        android.util.Log.w("TaskDashboardFragment", "Remote fetch error: $err")
                        Toast.makeText(requireContext(), "Failed to load team tasks: $err", Toast.LENGTH_LONG).show()
                    }
                })

                // fetch initial
                android.util.Log.d("TaskDashboardFragment", "Calling remoteVm.fetchTasks($teamIdArg)")
                remoteVm.fetchTasks(teamIdArg)

            } else {
                // existing local repo logic (unchanged)
                // Setup adapters
                recentAdapter = TaskRecentAdapter(
                    onArrowClick = { task, v ->
                        // ripple/elevation handled by Material by default; highlight then navigate
                        v.isPressed = true
                        v.postDelayed({ v.isPressed = false }, 120)

                        // determine taskId string to send to detail fragment
                        val taskIdArg = task.serverId ?: task.id.toString()
                        val nav = Navigation.findNavController(v)
                        val bundle = bundleOf("taskId" to taskIdArg)
                        nav.navigate(R.id.action_teamDashboard_to_taskDetail, bundle)
                    },
                    onCheckboxToggle = { task, checked ->
                        // show progress by disabling checkbox is done in adapter; call viewModel to update
                        viewModel.toggleTaskDone(task.id, checked)
                    }
                )

                rvRecent.adapter = recentAdapter

                // Collect state with repeatOnLifecycle using viewLifecycleOwner (local flow)
                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.uiState.collectLatest { state ->
                            // update pie chart
                            updateChart(pieChart, state)
                            // update summary adapter
                            (rvSummary.adapter as? SummaryAdapter)?.submit(state)
                            // update recent
                            recentAdapter.submitList(state.recent)
                        }
                    }
                }
            }

            // Summary as grid of 4
            rvSummary.layoutManager = GridLayoutManager(requireContext(), 4)
            rvSummary.adapter = SummaryAdapter()

            // assignee dropdown placeholder
            val assignees = listOf("All Members")
            actAssignee.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, assignees))

            // time chips
            chipGroupTime.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.chipHour -> viewModel.setTimeRange(TimeRange.HOUR)
                    R.id.chipDay -> viewModel.setTimeRange(TimeRange.DAY)
                    R.id.chipMonth -> viewModel.setTimeRange(TimeRange.MONTH)
                    R.id.chipYear -> viewModel.setTimeRange(TimeRange.YEAR)
                }
            }

            // Note: local viewModel.uiState is collected only in the local branch above; remote branch uses remoteVm tasks LiveData
        } catch (t: Throwable) {
            Log.e("TaskDashboardFragment", "Error in onViewCreated: ", t)
            Toast.makeText(requireContext(), "Failed to open Team Dashboard: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateChart(pieChart: PieChart, s: TaskDashboardUiState) {
        val entries = mutableListOf<PieEntry>()
        var totalCount = 0f
        if (s.done > 0) { entries.add(PieEntry(s.done.toFloat(), "Done")); totalCount += s.done }
        if (s.pending > 0) { entries.add(PieEntry(s.pending.toFloat(), "Pending")); totalCount += s.pending }
        if (s.overdue > 0) { entries.add(PieEntry(s.overdue.toFloat(), "Overdue")); totalCount += s.overdue }

        val ds = PieDataSet(entries, "")
        ds.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.brand_blue),
            ContextCompat.getColor(requireContext(), R.color.primary_light),
            ContextCompat.getColor(requireContext(), R.color.danger_500)
        )
        ds.setDrawValues(false)
        val pd = PieData(ds)
        pieChart.data = pd

        // appearance
        pieChart.setUsePercentValues(false) // we'll compute percent manually
        pieChart.description.isEnabled = false
        pieChart.setDrawEntryLabels(false)
        pieChart.legend.isEnabled = false
        pieChart.isRotationEnabled = false
        pieChart.holeRadius = 60f
        pieChart.setHoleColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
        pieChart.setCenterTextSize(14f)

        // default center text
        pieChart.centerText = if (s.total > 0) "${(s.done * 100 / s.total)}% Done" else "No tasks"

        // animate
        pieChart.animateY(300)
        pieChart.invalidate()

        // selection listener: show label + percent
        pieChart.setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                if (e is PieEntry && totalCount > 0f) {
                    val percent = (e.value / totalCount * 100).toInt()
                    pieChart.centerText = "${e.label}: ${percent}%"
                }
            }

            override fun onNothingSelected() {
                pieChart.centerText = if (s.total > 0) "${(s.done * 100 / s.total)}% Done" else "No tasks"
            }
        })
    }
}
