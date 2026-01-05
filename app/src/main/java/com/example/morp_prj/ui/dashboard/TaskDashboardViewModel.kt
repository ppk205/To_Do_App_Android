package com.example.morp_prj.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.TeamTask
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TaskDashboardViewModel : ViewModel() {

    private val _tasks = MutableLiveData<List<TeamTask>>()
    val tasks: LiveData<List<TeamTask>> = _tasks

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun fetchTasks(teamId: String) {
        _isLoading.value = true
        _error.value = null
        RetrofitClient.teamTaskApiService.getTeamTasks(teamId).enqueue(object : Callback<List<TeamTask>> {
            override fun onResponse(call: Call<List<TeamTask>>, response: Response<List<TeamTask>>) {
                _isLoading.value = false
                if (response.isSuccessful) {
                    _tasks.value = response.body() ?: emptyList()
                } else {
                    _error.value = "Failed to load tasks: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<List<TeamTask>>, t: Throwable) {
                _isLoading.value = false
                _error.value = t.message ?: "Network error"
            }
        })
    }
}