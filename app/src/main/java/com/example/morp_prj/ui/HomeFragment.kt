package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R

class HomeFragment : Fragment() {

    private lateinit var toDoAdapter: ToDoAdapter

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
        loadMockData()
        setupListeners(view)
    }

    private fun setupRecyclerView(root: View) {
        // Cập nhật: Khởi tạo Adapter với callback, KHÔNG truyền items vào constructor
        toDoAdapter = ToDoAdapter(
            onItemClick = { task ->
                Toast.makeText(context, "Clicked: ${task.title}", Toast.LENGTH_SHORT).show()
            },
            // Nếu muốn xử lý checkbox ở Home, thêm callback này:
            onCheckedChanged = { task, isChecked ->
                // Xử lý logic update trạng thái task tại đây nếu cần
                val statusMsg = if (isChecked) "Completed" else "Active"
                Toast.makeText(context, "Task ${task.title} is now $statusMsg", Toast.LENGTH_SHORT).show()
            }
        )

        val rvTasks = root.findViewById<RecyclerView>(R.id.rvTasks)
        rvTasks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = toDoAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadMockData() {
        val mockList = listOf(
            ToDoItem(1, "Mobile App Design", "10:00 AM - 12:30 PM", DueCategory.NONE, PriorityLevel.HIGH, listOf("Design"), TaskStatus.TODO),
            ToDoItem(2, "Team Meeting", "02:00 PM - 03:00 PM", DueCategory.NONE, PriorityLevel.MEDIUM, listOf("Meeting"), TaskStatus.TODO),
            ToDoItem(3, "Fix Login Bug", "04:00 PM - 06:00 PM", DueCategory.NONE, PriorityLevel.HIGH, listOf("Dev"), TaskStatus.DONE),
            ToDoItem(4, "Update Documentation", "09:00 AM - 10:00 AM", DueCategory.NONE, PriorityLevel.LOW, listOf("Doc"), TaskStatus.TODO)
        )

        // Cập nhật: Dùng submitList để đẩy items vào Adapter
        toDoAdapter.submitList(mockList)
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