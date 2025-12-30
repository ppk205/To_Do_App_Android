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

    // Các biến đếm cho Summary Cards
    val overdueCount = MutableLiveData(0)
    val todayCount = MutableLiveData(0)
    val upcomingCount = MutableLiveData(0)
    val completedCount = MutableLiveData(0)

    private val apiService = RetrofitClient.teamTaskApiService

    fun fetchTasks(teamId: String) {
        apiService.getTeamTasks(teamId).enqueue(object : Callback<List<TeamTask>> {
            override fun onResponse(call: Call<List<TeamTask>>, response: Response<List<TeamTask>>) {
                if (response.isSuccessful) {
                    val taskList = response.body() ?: emptyList()
                    _tasks.value = taskList
                    calculateSummary(taskList) // Hàm tính toán 4 ô Summary đã viết ở lượt trước
                }
            }

            override fun onFailure(call: Call<List<TeamTask>>, t: Throwable) {
                // Xử lý lỗi kết nối
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