package com.example.morp_prj.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.TaskRepository
import com.example.morp_prj.data.TaskUiMapper.toUiItem
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var toDoAdapter: ToDoAdapter

    // Overview TextViews
    private lateinit var tvTodoCount: TextView
    private lateinit var tvInProgressCount: TextView
    private lateinit var tvCompletedCount: TextView

    // Search
    private lateinit var etSearch: EditText
    private var queryText: String = ""
    private var latestAllItems: List<ToDoItem> = emptyList()

    // 1. Khởi tạo Repository để tương tác với Database
    private val repository by lazy {
        TaskRepository(AppDatabase.getInstance(requireContext()).taskDao())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Lấy thông tin user hiện tại
        val user = PreferenceManager.getUser(requireContext())

        // 2. Hiển thị lời chào (Giả sử id là tv_greeting hoặc tv_username trong layout)
        if (user != null) {
            // Thay "tvGreeting" bằng ID thật trong file xml của bạn (ví dụ: binding.tvUsername.text)
            // binding.tvGreeting.text = "Hi, ${user.displayName}" // Sử dụng displayName thay cho fullName
            Log.d("HomeFragment", "User logged in: ${user.displayName}")
        } else {
            Log.e("HomeFragment", "No user found in Preferences")
        }
        setupRecyclerView(view)
        setupOverviewViews(view)
        setupSearch(view)
        loadTasksFromDatabase() // Thực hiện load dữ liệu từ Database
        loadOverviewCounts() // Load số lượng task theo status
        setupListeners(view)
    }

    private fun setupSearch(root: View) {
        etSearch = root.findViewById(R.id.etSearch)

        etSearch.addTextChangedListener {
            queryText = it?.toString().orEmpty()
            refreshUi()
        }
    }

    private fun setupOverviewViews(root: View) {
        tvTodoCount = root.findViewById(R.id.tvTodoCount)
        tvInProgressCount = root.findViewById(R.id.tvInProgressCount)
        tvCompletedCount = root.findViewById(R.id.tvCompletedCount)
    }

    private fun loadOverviewCounts() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Observe TODO count
            launch {
                repository.observeCountByStatus("TODO")
                    .distinctUntilChanged()
                    .collect { count ->
                        tvTodoCount.text = "$count Tasks"
                    }
            }

            // Observe IN_PROGRESS count
            launch {
                repository.observeCountByStatus("IN_PROGRESS")
                    .distinctUntilChanged()
                    .collect { count ->
                        tvInProgressCount.text = "$count Tasks"
                    }
            }

            // Observe DONE count
            launch {
                repository.observeCountByStatus("DONE")
                    .distinctUntilChanged()
                    .collect { count ->
                        tvCompletedCount.text = "$count Tasks"
                    }
            }
        }
    }

    private fun setupRecyclerView(root: View) {
        toDoAdapter = ToDoAdapter(
            onCheckedChanged = { todo, isChecked ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val newStatus = if (isChecked) "DONE" else "TODO"
                    repository.updateStatus(todo.id, newStatus)
                }
            },
            onRowClicked = { anchor, item ->
                Toast.makeText(context, "Clicked: ${item.title}", Toast.LENGTH_SHORT).show()
            }
        )

        val rvTasks = root.findViewById<RecyclerView>(R.id.rvTasks)
        rvTasks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = toDoAdapter
        }
    }

    private fun loadTasksFromDatabase() {
        // Lắng nghe sự thay đổi dữ liệu từ Database theo thời gian thực (Real-time)
        // Dữ liệu tạo ở PersonalFragment sẽ tự động hiển thị ở đây
        viewLifecycleOwner.lifecycleScope.launch {
            repository.observeAll()
                .map { list ->
                    Log.d("HomeFragment", "Loaded ${list.size} tasks from database")
                    list.map { it.toUiItem() }
                }
                .collect { tasks ->
                    Log.d("HomeFragment", "Mapped ${tasks.size} tasks to UI items")
                    latestAllItems = tasks
                    refreshUi()
                }
        }
    }

    private fun refreshUi() {
        val filtered = applyFilters(latestAllItems)
        Log.d("HomeFragment", "Displaying ${filtered.size} tasks after filter (query='$queryText')")
        toDoAdapter.submitList(filtered)
    }

    private fun applyFilters(items: List<ToDoItem>): List<ToDoItem> {
        return items.filter { searchMatches(it) }
    }

    private fun searchMatches(item: ToDoItem): Boolean {
        val query = queryText.trim().lowercase()
        if (query.isBlank()) return true
        return item.title.lowercase().contains(query) ||
            item.tags.any { it.lowercase().contains(query) }
    }

    private fun setupListeners(root: View) {
        val ivNotification = root.findViewById<ImageView>(R.id.ivNotification)
        val tvSeeAll = root.findViewById<TextView>(R.id.tvSeeAll)

        ivNotification.setOnClickListener {
            Toast.makeText(context, "Notifications clicked", Toast.LENGTH_SHORT).show()
        }
        tvSeeAll.setOnClickListener {
            Toast.makeText(context, "See All clicked", Toast.LENGTH_SHORT).show()
        }
    }
}