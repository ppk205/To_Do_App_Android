package com.example.morp_prj.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.api.TeamTaskApiService
import com.example.morp_prj.data.model.TeamTask
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class TeamTaskViewModel : ViewModel() {

    // Danh sách Task thực tế
    private val _tasks = MutableLiveData<List<TeamTask>>()
    val tasks: LiveData<List<TeamTask>> = _tasks

    // Trạng thái Loading
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    // Các biến đếm cho Summary Cards
    val overdueCount = MutableLiveData(0)
    val todayCount = MutableLiveData(0)
    val upcomingCount = MutableLiveData(0)
    val completedCount = MutableLiveData(0)

    private val apiService = RetrofitClient.teamTaskApiService

    // Pending local updates that haven't been confirmed by server yet
    // Map taskId -> targetStatus
    private val pendingUpdates = mutableMapOf<String, String>()

    // Recently confirmed updates to avoid races between local confirmation and server fetches
    // Map taskId -> Pair(status, timestampMillis)
    private val confirmedUpdates = mutableMapOf<String, Pair<String, Long>>()

    // Expose pending status check for UI
    fun isPending(taskId: String): Boolean = pendingUpdates.containsKey(taskId)

    fun getPendingStatus(taskId: String): String? = pendingUpdates[taskId]

    fun fetchTasks(teamId: String) {
        _isLoading.postValue(true)
        _errorMessage.postValue(null)
        apiService.getTeamTasks(teamId).enqueue(object : Callback<List<TeamTask>> {
            override fun onResponse(call: Call<List<TeamTask>>, response: Response<List<TeamTask>>) {
                _isLoading.postValue(false)
                if (response.isSuccessful) {
                    val taskList = response.body() ?: emptyList()
                    val now = System.currentTimeMillis()
                    // Apply pending local updates so we don't immediately overwrite optimistic changes
                    val merged = taskList.map { t ->
                        // If we have a very recent confirmed update, prefer that to avoid races
                        val conf = confirmedUpdates[t.id]
                        if (conf != null && (now - conf.second) < 5_000L) {
                            t.copy(status = conf.first)
                        } else {
                            val pending = pendingUpdates[t.id]
                            if (!pending.isNullOrEmpty()) t.copy(status = pending) else t
                        }
                    }
                    // prune old confirmed entries
                    val expiry = now - 10_000L
                    confirmedUpdates.keys.retainAll { confirmedUpdates[it]?.second ?: 0L >= expiry }
                    _tasks.value = merged
                    calculateSummary(merged) // Hàm tính toán 4 ô Summary đã viết ở lượt trước
                    _errorMessage.postValue(null)
                } else {
                    android.util.Log.w("TeamTaskViewModel", "fetchTasks failed: code=${response.code()}")
                    val err = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    _errorMessage.postValue("Server error: ${response.code()} ${err ?: ""}")
                }
            }

            override fun onFailure(call: Call<List<TeamTask>>, t: Throwable) {
                _isLoading.postValue(false)
                android.util.Log.e("TeamTaskViewModel", "fetchTasks onFailure", t)
                _errorMessage.postValue(t.message ?: "Network error")
            }
        })
    }

    fun updateTaskStatus(taskId: String, newStatus: String, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        val body = mapOf("status" to newStatus)
        // Mark as pending and apply optimistic update in ViewModel cache
        pendingUpdates[taskId] = newStatus
        _tasks.value = _tasks.value?.map { t -> if (t.id == taskId) t.copy(status = newStatus) else t }

        apiService.updateTaskStatus(taskId, body).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    // Update local cache if present
                    // Confirmed by server: remove pending marker
                    pendingUpdates.remove(taskId)
                    _tasks.value = _tasks.value?.map { t -> if (t.id == taskId) t.copy(status = newStatus) else t }
                    // Remember this confirmation briefly so we don't get clobbered by a near-simultaneous fetch
                    confirmedUpdates[taskId] = Pair(newStatus, System.currentTimeMillis())
                    calculateSummary(_tasks.value ?: emptyList())
                    _errorMessage.postValue(null)
                    onComplete(true, null)
                } else {
                    val err = try { response.errorBody()?.string() } catch (e: Exception) { null }
                    android.util.Log.w("TeamTaskViewModel", "updateTaskStatus failed: code=${response.code()} body=$err")
                    // On failure remove pending marker so future fetch won't keep applying it
                    pendingUpdates.remove(taskId)
                    confirmedUpdates.remove(taskId)
                    _errorMessage.postValue("Update failed: ${response.code()} ${err ?: ""}")
                    onComplete(false, "${response.code()}: ${err ?: "Unknown error"}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                android.util.Log.e("TeamTaskViewModel", "updateTaskStatus onFailure", t)
                _errorMessage.postValue(t.message ?: "Network error")
                onComplete(false, t.message)
            }
        })
    }

    private fun calculateSummary(taskList: List<TeamTask>) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        // Reset counts
        var overdue = 0
        var today = 0
        var upcoming = 0
        var completed = 0

        taskList.forEach { task ->
            when {
                task.status.uppercase() == "DONE" || task.status.uppercase() == "COMPLETED" -> completed++
                task.dueDate != null && task.dueDate < now -> overdue++
                task.dueDate != null && isToday(task.dueDate) -> today++
                task.dueDate != null && task.dueDate > now -> upcoming++
            }
        }

        overdueCount.value = overdue
        todayCount.value = today
        upcomingCount.value = upcoming
        completedCount.value = completed
    }

    private fun isToday(timestamp: Long): Boolean {
        val taskDate = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayDate = Calendar.getInstance()
        return taskDate.get(Calendar.YEAR) == todayDate.get(Calendar.YEAR) &&
                taskDate.get(Calendar.DAY_OF_YEAR) == todayDate.get(Calendar.DAY_OF_YEAR)
    }
}