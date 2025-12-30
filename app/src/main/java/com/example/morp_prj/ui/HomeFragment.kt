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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.morp_prj.R
import com.example.morp_prj.data.TaskRepository
import com.example.morp_prj.data.TaskUiMapper.toUiItem
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeFragment : Fragment() {
    companion object {
        private const val GUEST_USER_ID = PreferenceManager.GUEST_USER_ID
    }

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

    private val prefs by lazy { PreferenceManager(requireContext()) }

    // Current user ID for filtering tasks (guest uses deterministic local id)
    private val currentUserId: String
        get() = if (prefs.isGuest()) GUEST_USER_ID else prefs.getUserId() ?: GUEST_USER_ID

    private lateinit var tvWelcome: TextView
    private lateinit var ivAvatar: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvWelcome = view.findViewById(R.id.tvWelcome)
        ivAvatar = view.findViewById(R.id.ivAvatar)
        updateGreeting()

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

    override fun onResume() {
        super.onResume()
        updateGreeting()
    }

    private fun updateGreeting() {
        val user = PreferenceManager.getUser(requireContext())
        val name = when {
            prefs.isGuest() -> getString(R.string.guest_user_name)
            user?.displayName?.isNotBlank() == true -> user.displayName
            user?.username?.isNotBlank() == true -> user.username
            user?.email?.isNotBlank() == true -> user.email
            else -> getString(R.string.default_user_name)
        }
        tvWelcome.text = getString(R.string.home_greeting_format, name)

        // Load avatar from server or use default
        if (user != null && !user.avatarUrl.isNullOrEmpty()) {
            // Build full URL for server avatar
            val baseUrl = "http://10.0.2.2:3001" // Android emulator localhost
            val fullUrl = if (user.avatarUrl.startsWith("http")) {
                user.avatarUrl
            } else {
                "$baseUrl${user.avatarUrl}"
            }

            Glide.with(this)
                .load(fullUrl)
                .placeholder(R.drawable.img_1) // Default avatar while loading
                .error(R.drawable.img_1) // Default avatar if load fails
                .circleCrop() // Make it circular
                .into(ivAvatar)
        } else {
            // No avatar URL, use default image
            Glide.with(this)
                .load(R.drawable.img_1)
                .circleCrop()
                .into(ivAvatar)
        }
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
        val userId = currentUserId
        viewLifecycleOwner.lifecycleScope.launch {
            // Observe TODO count for current user
            launch {
                repository.observeCountByStatusForUser(userId, "TODO")
                    .distinctUntilChanged()
                    .collect { count ->
                        tvTodoCount.text = resources.getQuantityString(R.plurals.tasks_count, count, count)
                    }
            }

            // Observe IN_PROGRESS count for current user
            launch {
                repository.observeCountByStatusForUser(userId, "IN_PROGRESS")
                    .distinctUntilChanged()
                    .collect { count ->
                        tvInProgressCount.text = resources.getQuantityString(R.plurals.tasks_count, count, count)
                    }
            }

            // Observe DONE count for current user
            launch {
                repository.observeCountByStatusForUser(userId, "DONE")
                    .distinctUntilChanged()
                    .collect { count ->
                        tvCompletedCount.text = resources.getQuantityString(R.plurals.tasks_count, count, count)
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
            onRowClicked = { _, item ->
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
        val userId = currentUserId


        // Get today's start and end time
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        // Load tasks with deadline today for current user
        viewLifecycleOwner.lifecycleScope.launch {
            repository.observeByDateRangeForUser(userId, startOfDay, endOfDay)
                .map { list ->
                    Log.d("HomeFragment", "Loaded ${list.size} today's tasks for user $userId")
                    list.map { it.toUiItem() }
                }
                .collect { tasks ->
                    Log.d("HomeFragment", "Mapped ${tasks.size} today's tasks to UI items")
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
        val tvViewDashboard = root.findViewById<TextView>(R.id.tvViewDashboard)

        ivNotification.setOnClickListener {
            Toast.makeText(context, "Notifications clicked", Toast.LENGTH_SHORT).show()
        }
        tvSeeAll.setOnClickListener {
            Toast.makeText(context, "See All clicked", Toast.LENGTH_SHORT).show()
        }
        tvViewDashboard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_dashboardDetail)
        }
    }
}