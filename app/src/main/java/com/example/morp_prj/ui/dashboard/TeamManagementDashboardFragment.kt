package com.example.morp_prj.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.TeamMember
import com.example.morp_prj.ui.NotificationsAdapter
import com.example.morp_prj.ui.TeamTaskViewModel
import com.example.morp_prj.ui.UiNotification
import com.example.morp_prj.utils.PreferenceManager
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.chip.ChipGroup
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TeamManagementDashboardFragment : Fragment() {

    private lateinit var viewModel: TeamTaskViewModel

    // Adapter cho Recent Activity
    private lateinit var recentActivityAdapter: NotificationsAdapter

    // Dữ liệu Remote
    private var remoteTasks: List<com.example.morp_prj.data.model.TeamTask> = emptyList()
    private var teamMembers: List<TeamMember> = emptyList()

    // User State
    private var currentUserId: String? = null
    private var isManager: Boolean = false

    // Filter State
    private var selectedAssigneeId: String? = null
    private var selectedTimeGranularity: String = "ALL"
    private var selectedTimeValue: String? = null
    private var selectedDateBasis: String = "DUE_DATE"

    private val assigneeDisplayToId = mutableMapOf<String, String?>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_team_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            val prefs = PreferenceManager(requireContext())
            currentUserId = prefs.getUserId()

            setupHeaderEvents(view)

            viewModel = ViewModelProvider(this).get(TeamTaskViewModel::class.java)

            // Views
            val rvRecent = view.findViewById<RecyclerView>(R.id.rvRecent)
            val rvSummary = view.findViewById<RecyclerView>(R.id.rvSummary)
            val pieChart = view.findViewById<PieChart>(R.id.pieChart)

            // Setup Recent Activity Adapter
            recentActivityAdapter = NotificationsAdapter(mutableListOf()) { _, _ -> }
            rvRecent.layoutManager = LinearLayoutManager(context)
            rvRecent.adapter = recentActivityAdapter
            rvRecent.isNestedScrollingEnabled = false

            // Setup Summary Adapter
            rvSummary.layoutManager = GridLayoutManager(requireContext(), 2)
            rvSummary.adapter = SummaryAdapter()

            // Resolve Team ID
            var resolvedTeamId = arguments?.getString("teamId")
            if (resolvedTeamId.isNullOrBlank()) {
                resolvedTeamId = findNavController().currentBackStackEntry?.arguments?.getString("teamId")
            }
            if (resolvedTeamId.isNullOrBlank()) {
                resolvedTeamId = parentFragment?.arguments?.getString("teamId")
            }
            if (resolvedTeamId.isNullOrBlank()) {
                resolvedTeamId = requireActivity().intent.getStringExtra("teamId")
            }
            val teamIdArg = resolvedTeamId?.trim()

            if (!teamIdArg.isNullOrBlank()) {
                fetchTeamMembers(teamIdArg) {
                    determineUserRole(view)
                    viewModel.fetchTasks(teamIdArg)
                }

                viewModel.tasks.observe(viewLifecycleOwner, Observer { list ->
                    remoteTasks = list
                    populateAssigneesDropdown(view)
                    applyFiltersAndUpdateDashboard(view)
                    generateTeamActivity()
                })

                setupFilterListeners(view)

            } else {
                Toast.makeText(context, "Team ID not found!", Toast.LENGTH_SHORT).show()
            }

        } catch (t: Throwable) {
            Log.e("TeamDashboard", "Error init: ", t)
        }
    }

    private fun setupHeaderEvents(view: View) {
        view.findViewById<View>(R.id.btnMenu)?.setOnClickListener {
            val drawer = parentFragment?.parentFragment?.view?.findViewById<DrawerLayout>(R.id.drawer_layout)
                ?: requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)

            if (drawer != null) {
                if (drawer.isDrawerOpen(GravityCompat.END)) drawer.closeDrawer(GravityCompat.END)
                else drawer.openDrawer(GravityCompat.END)
            }
        }
    }

    private fun determineUserRole(view: View) {
        if (currentUserId == null) return

        val me = teamMembers.find { it.id == currentUserId }

        isManager = me?.role?.lowercase() in listOf("manager", "co-manager")

        val actAssignee = view.findViewById<AutoCompleteTextView>(R.id.actAssignee)
        val tvLabel = view.findViewById<TextView>(R.id.tvFilterMemberLabel)

        if (!isManager) {
            actAssignee?.visibility = View.GONE
            tvLabel?.visibility = View.GONE
        } else {
            actAssignee?.visibility = View.VISIBLE
            tvLabel?.visibility = View.VISIBLE
        }
    }

    private fun applyFiltersAndUpdateDashboard(view: View) {
        val rvSummary = view.findViewById<RecyclerView>(R.id.rvSummary)
        val pieChart = view.findViewById<PieChart>(R.id.pieChart)

        val filtered = remoteTasks.filter { task ->
            val assigneeOk: Boolean
            if (isManager) {
                assigneeOk = selectedAssigneeId?.let { id ->
                    task.assignees.any { it.id == id }
                } ?: true
            } else {
                assigneeOk = currentUserId?.let { myId ->
                    task.assignees.any { it.id == myId }
                } ?: false
            }

            if (!assigneeOk) return@filter false

            // Filter Time
            if (selectedTimeGranularity == "ALL") return@filter true
            if (selectedTimeValue.isNullOrBlank()) return@filter true

            val dateToFilter = if (selectedDateBasis == "DUE_DATE") task.dueDate else task.createdAt
            if (dateToFilter == null || dateToFilter == 0L) return@filter false

            val cal = Calendar.getInstance()
            cal.timeInMillis = dateToFilter

            when (selectedTimeGranularity) {
                "DAY" -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time) == selectedTimeValue
                "MONTH" -> SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time) == selectedTimeValue
                "YEAR" -> cal.get(Calendar.YEAR).toString() == selectedTimeValue
                else -> true
            }
        }

        val now = System.currentTimeMillis()
        val total = filtered.size

        val done = filtered.count { it.status.equals("DONE", true) || it.status.equals("COMPLETED", true) }
        val todo = filtered.count { it.status.equals("TODO", true) }

        val overdue = filtered.count {
            val status = it.status.uppercase()
            val isNotDone = status != "DONE" && status != "COMPLETED"
            val isExplicitOverdue = status == "OVERDUE"
            val isExpired = it.dueDate != null && it.dueDate < now
            isNotDone && (isExplicitOverdue || isExpired)
        }

        val inProgress = total - done - overdue - todo

        val state = TaskDashboardUiState(total = total, done = done, inProgress = inProgress, overdue = overdue, todo = todo)

        (rvSummary.adapter as? SummaryAdapter)?.submit(state)
        updateChart(pieChart, state)
    }

    private fun updateChart(pieChart: PieChart, s: TaskDashboardUiState) {
        val entries = mutableListOf<PieEntry>()
        var totalCount = 0f

        if (s.done > 0) {
            entries.add(PieEntry(s.done.toFloat(), "Done"))
            totalCount += s.done
        }
        if (s.inProgress > 0) {
            entries.add(PieEntry(s.inProgress.toFloat(), "In Progress"))
            totalCount += s.inProgress
        }
        if (s.todo > 0) {
            entries.add(PieEntry(s.todo.toFloat(), "Todo"))
            totalCount += s.todo
        }
        if (s.overdue > 0) {
            entries.add(PieEntry(s.overdue.toFloat(), "Overdue"))
            totalCount += s.overdue
        }

        val ds = PieDataSet(entries, "")
        ds.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.brand_blue),
            ContextCompat.getColor(requireContext(), R.color.primary_light),
            ContextCompat.getColor(requireContext(), R.color.dark_gray),
            ContextCompat.getColor(requireContext(), R.color.danger_500)
        )
        ds.setDrawValues(false)

        val pd = PieData(ds)
        pieChart.data = pd

        // Appearance
        pieChart.setUsePercentValues(false)
        pieChart.description.isEnabled = false
        pieChart.setDrawEntryLabels(false)
        pieChart.legend.isEnabled = false
        pieChart.isRotationEnabled = false
        pieChart.holeRadius = 60f
        pieChart.setHoleColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
        pieChart.setCenterTextSize(14f)
        pieChart.setCenterTextColor(Color.BLACK)

        val defaultCenterText = if (s.total > 0) "${(s.done * 100 / s.total)}% Done" else "No tasks"
        pieChart.centerText = defaultCenterText

        pieChart.animateY(300)
        pieChart.invalidate()

        pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e is PieEntry && totalCount > 0f) {
                    val percent = (e.value / totalCount * 100).toInt()
                    pieChart.centerText = "${e.label}: ${percent}%"
                }
            }

            override fun onNothingSelected() {
                pieChart.centerText = defaultCenterText
            }
        })
    }

    private fun fetchTeamMembers(teamId: String, onComplete: () -> Unit) {
        RetrofitClient.teamApiService.getTeamMembers(teamId).enqueue(object : Callback<List<TeamMember>> {
            override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                if (response.isSuccessful) teamMembers = response.body() ?: emptyList()
                onComplete()
            }
            override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) { onComplete() }
        })
    }

    private fun generateTeamActivity() {
        val activityPairs = mutableListOf<Pair<UiNotification, Long>>()
        remoteTasks.forEach { task ->
            val time = if (task.createdAt > 0) task.createdAt else 0L
            if (time > 0) {
                val creatorName = getMemberName(task.createdBy ?: "")
                val notif = UiNotification(
                    id = task.id.hashCode().toLong(),
                    title = "New Task Created",
                    message = "'${task.title}' was added by $creatorName",
                    time = formatTimeAgo(time),
                    isNew = false,
                    dedupeKey = "task_${task.id}"
                )
                activityPairs.add(Pair(notif, time))
            }
        }
        teamMembers.forEach { member ->
            val time = parseIsoTime(member.joinedAt)
            if (time > 0) {
                val name = member.displayName ?: member.email ?: "Unknown"
                val notif = UiNotification(
                    id = member.id.hashCode().toLong(),
                    title = "New Member Joined",
                    message = "$name has joined the team",
                    time = formatTimeAgo(time),
                    isNew = false,
                    dedupeKey = "member_${member.id}"
                )
                activityPairs.add(Pair(notif, time))
            }
        }
        val sortedList = activityPairs.sortedByDescending { it.second }.map { it.first }.take(10)
        recentActivityAdapter.replaceAll(sortedList)

        val rvRecent = view?.findViewById<RecyclerView>(R.id.rvRecent)
        rvRecent?.visibility = if (sortedList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun getMemberName(userId: String): String {
        return teamMembers.find { it.id == userId }?.displayName
            ?: teamMembers.find { it.id == userId }?.email
            ?: "Someone"
    }

    private fun setupFilterListeners(view: View) {
        val actAssignee = view.findViewById<AutoCompleteTextView>(R.id.actAssignee)
        val actDateBasis = view.findViewById<AutoCompleteTextView>(R.id.actDateBasis)
        val actTimeSelector = view.findViewById<AutoCompleteTextView>(R.id.actTimeSelector)
        val chipGroupTime = view.findViewById<ChipGroup>(R.id.chipGroupTime)

        actAssignee.setOnClickListener { actAssignee.showDropDown() }
        actAssignee.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actAssignee.showDropDown() }
        actAssignee.setOnItemClickListener { parent, _, position, _ ->
            val sel = parent.getItemAtPosition(position) as String
            selectedAssigneeId = assigneeDisplayToId[sel]
            applyFiltersAndUpdateDashboard(view)
        }

        val dateBasisOptions = listOf("Due Date (Ngày hết hạn)", "Created Date (Ngày giao)")
        val basisAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dateBasisOptions)
        actDateBasis.setAdapter(basisAdapter)
        actDateBasis.setText(dateBasisOptions[0], false)
        actDateBasis.setOnClickListener { actDateBasis.showDropDown() }
        actDateBasis.setOnItemClickListener { _, _, position, _ ->
            selectedDateBasis = if (position == 0) "DUE_DATE" else "CREATED_AT"
            applyFiltersAndUpdateDashboard(view)
        }

        chipGroupTime.setOnCheckedChangeListener { _, checkedId ->
            selectedTimeGranularity = when (checkedId) {
                R.id.chipAllTime -> "ALL"
                R.id.chipDay -> "DAY"
                R.id.chipMonth -> "MONTH"
                R.id.chipYear -> "YEAR"
                else -> "ALL"
            }
            populateTimeValueOptions(selectedTimeGranularity)
            applyFiltersAndUpdateDashboard(view)
        }

        actTimeSelector.setOnClickListener { actTimeSelector.showDropDown() }
        actTimeSelector.setOnItemClickListener { parent, _, position, _ ->
            selectedTimeValue = parent.getItemAtPosition(position) as String
            applyFiltersAndUpdateDashboard(view)
        }
    }

    private fun populateAssigneesDropdown(view: View) {
        if (!isManager) return

        val allMemberNames = mutableSetOf<Pair<String, String>>()
        teamMembers.forEach {
            val name = it.displayName ?: it.email ?: "Unknown"
            val id = it.id
            allMemberNames.add(Pair(name, id))
        }
        val displayList = ArrayList<String>()
        displayList.add("All Members")
        assigneeDisplayToId.clear()
        assigneeDisplayToId["All Members"] = null
        allMemberNames.sortedBy { it.first }.forEach { (name, id) ->
            displayList.add(name)
            assigneeDisplayToId[name] = id
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, displayList)
        val actAssignee = view.findViewById<AutoCompleteTextView>(R.id.actAssignee)
        actAssignee?.setAdapter(adapter)

        val current = assigneeDisplayToId.entries.find { it.value == selectedAssigneeId }?.key
        actAssignee?.setText(current ?: "All Members", false)
    }

    private fun populateTimeValueOptions(granularity: String) {
        val actTimeSelector = view?.findViewById<AutoCompleteTextView>(R.id.actTimeSelector)
        if (granularity == "ALL") {
            actTimeSelector?.visibility = View.GONE
            selectedTimeValue = null
            return
        }
        actTimeSelector?.visibility = View.VISIBLE
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        when (granularity) {
            "DAY" -> for (i in 0..29) {
                cal.time = Date()
                cal.add(Calendar.DAY_OF_YEAR, -i)
                list.add(sdfDay.format(cal.time))
            }
            "MONTH" -> for (i in 0..11) {
                cal.time = Date()
                cal.add(Calendar.MONTH, -i)
                list.add(sdfMonth.format(cal.time))
            }
            "YEAR" -> {
                val year = cal.get(Calendar.YEAR)
                for (i in -1..2) list.add((year + i).toString())
            }
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, list)
        actTimeSelector?.setAdapter(adapter)
        if (list.isNotEmpty()) {
            selectedTimeValue = list[0]
            actTimeSelector?.setText(list[0], false)
        }
    }

    private fun parseIsoTime(iso: String?): Long {
        if (iso.isNullOrEmpty()) return 0L
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(iso)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun formatTimeAgo(time: Long): String {
        val diff = System.currentTimeMillis() - time
        val min = 60 * 1000
        val hour = 60 * min
        val day = 24 * hour
        return when {
            diff < 0 -> "Just now"
            diff < min -> "Just now"
            diff < hour -> "${diff / min}m ago"
            diff < day -> "${diff / hour}h ago"
            else -> "${diff / day}d ago"
        }
    }
}