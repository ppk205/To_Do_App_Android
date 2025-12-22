package com.example.morp_prj.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.TaskRepository
import com.example.morp_prj.data.db.AppDatabase
import com.example.morp_prj.data.db.TaskEntity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TaskFragment : Fragment() {

    private enum class Priority { LOW, MEDIUM, HIGH }

    private var selectedPriority: Priority = Priority.MEDIUM
    private var selectedDeadlineAt: Long? = null

    private val deadlineFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    private val repository by lazy {
        TaskRepository(AppDatabase.getInstance(requireContext()).taskDao())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val etTitle = view.findViewById<TextInputEditText>(R.id.etTitle)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etDescription)
        val etDeadline = view.findViewById<TextInputEditText>(R.id.etDeadline)
        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.togglePriority)

        // Back
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Default selection
        toggle.check(R.id.btnPriorityMedium)

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedPriority = when (checkedId) {
                R.id.btnPriorityLow -> Priority.LOW
                R.id.btnPriorityHigh -> Priority.HIGH
                else -> Priority.MEDIUM
            }
        }

        etDeadline.setOnClickListener { openDeadlinePicker(etDeadline) }

        fun submit() {
            val title = etTitle.text?.toString().orEmpty().trim()
            val description = etDescription.text?.toString().orEmpty().trim()

            if (title.isBlank()) {
                Toast.makeText(requireContext(), "Task title is required", Toast.LENGTH_SHORT).show()
                return
            }

            // Tags: allow users to type hashtags or comma-separated tags in description as a quick workaround for now.
            // Format supported: #work #marketing OR "work, marketing".
            val tagsCsv = extractTagsCsv(description)

            val entity = TaskEntity(
                title = title,
                description = description,
                deadlineAt = selectedDeadlineAt,
                priority = selectedPriority.name,
                status = "TODO",
                tagsCsv = tagsCsv,
            )

            viewLifecycleOwner.lifecycleScope.launch {
                repository.insert(entity)
                findNavController().navigateUp()
            }
        }

        // Save icon on toolbar
        view.findViewById<View>(R.id.btnSaveIcon).setOnClickListener { submit() }

        // Bottom buttons
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { findNavController().navigateUp() }
        view.findViewById<View>(R.id.btnSave).setOnClickListener { submit() }
    }

    private fun openDeadlinePicker(target: TextInputEditText) {
        val now = Calendar.getInstance()
        val picked = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                picked.set(Calendar.YEAR, year)
                picked.set(Calendar.MONTH, month)
                picked.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        picked.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        picked.set(Calendar.MINUTE, minute)
                        picked.set(Calendar.SECOND, 0)
                        picked.set(Calendar.MILLISECOND, 0)

                        selectedDeadlineAt = picked.timeInMillis
                        target.setText(deadlineFormatter.format(picked.time))
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun extractTagsCsv(text: String): String {
        val hashtagTags = Regex("#(\\w+)")
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()

        val commaTags = text
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val tags = (hashtagTags + commaTags)
            .distinctBy { it.lowercase() }
            .take(10)

        return tags.joinToString(",")
    }
}