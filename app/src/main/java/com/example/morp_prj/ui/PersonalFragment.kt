package com.example.morp_prj.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

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

    private var recyclerView: RecyclerView? = null
    private var adapter: ToDoAdapter? = null
    private var latestAllItems: List<ToDoItem> = emptyList()
    private val selectedIds = mutableSetOf<Long>()
    private var selectionMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_personal, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById<RecyclerView>(R.id.recyclerTasks).apply {
            layoutManager = LinearLayoutManager(requireContext())
        }

        adapter = ToDoAdapter(
            onCheckedChanged = ::handleCheckChanged,
            onRowClicked = ::handleRowClick,
            onRowMenuClicked = ::showItemMenu,
            onSelectionToggle = { toggleSelection(it.id) },
            onLongPressForSelection = { startSelectionMode(it.id) },
        )
        recyclerView?.adapter = adapter

        view.findViewById<View>(R.id.createTask).setOnClickListener {
            findNavController().navigate(R.id.action_personal_to_task)
        }

        setupSearch(view)
        setupTabsAndFilters(view)
        observeTasks()
    }

    private fun setupSearch(root: View) {
        val header = root.findViewById<ViewGroup>(R.id.header)
        val searchContainer = root.findViewById<View>(R.id.searchInput)
        val searchField = root.findViewById<EditText>(R.id.etSearch)
        val ivSearch = root.findViewById<ImageView>(R.id.ivSearch)

        ivSearch.setOnClickListener {
            searchContainer.isVisible = !searchContainer.isVisible
            if (!searchContainer.isVisible) {
                searchField.setText("")
                queryText = ""
                refreshUi()
            } else {
                searchField.requestFocus()
            }
        }

        searchField.addTextChangedListener {
            queryText = it?.toString().orEmpty()
            refreshUi()
        }
    }

    private fun setupTabsAndFilters(root: View) {
        val tabAll = root.findViewById<TextView>(R.id.tab_all)
        val tabTodo = root.findViewById<TextView>(R.id.tab_todo)
        val tabProgress = root.findViewById<TextView>(R.id.tab_progress)
        val tabDone = root.findViewById<TextView>(R.id.tab_done)

        fun setActive(tv: TextView, active: Boolean) {
            tv.setBackgroundResource(if (active) R.drawable.tab_active else R.drawable.tab_inactive)
            tv.setTextColor(resources.getColor(if (active) R.color.white else android.R.color.black, null))
        }

        fun updateTabs() {
            setActive(tabAll, selectedTab == StatusTab.ALL)
            setActive(tabTodo, selectedTab == StatusTab.TODO)
            setActive(tabProgress, selectedTab == StatusTab.IN_PROGRESS)
            setActive(tabDone, selectedTab == StatusTab.DONE)
        }

        tabAll.setOnClickListener { selectedTab = StatusTab.ALL; updateTabs(); refreshUi() }
        tabTodo.setOnClickListener { selectedTab = StatusTab.TODO; updateTabs(); refreshUi() }
        tabProgress.setOnClickListener { selectedTab = StatusTab.IN_PROGRESS; updateTabs(); refreshUi() }
        tabDone.setOnClickListener { selectedTab = StatusTab.DONE; updateTabs(); refreshUi() }

        updateTabs()

        val filterDate = root.findViewById<TextView>(R.id.filterDate)
        val filterPriority = root.findViewById<TextView>(R.id.filterPriority)
        val filterTag = root.findViewById<TextView>(R.id.filterTag)

        fun updateFiltersUi() {
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

        fun showDateFilter() {
            val labels = arrayOf("Any", "Today", "Tomorrow", "This week", "No deadline")
            val values = arrayOf(DateFilter.ANY, DateFilter.TODAY, DateFilter.TOMORROW, DateFilter.THIS_WEEK, DateFilter.NO_DEADLINE)
            val checked = values.indexOf(selectedDateFilter).coerceAtLeast(0)

            AlertDialog.Builder(requireContext())
                .setTitle("Filter by date")
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    selectedDateFilter = values[which]
                    dialog.dismiss()
                    updateFiltersUi()
                    refreshUi()
                }
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear") { _, _ ->
                    selectedDateFilter = DateFilter.ANY
                    updateFiltersUi()
                    refreshUi()
                }
                .show()
        }

        fun showPriorityFilter() {
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
                        2 -> PriorityLevel.MEDIUM
                        3 -> PriorityLevel.HIGH
                        else -> null
                    }
                    dialog.dismiss()
                    updateFiltersUi()
                    refreshUi()
                }
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear") { _, _ ->
                    selectedPriorityFilter = null
                    updateFiltersUi()
                    refreshUi()
                }
                .show()
        }

        fun showTagFilter() {
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
                return
            }

            val labels = arrayOf("Any") + tags.toTypedArray()
            val checked = labels.indexOfFirst { it.equals(selectedTagFilter, ignoreCase = true) }.takeIf { it >= 0 } ?: 0

            AlertDialog.Builder(requireContext())
                .setTitle("Filter by tag")
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    selectedTagFilter = if (which == 0) null else labels[which]
                    dialog.dismiss()
                    updateFiltersUi()
                    refreshUi()
                }
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear") { _, _ ->
                    selectedTagFilter = null
                    updateFiltersUi()
                    refreshUi()
                }
                .show()
        }

        filterDate.setOnClickListener { showDateFilter() }
        filterPriority.setOnClickListener { showPriorityFilter() }
        filterTag.setOnClickListener { showTagFilter() }

        updateFiltersUi()

        root.findViewById<ImageView>(R.id.ivMore).setOnClickListener { anchor ->
            PopupMenu(requireContext(), anchor).apply {
                MenuInflater(requireContext()).inflate(R.menu.personal_frag_menu, menu)
                setOnMenuItemClickListener { handleMenuItem(it) }
            }.show()
        }
    }

    private fun observeTasks() {
        val allItemsFlow: Flow<List<ToDoItem>> = repository.observeAll()
            .map { list -> list.map { it.toUiItem() } }
            .distinctUntilChanged()

        viewLifecycleOwner.lifecycleScope.launch {
            allItemsFlow.collect { items ->
                latestAllItems = items
                refreshUi()
            }
        }
    }

    private fun applyFilters(items: List<ToDoItem>): List<ToDoItem> {
        return items.asSequence()
            .filter(::statusMatches)
            .filter(::dateMatches)
            .filter(::priorityMatches)
            .filter(::tagMatches)
            .filter(::searchMatches)
            .toList()
    }

    private fun refreshUi() {
        val filtered = applyFilters(latestAllItems)
        adapter?.submitData(filtered, selectedIds, selectionMode)
    }

    private fun statusMatches(item: ToDoItem): Boolean {
        return when (selectedTab) {
            StatusTab.ALL -> true
            StatusTab.TODO -> item.status == TaskStatus.TODO
            StatusTab.IN_PROGRESS -> item.status == TaskStatus.IN_PROGRESS
            StatusTab.DONE -> item.status == TaskStatus.DONE
        }
    }

    private fun dateMatches(item: ToDoItem): Boolean {
        val deadline = item.deadlineAt ?: return selectedDateFilter == DateFilter.ANY || selectedDateFilter == DateFilter.NO_DEADLINE
        val calDeadline = Calendar.getInstance().apply { timeInMillis = deadline }
        val now = Calendar.getInstance()

        fun Calendar.sameDay(other: Calendar): Boolean {
            return get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
                get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
        }

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val endOfWeek = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            add(Calendar.WEEK_OF_YEAR, 1)
        }

        return when (selectedDateFilter) {
            DateFilter.ANY -> true
            DateFilter.TODAY -> calDeadline.sameDay(now)
            DateFilter.TOMORROW -> calDeadline.sameDay(tomorrow)
            DateFilter.THIS_WEEK -> deadline in (now.timeInMillis + 1)..endOfWeek.timeInMillis
            DateFilter.NO_DEADLINE -> false
        }
    }

    private fun priorityMatches(item: ToDoItem): Boolean {
        val filter = selectedPriorityFilter ?: return true
        return item.priority == filter
    }

    private fun tagMatches(item: ToDoItem): Boolean {
        val filter = selectedTagFilter?.trim().orEmpty()
        if (filter.isBlank()) return true
        return item.tags.any { it.equals(filter, ignoreCase = true) }
    }

    private fun searchMatches(item: ToDoItem): Boolean {
        val query = queryText.trim().lowercase()
        if (query.isBlank()) return true
        return item.title.lowercase().contains(query) ||
            item.tags.any { it.lowercase().contains(query) }
    }

    private fun handleRowClick(anchor: View, item: ToDoItem) {
        if (selectionMode) {
            toggleSelection(item.id)
        } else {
            showItemMenu(anchor, item)
        }
    }

    private fun handleCheckChanged(item: ToDoItem, isChecked: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newStatus = if (isChecked) TaskStatus.DONE.name else TaskStatus.TODO.name
            repository.updateStatus(item.id, newStatus)
        }
    }

    private fun showItemMenu(anchor: View, item: ToDoItem) {
        PopupMenu(requireContext(), anchor).apply {
            MenuInflater(requireContext()).inflate(R.menu.personal_frag_menu, menu)
            menu.add(0, R.id.personal_delete, 0, "Delete")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.personal_delete -> {
                        confirmDeletion(listOf(item.id))
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    private fun startSelectionMode(initialId: Long) {
        selectionMode = true
        selectedIds.clear()
        selectedIds.add(initialId)
        refreshUi()
    }

    private fun toggleSelection(taskId: Long) {
        if (!selectionMode) return
        if (!selectedIds.add(taskId)) selectedIds.remove(taskId)
        if (selectedIds.isEmpty()) selectionMode = false
        refreshUi()
    }

    private fun confirmDeletion(ids: List<Long>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete tasks")
            .setMessage("Delete ${ids.size} task(s)?")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.deleteByIds(ids)
                    selectionMode = false
                    selectedIds.clear()
                    observeTasks()
                    Snackbar.make(requireView(), "Deleted", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                selectionMode = false
                selectedIds.clear()
                refreshUi()
            }
            .show()
    }

    private fun handleMenuItem(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.personal_sync -> {
                Snackbar.make(requireView(), "Sync coming soon", Snackbar.LENGTH_SHORT).show()
                true
            }
            R.id.personal_delete -> {
                if (selectedIds.isEmpty()) {
                    Toast.makeText(requireContext(), "Long press a task to select", Toast.LENGTH_SHORT).show()
                } else {
                    confirmDeletion(selectedIds.toList())
                }
                true
            }
            else -> false
        }
    }
}