package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.TaskRepository
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardDetailFragment : Fragment() {

    companion object {
        private const val GUEST_USER_ID = PreferenceManager.GUEST_USER_ID
    }

    // Stats TextViews
    private lateinit var tvTotalCount: TextView
    private lateinit var tvTodoCount: TextView
    private lateinit var tvInProgressCount: TextView
    private lateinit var tvCompletedCount: TextView

    // Progress
    private lateinit var tvProgressPercent: TextView
    private lateinit var progressBar: ProgressBar

    // Today's summary
    private lateinit var tvTodayTasksCount: TextView
    private lateinit var tvTodayCompletedCount: TextView
    private lateinit var tvTodayPendingCount: TextView

    private val repository by lazy {
        TaskRepository(AppDatabase.getInstance(requireContext()).taskDao())
    }

    private val prefs by lazy { PreferenceManager(requireContext()) }

    private val currentUserId: String
        get() = if (prefs.isGuest()) GUEST_USER_ID else prefs.getUserId() ?: GUEST_USER_ID

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupBackButton(view)
        loadDashboardData()
    }

    private fun setupViews(view: View) {
        // Stats cards
        tvTotalCount = view.findViewById(R.id.tvTotalCount)
        tvTodoCount = view.findViewById(R.id.tvTodoCount)
        tvInProgressCount = view.findViewById(R.id.tvInProgressCount)
        tvCompletedCount = view.findViewById(R.id.tvCompletedCount)

        // Progress
        tvProgressPercent = view.findViewById(R.id.tvProgressPercent)
        progressBar = view.findViewById(R.id.progressBar)

        // Today's summary
        tvTodayTasksCount = view.findViewById(R.id.tvTodayTasksCount)
        tvTodayCompletedCount = view.findViewById(R.id.tvTodayCompletedCount)
        tvTodayPendingCount = view.findViewById(R.id.tvTodayPendingCount)
    }

    private fun setupBackButton(view: View) {
        val ivBack = view.findViewById<ImageView>(R.id.ivBack)
        ivBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun loadDashboardData() {
        val userId = currentUserId

        viewLifecycleOwner.lifecycleScope.launch {
            // === ✅ NEW UNIFIED BUSINESS LOGIC ===
            // Observe toàn bộ task list để tính toán chính xác
            launch {
                repository.observeAllByUser(userId)
                    .distinctUntilChanged()
                    .collect { tasks ->
                        val now = System.currentTimeMillis()

                        // Tính toán theo logic thống nhất: Time + Status
                        val total = tasks.size
                        val done = tasks.count { it.status.equals("DONE", ignoreCase = true) }

                        // In Progress: Chưa DONE và còn deadline trong tương lai
                        val inProgress = tasks.count {
                            !it.status.equals("DONE", ignoreCase = true) &&
                            it.deadlineAt != null &&
                            it.deadlineAt!! > now
                        }

                        // Overdue: Chưa DONE và deadline đã quá hạn
                        // Note: Biến overdue được tính toán để sẵn sàng cho tương lai
                        // Có thể hiển thị trong UI bằng cách thêm TextView tương ứng
                        val overdue = tasks.count {
                            !it.status.equals("DONE", ignoreCase = true) &&
                            it.deadlineAt != null &&
                            it.deadlineAt!! < now
                        }

                        // TODO: Các task còn lại (hoặc có thể tính riêng nếu cần)
                        val todo = tasks.count { it.status.equals("TODO", ignoreCase = true) }

                        // Cập nhật UI với số liệu đã tính
                        tvTotalCount.text = total.toString()
                        tvTodoCount.text = todo.toString()
                        tvInProgressCount.text = inProgress.toString()
                        tvCompletedCount.text = done.toString()

                        // Tính toán phần trăm hoàn thành
                        val progressPercent = if (total > 0) (done * 100 / total) else 0
                        tvProgressPercent.text = "$progressPercent%"
                        progressBar.progress = progressPercent

                        // Log overdue count for monitoring (có thể bỏ nếu không cần)
                        android.util.Log.d("DashboardDetail", "Stats: Total=$total, Done=$done, InProgress=$inProgress, Overdue=$overdue")
                    }
            }

            // Today's summary - giữ nguyên logic
            launch {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis

                calendar.add(Calendar.DAY_OF_MONTH, 1)
                val endOfDay = calendar.timeInMillis

                repository.observeByDateRangeForUser(userId, startOfDay, endOfDay)
                    .distinctUntilChanged()
                    .collect { tasks ->
                        val todayTotal = tasks.size
                        val todayCompleted = tasks.count { it.status.equals("DONE", ignoreCase = true) }
                        val todayPending = todayTotal - todayCompleted

                        tvTodayTasksCount.text = todayTotal.toString()
                        tvTodayCompletedCount.text = todayCompleted.toString()
                        tvTodayPendingCount.text = todayPending.toString()
                    }
            }
        }
    }
}
