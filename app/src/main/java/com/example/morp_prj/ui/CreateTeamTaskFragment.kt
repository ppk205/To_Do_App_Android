package com.example.morp_prj.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.data.api.RetrofitClient
import com.example.morp_prj.data.model.CreateTeamTaskRequest
import com.example.morp_prj.data.model.TeamMember
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateTeamTaskFragment : Fragment(R.layout.fragment_create_team_task) {

    private var teamId: String? = null
    private var dueDate: Long? = null
    private val selectedAssignees = mutableListOf<TeamMember>()
    private var allTeamMembers = listOf<TeamMember>()

    private lateinit var etDueDate: EditText
    private lateinit var chipGroupMembers: ChipGroup
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            teamId = it.getString("teamId")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        etDueDate = view.findViewById(R.id.etDueDate)
        chipGroupMembers = view.findViewById(R.id.chipGroupMembers)

        setupSpinner(view)
        setupClickListeners(view)
        
        loadTeamMembers()
    }

    private fun setupSpinner(view: View) {
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinnerPriority)
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.filter_priorities,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPriority.adapter = adapter
        }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            findNavController().popBackStack()
        }

        view.findViewById<EditText>(R.id.etDueDate).setOnClickListener {
            showDatePickerDialog()
        }

        view.findViewById<Button>(R.id.btnAddMember).setOnClickListener {
            showAssigneeSelectionDialog()
        }

        view.findViewById<Button>(R.id.btnCreateTask).setOnClickListener {
            createTask()
        }
    }
    
    private fun loadTeamMembers() {
        teamId?.let {
            RetrofitClient.teamApiService.getTeamMembers(it).enqueue(object : Callback<List<TeamMember>> {
                override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                    if (response.isSuccessful) {
                        allTeamMembers = response.body() ?: emptyList()
                    }
                }
                override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) { /* Do nothing */ }
            })
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                dueDate = selectedCalendar.timeInMillis
                etDueDate.setText(SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(selectedCalendar.time))
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun showAssigneeSelectionDialog() {
        val memberNames = allTeamMembers.map { it.displayName }.toTypedArray()
        val checkedItems = BooleanArray(memberNames.size) { selectedAssignees.any { selected -> selected.id == allTeamMembers[it].id } }

        AlertDialog.Builder(requireContext())
            .setTitle("Assign to")
            .setMultiChoiceItems(memberNames, checkedItems) { _, which, isChecked ->
                val member = allTeamMembers[which]
                if (isChecked) selectedAssignees.add(member) else selectedAssignees.removeAll { it.id == member.id }
            }
            .setPositiveButton("OK") { _, _ -> updateAssigneesChips() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAssigneesChips() {
        chipGroupMembers.removeAllViews()
        selectedAssignees.forEach { member ->
            val chip = Chip(context)
            chip.text = member.displayName
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener { 
                selectedAssignees.remove(member)
                updateAssigneesChips()
            }
            chipGroupMembers.addView(chip)
        }
    }

    private fun createTask() {
        val currentTeamId = teamId ?: return
        val currentUserId = preferenceManager.getUserId() ?: return

        val title = view?.findViewById<EditText>(R.id.etTaskName)?.text.toString()
        val description = view?.findViewById<EditText>(R.id.etDescription)?.text.toString()
        val priority = view?.findViewById<Spinner>(R.id.spinnerPriority)?.selectedItem.toString()
        val assigneeIds = selectedAssignees.map { it.id }

        if (title.isEmpty()) {
            Toast.makeText(context, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateTeamTaskRequest(currentTeamId, title, description, dueDate, priority, assigneeIds, currentUserId)

        RetrofitClient.teamTaskApiService.createTeamTask(request).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Task created successfully", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                } else {
                    Log.e("CreateTeamTask", "Failed to create task: ${response.code()}")
                    Toast.makeText(context, "Failed to create task", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("CreateTeamTask", "Error creating task", t)
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}