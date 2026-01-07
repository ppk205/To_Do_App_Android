package com.example.morp_prj.ui

import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.example.morp_prj.data.repository.AuthRepository
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeFragment : Fragment() {
    companion object {
        private const val GUEST_USER_ID = PreferenceManager.GUEST_USER_ID

        /**
         * REQ-HOME-02: Sanitize text để ngăn XSS
         * Loại bỏ các ký tự đặc biệt HTML có thể gây XSS
         */
        private fun sanitizeForDisplay(text: String?): String {
            if (text.isNullOrBlank()) return ""
            // Escape các ký tự HTML đặc biệt
            return TextUtils.htmlEncode(text)
        }
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

    private lateinit var tvGreeting: TextView
    private lateinit var ivUserAvatar: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Chặn back press - không cho người dùng quay lại màn hình trước login
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Không làm gì - chặn back press hoàn toàn
                // Hoặc có thể thoát app nếu muốn:
                // requireActivity().finish()
            }
        })

        tvGreeting = view.findViewById(R.id.tvGreeting)
        ivUserAvatar = view.findViewById(R.id.ivUserAvatar)
        updateGreeting()

        // Fetch fresh profile từ server để đảm bảo avatar được load ngay sau đăng nhập
        if (!prefs.isGuest()) {
            fetchProfileFromServer()
        }

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
        // Fetch fresh profile data from server khi quay lại màn hình
        // Điều này đảm bảo avatar luôn được cập nhật mới nhất
        if (!prefs.isGuest()) {
            fetchProfileFromServer()
        }
    }

    /**
     * Fetch profile từ server để đảm bảo dữ liệu luôn mới nhất
     * Đặc biệt quan trọng sau khi đăng nhập vì cache có thể chưa có avatarUrl
     */
    private fun fetchProfileFromServer() {
        val authRepo = AuthRepository(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepo.fetchProfile()

            result.onSuccess { user ->
                // Lưu vào cache cục bộ
                PreferenceManager.saveUser(requireContext(), user)
                // Cập nhật lại UI với dữ liệu mới
                updateGreetingWithUser(user)
            }.onFailure { error ->
                // Fail silently - tiếp tục sử dụng dữ liệu từ cache
                Log.w("HomeFragment", "Không thể fetch profile từ server: ${error.message}")
            }
        }
    }

    private fun updateGreeting() {
        val user = PreferenceManager.getUser(requireContext())
        updateGreetingWithUser(user)
    }

    /**
     * Cập nhật UI greeting và avatar với dữ liệu user được truyền vào
     * REQ-HOME-02: Sanitize name để ngăn XSS
     */
    private fun updateGreetingWithUser(user: com.example.morp_prj.data.model.User?) {
        // Xử lý tên hiển thị - REQ-HOME-02: Sanitize để ngăn XSS
        val rawName = when {
            prefs.isGuest() -> "Guest"
            user?.displayName?.isNotBlank() == true -> user.displayName
            user?.username?.isNotBlank() == true -> user.username
            user?.email?.isNotBlank() == true -> user.email
            else -> "User"
        }

        // REQ-HOME-02: Sanitize text trước khi hiển thị để ngăn Stored XSS
        val safeName = sanitizeForDisplay(rawName)
        tvGreeting.text = "Hi, $safeName"

        // Load avatar using RetrofitClient for consistent URL handling
        if (user != null && !user.avatarUrl.isNullOrEmpty()) {
            val fullUrl = RetrofitClient.buildFullUrl(user.avatarUrl) ?: user.avatarUrl

            Log.d("HomeFragment", "Loading avatar from URL: $fullUrl")

            Glide.with(this)
                .load(fullUrl)
                .placeholder(R.drawable.img_1) // Default avatar while loading
                .error(R.drawable.img_1) // Default avatar if load fails
                .circleCrop() // Make it circular
                .into(ivUserAvatar)
        } else {
            // Không có avatar URL, sử dụng ảnh mặc định
            Glide.with(this)
                .load(R.drawable.img_1)
                .circleCrop()
                .into(ivUserAvatar)
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
            // === ✅ NEW UNIFIED BUSINESS LOGIC ===
            // Observe toàn bộ task list để tính toán chính xác theo logic thống nhất
            repository.observeAllByUser(userId)
                .distinctUntilChanged()
                .collect { tasks ->
                    val now = System.currentTimeMillis()

                    // TODO count - task có status TODO
                    val todoCount = tasks.count { it.status.equals("TODO", ignoreCase = true) }
                    tvTodoCount.text = resources.getQuantityString(R.plurals.tasks_count, todoCount, todoCount)

                    // IN_PROGRESS count - Task chưa DONE và còn deadline trong tương lai
                    val inProgressCount = tasks.count {
                        !it.status.equals("DONE", ignoreCase = true) &&
                        it.deadlineAt != null &&
                        it.deadlineAt!! > now
                    }
                    tvInProgressCount.text = resources.getQuantityString(R.plurals.tasks_count, inProgressCount, inProgressCount)

                    // DONE count - task có status DONE
                    val doneCount = tasks.count { it.status.equals("DONE", ignoreCase = true) }
                    tvCompletedCount.text = resources.getQuantityString(R.plurals.tasks_count, doneCount, doneCount)
                }
        }
    }

    private fun setupRecyclerView(root: View) {
        toDoAdapter = ToDoAdapter(
            onCheckedChanged = { todo, isChecked ->
                viewLifecycleOwner.lifecycleScope.launch {
                    // Khi check → DONE, khi uncheck → TODO (mặc định)
                    // Lưu ý: Không thể khôi phục IN_PROGRESS vì không lưu trạng thái trước đó
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

        // === ✅ TODAY'S TASKS LOGIC ===
        // Tính toán khoảng thời gian của ngày hôm nay (từ 00:00:00 đến 23:59:59)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        Log.d("HomeFragment", "Loading Today's Tasks for user $userId (deadline between $startOfDay and $endOfDay)")

        // Load tasks có deadline trong ngày hôm nay
        viewLifecycleOwner.lifecycleScope.launch {
            repository.observeByDateRangeForUser(userId, startOfDay, endOfDay)
                .map { list ->
                    Log.d("HomeFragment", "Loaded ${list.size} today's tasks from database")
                    list.map { it.toUiItem() }
                }
                .collect { tasks ->
                    Log.d("HomeFragment", "Displaying ${tasks.size} today's tasks")
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
        val tvViewDashboard = root.findViewById<TextView>(R.id.tvViewDashboard)
        tvViewDashboard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_dashboardDetail)
        }
    }
}