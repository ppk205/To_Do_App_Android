package com.example.morp_prj.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.morp_prj.data.TaskRepository
import com.example.morp_prj.data.db.TaskEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TaskDashboardViewModel(private val repo: TaskRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskDashboardUiState(isLoading = true))
    val uiState: StateFlow<TaskDashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val flow: Flow<List<TaskEntity>> = repo.observeAllLocal("guest")

                flow.collect { list ->
                    val now = System.currentTimeMillis()
                    val total = list.size
                    val done = list.count { it.status.equals("DONE", true) }
                    val inProgress = list.count { it.status.equals("IN_PROGRESS", true) }
                    val overdue = list.count { it.deadlineAt != null && it.deadlineAt!! < now && !it.status.equals("DONE", true) }

                    _uiState.update { s ->
                        s.copy(
                            isLoading = false,
                            total = total,
                            done = done,
                            inProgress = inProgress,
                            overdue = overdue,
                            recent = list.sortedByDescending { it.createdAt }.take(20)
                        )
                    }
                }

            } catch (t: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = t.message ?: "Unknown") }
            }
        }
    }

    fun setTimeRange(range: TimeRange) {
        _uiState.update { it.copy(timeRange = range) }
    }

    fun setAssignee(assigneeId: String?) {
        _uiState.update { it.copy(selectedAssigneeId = assigneeId) }
    }

    /**
     * Toggle a task's DONE flag. If checked==true -> set DONE.
     * If checked==false -> try to restore previous non-DONE status if known (IN_PROGRESS), otherwise TODO.
     */
    fun toggleTaskDone(taskId: Long, checked: Boolean) {
        viewModelScope.launch {
            val prev = _uiState.value
            // find previous item
            val prevItem = prev.recent.find { it.id == taskId }
            val oldStatus = prevItem?.status ?: "TODO"

            val newStatus = if (checked) {
                "DONE"
            } else {
                // prefer previous non-DONE status, else TODO
                if (oldStatus.equals("IN_PROGRESS", true)) "IN_PROGRESS" else "TODO"
            }

            // optimistic update
            val updatedRecent = prev.recent.map { if (it.id == taskId) it.copy(status = newStatus) else it }
            _uiState.update { it.copy(recent = updatedRecent) }

            try {
                // persist change
                repo.updateStatus(taskId, newStatus)
            } catch (t: Throwable) {
                // rollback to previous list on failure
                _uiState.update { it.copy(recent = prev.recent, error = t.message) }
            }
        }
    }
}
