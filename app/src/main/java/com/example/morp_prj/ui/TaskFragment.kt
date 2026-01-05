package com.example.morp_prj.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.TaskRepository
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.data.db.TaskEntity
import com.example.morp_prj.utils.PreferenceManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskFragment : Fragment(R.layout.fragment_task) {

    private var selectedDeadlineAt: Long? = null

    // Match CreateTeamTask style (day-month-year and 24h time)
    private val deadlineFormatter = SimpleDateFormat("dd-MM-yyyy  |  HH:mm", Locale.getDefault())

    private val repository by lazy {
        TaskRepository(AppDatabase.getInstance(requireContext()).taskDao())
    }

    private val prefs: PreferenceManager by lazy { PreferenceManager(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etTaskName = view.findViewById<EditText>(R.id.etTaskName)
        val etDescription = view.findViewById<EditText>(R.id.etDescription)
        val etDueDate = view.findViewById<EditText>(R.id.etDueDate)
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinnerPriority)
        val etTags = view.findViewById<EditText>(R.id.etTaskTags)

        // Back
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        // Priority spinner (reuse same options as team task)
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.priorities_teamtask_spinner,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPriority.adapter = adapter
        }

        etDueDate.setOnClickListener { showDatePickerDialog(etDueDate) }

        view.findViewById<View>(R.id.btnCreateTask).setOnClickListener {
            submit(etTaskName, etDescription, etTags, spinnerPriority)
        }
    }

    private fun submit(
        etTaskName: EditText,
        etDescription: EditText,
        etTags: EditText,
        spinnerPriority: Spinner,
    ) {
        val title = etTaskName.text?.toString().orEmpty().trim()
        val description = etDescription.text?.toString().orEmpty().trim()

        if (title.isBlank()) {
            Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show()
            return
        }

        val tagsInput = etTags.text?.toString()?.trim()
        val tagsCsv = if (!tagsInput.isNullOrBlank()) {
            tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        } else ""

        val priority = spinnerPriority.selectedItem?.toString().orEmpty().ifBlank { "MEDIUM" }
        val userId = prefs.getCurrentUserIdOrGuest()

        val entity = TaskEntity(
            title = title,
            description = description,
            deadlineAt = selectedDeadlineAt,
            priority = priority,
            status = "TODO",
            tagsCsv = tagsCsv,
            userId = userId,
        )

        viewLifecycleOwner.lifecycleScope.launch {
            repository.insert(entity)
            findNavController().navigateUp()
        }
    }

    private fun showDatePickerDialog(target: EditText) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                showTimePickerDialog(target, year, month, dayOfMonth)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePickerDialog(target: EditText, year: Int, month: Int, dayOfMonth: Int) {
        val calendar = Calendar.getInstance()

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                selectedDeadlineAt = selectedCalendar.timeInMillis
                target.setText(deadlineFormatter.format(selectedCalendar.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }
}