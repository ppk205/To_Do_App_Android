package com.example.morp_prj.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask
import com.example.morp_prj.utils.PreferenceManager
import java.util.Calendar

class AssignedTeamTasksFragment : Fragment() {

    private val viewModel: TeamTaskViewModel by activityViewModels()
    private lateinit var adapter: AssignedTeamTasksAdapter

    // Lưu danh sách gốc của user (sau khi lọc theo userId)
    private var myTasks: List<TeamTask> = emptyList()

    // Filter status: "OVERDUE", "TODAY", "UPCOMING", "COMPLETED", hoặc null (ALL)
    private var currentFilter: String? = null

    // Map lưu reference đến các CardView để đổi màu
    private var cardMap: Map<String, View>? = null

    // remember previous status for tasks when toggling to DONE so unchecking restores it
    private val previousStatusMap = mutableMapOf<String, String>()

    // current teamId context (used to refetch authoritative list after updates)
    private var currentTeamId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_assigned_team_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvTasks = view.findViewById<RecyclerView>(R.id.rvTasks)
        adapter = AssignedTeamTasksAdapter(
            emptyList(),
            { task: TeamTask, itemView: View ->
                 // Use a ripple/elevation pulse for feedback and then navigate
                 val originalElevation = itemView.elevation
                 itemView.elevation = originalElevation + 8f
                 itemView.postDelayed({
                     itemView.elevation = originalElevation
                     val bundle = Bundle().apply { putString("taskId", task.id) }
                     val navOptions = androidx.navigation.navOptions {
                         anim {
                             enter = R.anim.slide_in_right
                             exit = R.anim.slide_out_left
                             popEnter = R.anim.fade_in
                             popExit = R.anim.fade_out
                         }
                     }
                     findNavController().navigate(R.id.teamTaskDetailFragment, bundle, navOptions)
                 }, 140)
            }, { task: TeamTask, isChecked: Boolean ->
                 // Determine the current status from our local snapshot (myTasks) before making changes
                 val currentStatus = myTasks.firstOrNull { it.id == task.id }?.status ?: task.status

                 // When checking -> mark DONE and remember previous status
                 // When unchecking -> restore previous status if known, otherwise fallback to currentStatus or OVERDUE or TODO
                 val newStatus: String
                 if (isChecked) {
                     previousStatusMap[task.id] = currentStatus
                     newStatus = "DONE"
                 } else {
                     val now = System.currentTimeMillis()
                     val isOverDue = task.dueDate != null && task.dueDate < now

                     newStatus = when {
                         isOverDue -> "OVERDUE"
                         else -> previousStatusMap.remove(task.id) ?: "TODO"
                     }
                 }

                 // Use unified helper for optimistic update + UI feedback
                 val oldTasks = myTasks
                 updateTaskStatusWithUi(task, newStatus, oldTasks)
            }, { task: TeamTask, status: String ->
                 // status changed from popup -> reuse same helper
                 val oldTasks = myTasks
                 updateTaskStatusWithUi(task, status, oldTasks)
            }
        )
        rvTasks.layoutManager = LinearLayoutManager(context)
        rvTasks.adapter = adapter

        // Setup click listeners for Summary Cards
        setupCardListeners(view)

        val teamId = arguments?.getString("teamId") ?: ""
        currentTeamId = if (teamId.isNotEmpty()) teamId else null

        observeViewModel(view)

        if (currentTeamId != null && viewModel.tasks.value.isNullOrEmpty()) {
            viewModel.fetchTasks(currentTeamId!!)
        }
    }

    private fun setupCardListeners(view: View) {
        val cardOverdue = view.findViewById<View>(R.id.cardOverdue)
        val cardToday = view.findViewById<View>(R.id.cardToday)
        val cardUpcoming = view.findViewById<View>(R.id.cardUpcoming)
        val cardCompleted = view.findViewById<View>(R.id.cardCompleted)

        cardMap = mapOf(
            "OVERDUE" to cardOverdue,
            "TODAY" to cardToday,
            "UPCOMING" to cardUpcoming,
            "COMPLETED" to cardCompleted
        )

        cardOverdue.setOnClickListener { toggleFilter("OVERDUE") }
        cardToday.setOnClickListener { toggleFilter("TODAY") }
        cardUpcoming.setOnClickListener { toggleFilter("UPCOMING") }
        cardCompleted.setOnClickListener { toggleFilter("COMPLETED") }
    }

    private fun toggleFilter(filterType: String) {
        if (currentFilter == filterType) {
            // Click lại thẻ đang chọn -> Bỏ chọn (Hiện tất cả)
            currentFilter = null
        } else {
            // Click thẻ mới -> Chọn filter đó
            currentFilter = filterType
        }

        updateCardVisuals()
        applyFilter()
    }

    private fun updateCardVisuals() {
        // Reset tất cả về màu mặc định (Trắng hoặc màu gốc)
        cardMap?.values?.forEach { view ->
            if (view is CardView) {
                view.setCardBackgroundColor(Color.WHITE)
            } else {
                view.setBackgroundColor(Color.WHITE)
            }
        }

        currentFilter?.let { filter ->
            val selectedCard = cardMap?.get(filter)
            if (selectedCard is CardView) {
                selectedCard.setCardBackgroundColor(Color.parseColor("#E3F2FD"))
            } else {
                selectedCard?.setBackgroundColor(Color.LTGRAY)
            }
        }

        currentFilter?.let { filter ->
            val selectedCard = cardMap?.get(filter)
            if (selectedCard is CardView) {
                selectedCard.setCardBackgroundColor(Color.parseColor("#E3F2FD")) // Light Blue tint
            }
        }
    }

    private fun applyFilter() {
        if (myTasks.isEmpty()) {
            adapter.submitList(emptyList())
            return
        }

        val now = System.currentTimeMillis()
        val filteredList = if (currentFilter == null) {
            myTasks
        } else {
            myTasks.filter { task ->
                val isDone = task.status.uppercase() == "DONE" || task.status.uppercase() == "COMPLETED"
                when (currentFilter) {
                    "COMPLETED" -> isDone
                    "OVERDUE" -> !isDone && (task.status.uppercase() == "OVERDUE" || (task.dueDate != null && task.dueDate < now))
                    "TODAY" -> !isDone && !(task.status.uppercase() == "OVERDUE" || (task.dueDate != null && task.dueDate < now)) && (task.dueDate != null && isToday(task.dueDate))
                    "UPCOMING" -> !isDone && !(task.status.uppercase() == "OVERDUE" || (task.dueDate != null && task.dueDate < now)) && !(task.dueDate != null && isToday(task.dueDate)) && (task.dueDate != null && task.dueDate > now)
                    else -> true
                }
            }
        }
        adapter.submitList(filteredList)
    }

    private fun observeViewModel(view: View) {
        viewModel.tasks.observe(viewLifecycleOwner) { taskList ->
            // get current user's tasks
            val userId = PreferenceManager(requireContext()).getUserId()
            myTasks = if (userId != null) {
                taskList.filter { task ->
                    task.assignees.any { it.id == userId }
                }
            } else {
                emptyList()
            }

            // Merge pending optimistic statuses from ViewModel so UI remains consistent
            myTasks = myTasks.map { t ->
                val pending = viewModel.getPendingStatus(t.id)
                if (!pending.isNullOrEmpty() && pending != t.status) t.copy(status = pending) else t
            }

            // Remove any previousStatus entries that no longer correspond to existing tasks
            previousStatusMap.keys.retainAll(myTasks.map { it.id })

            // luôn tính trên toàn bộ tasks của user
            calculateMySummary(view, myTasks)

            // Hiển thị danh sách (có áp dụng filter nếu đang chọn)
            applyFilter()

            // Reflect pending update states in the adapter so checkboxes are disabled for inflight changes
            myTasks.forEach { t ->
                adapter.setLoading(t.id, viewModel.isPending(t.id))
            }
        }
    }

    private fun calculateMySummary(view: View, tasks: List<TeamTask>) {
        val now = System.currentTimeMillis()
        var overdue = 0
        var today = 0
        var upcoming = 0
        var completed = 0

        tasks.forEach { task ->
            when {
                task.status.uppercase() == "DONE" || task.status.uppercase() == "COMPLETED" -> completed++
                task.status.uppercase() == "OVERDUE" || (task.dueDate != null && task.dueDate < now) -> overdue++
                task.dueDate != null && isToday(task.dueDate) -> today++
                task.dueDate != null && task.dueDate > now -> upcoming++
            }
        }

        updateSummaryCard(view, R.id.cardOverdue, "Overdue", overdue)
        updateSummaryCard(view, R.id.cardToday, "Today", today)
        updateSummaryCard(view, R.id.cardUpcoming, "Upcoming", upcoming)
        updateSummaryCard(view, R.id.cardCompleted, "Completed", completed)
    }

    private fun isToday(timestamp: Long): Boolean {
        val taskDate = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayDate = Calendar.getInstance()
        return taskDate.get(Calendar.YEAR) == todayDate.get(Calendar.YEAR) &&
                taskDate.get(Calendar.DAY_OF_YEAR) == todayDate.get(Calendar.DAY_OF_YEAR)
    }

    private fun updateSummaryCard(rootView: View, cardId: Int, label: String, count: Int) {
        val card = rootView.findViewById<View>(cardId)
        if (card != null) {
            card.findViewById<TextView>(R.id.tvSummaryLabel).text = label
            card.findViewById<TextView>(R.id.tvSummaryCount).text = count.toString()
        }
    }

    // Unified helper that performs optimistic UI update, shows loading state, calls ViewModel, and handles success/failure UI flows
    private fun updateTaskStatusWithUi(task: TeamTask, targetStatus: String, previousTasksSnapshot: List<TeamTask>) {
        // mark loading
        adapter.setLoading(task.id, true)

        // optimistic update
        myTasks = myTasks.map { if (it.id == task.id) it.copy(status = targetStatus) else it }
        applyFilter()

        viewModel.updateTaskStatus(task.id, targetStatus) { success, errMsg ->
            // clear loading
            adapter.setLoading(task.id, false)

            val root = view ?: return@updateTaskStatus

            if (success) {
                // show undo snackbar
                val snack = com.google.android.material.snackbar.Snackbar.make(root, "Task updated", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                snack.setAction("Undo") {
                    // revert to previous status snapshot
                    myTasks = previousTasksSnapshot
                    applyFilter()
                    // send revert request (no further undo)
                    adapter.setLoading(task.id, true)
                    viewModel.updateTaskStatus(task.id, previousTasksSnapshot.firstOrNull { it.id == task.id }?.status ?: "TODO") { ok, _ ->
                        adapter.setLoading(task.id, false)
                        if (!ok) {
                            android.widget.Toast.makeText(requireContext(), "Failed to revert", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                // Update local snapshot from ViewModel so UI reflects the confirmed state immediately
                val vmTasks = viewModel.tasks.value ?: emptyList()
                val userIdNow = PreferenceManager(requireContext()).getUserId()
                myTasks = if (userIdNow != null) {
                    vmTasks.filter { it.assignees.any { a -> a.id == userIdNow } }
                } else {
                    emptyList()
                }
                // Merge any still-pending optimistic statuses
                myTasks = myTasks.map { t ->
                    val pending = viewModel.getPendingStatus(t.id)
                    if (!pending.isNullOrEmpty() && pending != t.status) t.copy(status = pending) else t
                }
                applyFilter()

                // scheduling a delayed authoritative fetch to fully reconcile with server (small debounce)
                view?.postDelayed({ currentTeamId?.let { viewModel.fetchTasks(it) } }, 800)
            } else {
                // revert locally to previous snapshot and show retry snackbar
                myTasks = previousTasksSnapshot
                applyFilter()

                val errText = errMsg ?: "Failed to update task status"
                val snack = com.google.android.material.snackbar.Snackbar.make(root, errText, com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                snack.setAction("Retry") {
                    updateTaskStatusWithUi(task, targetStatus, previousTasksSnapshot)
                }
                snack.setActionTextColor(android.graphics.Color.YELLOW)
                snack.show()
            }
        }
    }
}