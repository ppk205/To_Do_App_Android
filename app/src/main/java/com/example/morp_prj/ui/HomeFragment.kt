package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.TaskRepository
import com.example.morp_prj.data.TaskUiMapper.toUiItem
import com.example.morp_prj.data.db.AppDatabase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var toDoAdapter: ToDoAdapter

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

        setupRecyclerView(view)
        loadTasksFromDatabase() // Thực hiện load dữ liệu từ Database
        setupListeners(view)
    }

    private fun setupRecyclerView(root: View) {
        toDoAdapter = ToDoAdapter(
            onItemClick = { task ->
                Toast.makeText(context, "Clicked: ${task.title}", Toast.LENGTH_SHORT).show()
            },
            // Xử lý đồng bộ trạng thái khi user tick checkbox ở màn hình Home
            onCheckedChanged = { todo, isChecked ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val newStatus = if (isChecked) "DONE" else "TODO"
                    repository.updateStatus(todo.id, newStatus)
                }
            }
        )

        val rvTasks = root.findViewById<RecyclerView>(R.id.rvTasks)
        rvTasks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = toDoAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadTasksFromDatabase() {
        // Lắng nghe sự thay đổi dữ liệu từ Database theo thời gian thực (Real-time)
        // Dữ liệu tạo ở PersonalFragment sẽ tự động hiển thị ở đây
        viewLifecycleOwner.lifecycleScope.launch {
            repository.observeAll()
                .map { list ->
                    list.map { it.toUiItem() }
                }
                .distinctUntilChanged()
                .collect { tasks ->
                    toDoAdapter.submitList(tasks)
                }
        }
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