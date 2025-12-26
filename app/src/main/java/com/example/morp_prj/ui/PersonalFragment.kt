package com.example.morp_prj.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.TaskRepository
import com.example.morp_prj.data.TaskUiMapper.toUiItem
import com.example.morp_prj.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PersonalFragment : Fragment() {

    private enum class StatusTab { ALL, TODO, IN_PROGRESS, DONE }

    private enum class DateFilter { ANY, TODAY, TOMORROW, THIS_WEEK, NO_DEADLINE }

    private var selectedTab: StatusTab = StatusTab.ALL
    private var queryText: String = ""

    private var selectedDateFilter: DateFilter = DateFilter.ANY
    private var selectedPriorityFilter: PriorityLevel? = null
    private var selectedTagFilter: String? = null

    private val repository by lazy {
        TaskRepository(AppDatabase.getInstance(requireContext()).taskDao())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_personal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerTasks)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Navigate to create task
        view.findViewById<View>(R.id.createTask).setOnClickListener {
            findNavController().navigate(R.id.action_personal_to_task)
        }

        // Tabs
        val tabAll = view.findViewById<TextView>(R.id.tab_all)
        val tabTodo = view.findViewById<TextView>(R.id.tab_todo)
        val tabProgress = view.findViewById<TextView>(R.id.tab_progress)
        val tabDone = view.findViewById<TextView>(R.id.tab_done)

        fun updateTabUi() {
            // Basic highlight by swapping backgrounds
            fun setActive(tv: TextView, active: Boolean) {
                tv.setBackgroundResource(if (active) R.drawable.tab_active else R.drawable.tab_inactive)
                tv.setTextColor(resources.getColor(if (active) R.color.white else android.R.color.black, null))
            }

            setActive(tabAll, selectedTab == StatusTab.ALL)
            setActive(tabTodo, selectedTab == StatusTab.TODO)
            setActive(tabProgress, selectedTab == StatusTab.IN_PROGRESS)
            setActive(tabDone, selectedTab == StatusTab.DONE)
        }

        updateTabUi()

        // Filter chips
        val filterDate = view.findViewById<TextView>(R.id.filterDate)
        val filterPriority = view.findViewById<TextView>(R.id.filterPriority)
        val filterTag = view.findViewById<TextView>(R.id.filterTag)

        fun updateFilterUi() {
            filterDate.text = when (selectedDateFilter) {
                DateFilter.ANY -> "📅  Date"
                DateFilter.TODAY -> "📅 Today"
                DateFilter.TOMORROW -> "📅 Tomorrow"
                DateFilter.THIS_WEEK -> "📅 This week"
                DateFilter.NO_DEADLINE -> "📅 No deadline"
            }

            filterPriority.text = selectedPriorityFilter?.let { "🏷 ${it.name}" } ?: "🏷 Priority"
            filterTag.text = selectedTagFilter?.let { "🔖 $it" } ?: "🔖 Tag"
        }
        updateFilterUi()

        // Search UI: toggle a simple EditText under header (created programmatically to avoid XML changes)
        val header = view.findViewById<ViewGroup>(R.id.header)
        val ivSearch = view.findViewById<ImageView>(R.id.ivSearch)
        val searchBox = EditText(requireContext()).apply {
            hint = "Search tasks..."
            setPadding(24, 16, 24, 16)
            isSingleLine = true
            isVisible = false
        }
        header.addView(searchBox)

        val allItemsFlow: Flow<List<ToDoItem>> = repository.observeAll()
            .map { list -> list.map { it.toUiItem() } }
            .distinctUntilChanged()

        fun statusMatches(item: ToDoItem): Boolean {
            return when (selectedTab) {
                StatusTab.ALL -> true
                StatusTab.TODO -> item.status == TaskStatus.TODO
                StatusTab.IN_PROGRESS -> item.status == TaskStatus.IN_PROGRESS
                StatusTab.DONE -> item.status == TaskStatus.DONE
            }
        }

        fun dateMatches(item: ToDoItem): Boolean {
            return when (selectedDateFilter) {
                DateFilter.ANY -> true
                DateFilter.NO_DEADLINE -> item.dueCategory == DueCategory.NONE && item.timeLabel == "No deadline"
                DateFilter.TODAY -> item.dueCategory == DueCategory.TODAY
                DateFilter.TOMORROW -> item.dueCategory == DueCategory.TOMORROW
                DateFilter.THIS_WEEK -> item.dueCategory == DueCategory.THIS_WEEK
            }
        }

        fun priorityMatches(item: ToDoItem): Boolean {
            val p = selectedPriorityFilter ?: return true
            return item.priority == p
        }

        fun tagMatches(item: ToDoItem): Boolean {
            val tag = selectedTagFilter?.trim().orEmpty()
            if (tag.isBlank()) return true
            return item.tags.any { it.equals(tag, ignoreCase = true) }
        }

        fun searchMatches(item: ToDoItem): Boolean {
            val q = queryText.trim().lowercase()
            if (q.isBlank()) return true
            if (item.title.lowercase().contains(q)) return true
            return item.tags.any { it.lowercase().contains(q) }
        }

        fun applyFilters(items: List<ToDoItem>): List<ToDoItem> {
            return items
                .asSequence()
                .filter(::statusMatches)
                .filter(::dateMatches)
                .filter(::priorityMatches)
                .filter(::tagMatches)
                .filter(::searchMatches)
                .toList()
        }

        var latestAllItems: List<ToDoItem> = emptyList()

        fun render(filtered: List<ToDoItem>) {
            recyclerView.adapter = ToDoAdapter(
                items = filtered,
                onCheckedChanged = { todo, isChecked ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val newStatus = if (isChecked) "DONE" else "TODO"
                        repository.updateStatus(todo.id, newStatus)
                    }
                },
            )
        }

        fun refreshUi() {
            updateFilterUi()
            render(applyFilters(latestAllItems))
        }

        // Tab clicks
        tabAll.setOnClickListener { selectedTab = StatusTab.ALL; updateTabUi(); refreshUi() }
        tabTodo.setOnClickListener { selectedTab = StatusTab.TODO; updateTabUi(); refreshUi() }
        tabProgress.setOnClickListener { selectedTab = StatusTab.IN_PROGRESS; updateTabUi(); refreshUi() }
        tabDone.setOnClickListener { selectedTab = StatusTab.DONE; updateTabUi(); refreshUi() }

        // Filter dialogs
        filterDate.setOnClickListener {
            val labels = arrayOf("Any", "Today", "Tomorrow", "This week", "No deadline")
            val values = arrayOf(DateFilter.ANY, DateFilter.TODAY, DateFilter.TOMORROW, DateFilter.THIS_WEEK, DateFilter.NO_DEADLINE)
            val checked = values.indexOf(selectedDateFilter).coerceAtLeast(0)

            AlertDialog.Builder(requireContext())
                .setTitle("Filter by date")
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    selectedDateFilter = values[which]
                    dialog.dismiss()
                    refreshUi()
                }
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear") { _, _ ->
                    selectedDateFilter = DateFilter.ANY
                    refreshUi()
                }
                .show()
        }

        filterPriority.setOnClickListener {
            val labels = arrayOf("Any", "LOW", "MEDIUM", "HIGH")
            val checked = when (selectedPriorityFilter) {
                null -> 0
                PriorityLevel.LOW -> 1
                PriorityLevel.MEDIUM -> 2
                PriorityLevel.HIGH -> 3
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Filter by priority")
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    selectedPriorityFilter = when (which) {
                        1 -> PriorityLevel.LOW
                        3 -> PriorityLevel.HIGH
                        2 -> PriorityLevel.MEDIUM
                        else -> null
                    }
                    dialog.dismiss()
                    refreshUi()
                }
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear") { _, _ ->
                    selectedPriorityFilter = null
                    refreshUi()
                }
                .show()
        }

        filterTag.setOnClickListener {
            val tags = latestAllItems.flatMap { it.tags }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .sorted()

            if (tags.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setMessage("No tags available yet. Create tasks with tags first.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            val labels = arrayOf("Any") + tags.toTypedArray()
            val checked = (labels.indexOfFirst { it.equals(selectedTagFilter, ignoreCase = true) }).let { if (it >= 0) it else 0 }

            AlertDialog.Builder(requireContext())
                .setTitle("Filter by tag")
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    selectedTagFilter = if (which == 0) null else labels[which]
                    dialog.dismiss()
                    refreshUi()
                }
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear") { _, _ ->
                    selectedTagFilter = null
                    refreshUi()
                }
                .show()
        }

        // Search
        ivSearch.setOnClickListener {
            searchBox.isVisible = !searchBox.isVisible
            if (!searchBox.isVisible) {
                searchBox.setText("")
                queryText = ""
                refreshUi()
            }
        }

        searchBox.addTextChangedListener {
            queryText = it?.toString().orEmpty()
            refreshUi()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            allItemsFlow.collect { all ->
                latestAllItems = all
                refreshUi()
            }
        }
    }
}