package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.model.TeamTask
import com.example.morp_prj.utils.DateUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarPickerBottomSheet(
    private val allTasks: List<TeamTask>,
    private var selectedDateMillis: Long? = null,
    private val onDateSelected: (Long) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var tvMonthYear: TextView
    private lateinit var rvGrid: RecyclerView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var adapter: CalendarGridAdapter

    private val currentCalendar = Calendar.getInstance()

    // Map lưu trạng thái từng ngày: "yyyy-DDD" -> Status
    // Status: 0 = None, 1 = Has Pending (Circle), 2 = All Done
    private val dateStatusMap = mutableMapOf<String, Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_calendar_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvMonthYear = view.findViewById(R.id.tvMonthYear)
        rvGrid = view.findViewById(R.id.rvCalendarGrid)
        btnPrev = view.findViewById(R.id.btnPrevMonth)
        btnNext = view.findViewById(R.id.btnNextMonth)

        analyzeTasks()

        setupRecyclerView()
        updateMonthDisplay()

        btnPrev.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateMonthDisplay()
        }

        btnNext.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateMonthDisplay()
        }
    }

    private fun analyzeTasks() {
        dateStatusMap.clear()
        val tasksByDay = allTasks.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.dueDate ?: 0L }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        }

        tasksByDay.forEach { (key, tasks) ->
            if (tasks.isEmpty()) return@forEach

            val hasPending = tasks.any { it.status != "DONE" && it.status != "COMPLETED" }
            if (hasPending) {
                dateStatusMap[key] = 1
            } else {
                dateStatusMap[key] = 2
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = CalendarGridAdapter(
            dateStatusMap = dateStatusMap,
            selectedDateMillis = selectedDateMillis,
            onDayClick = { dateMillis ->
                onDateSelected(dateMillis)
                dismiss()
            }
        )
        rvGrid.layoutManager = GridLayoutManager(context, 7)
        rvGrid.adapter = adapter
    }

    private fun updateMonthDisplay() {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        tvMonthYear.text = sdf.format(currentCalendar.time)

        val days = ArrayList<Long?>()
        val tempCal = currentCalendar.clone() as Calendar

        tempCal.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
        val emptySlots = firstDayOfWeek - 1 // Calendar.SUNDAY is 1

        for (i in 0 until emptySlots) {
            days.add(null)
        }

        val maxDays = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..maxDays) {
            days.add(tempCal.timeInMillis)
            tempCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        adapter.submitList(days, currentCalendar.get(Calendar.MONTH))
    }
}