package com.example.morp_prj.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
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

    // Edit Mode Variables
    private var taskId: String? = null
    private var initTitle: String? = null
    private var initDesc: String? = null
    private var initPriority: String? = null
    private var initTags: ArrayList<String>? = null
    private var initDueDate: Long = 0L
    private var initAssigneeIds: ArrayList<String>? = null

    // Current Data
    private var dueDate: Long? = null
    private val selectedAssignees = mutableListOf<TeamMember>()
    private var allTeamMembers = listOf<TeamMember>()

    // UI References
    private lateinit var etDueDate: EditText
    private lateinit var chipGroupMembers: ChipGroup
    private lateinit var chipGroupTags: ChipGroup
    private lateinit var etTaskTags: EditText
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            teamId = it.getString("teamId")
            taskId = it.getString("taskId")
            if (taskId != null) {
                initTitle = it.getString("taskTitle")
                initDesc = it.getString("taskDesc")
                initPriority = it.getString("taskPriority")
                initTags = it.getStringArrayList("taskTags")
                initAssigneeIds = it.getStringArrayList("taskAssigneeIds")
                initDueDate = it.getLong("taskDueDate", 0L)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        etDueDate = view.findViewById(R.id.etDueDate)
        chipGroupMembers = view.findViewById(R.id.chipGroupMembers)
        chipGroupTags = view.findViewById(R.id.chipGroupTags)
        etTaskTags = view.findViewById(R.id.etTaskTags)

        setupSpinner(view)
        setupClickListeners(view)
        setupTagInput()

        if (taskId != null) {
            setupEditMode(view)
        }

        loadTeamMembers()

        // Ensure input fields scroll into view when keyboard opens
        val scrollToView: (View) -> Unit = { v ->
            v.postDelayed({
                v.parent.requestChildFocus(v, v)
            }, 100)
        }

        view.findViewById<EditText>(R.id.etTaskName)?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) scrollToView(v)
        }
        view.findViewById<EditText>(R.id.etDescription)?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) scrollToView(v)
        }
        etDueDate.setOnFocusChangeListener { v, hasFocus -> if (hasFocus) scrollToView(v) }
        etTaskTags.setOnFocusChangeListener { v, hasFocus -> if (hasFocus) scrollToView(v) }
    }


    private fun setupTagInput() {
        etTaskTags.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val tagText = etTaskTags.text.toString().trim()
                if (tagText.isNotEmpty()) {
                    addTagChip(tagText)
                    etTaskTags.text.clear()
                }
                return@setOnKeyListener true
            }
            false
        }

        etTaskTags.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val tagText = etTaskTags.text.toString().trim()
                if (tagText.isNotEmpty()) {
                    addTagChip(tagText)
                    etTaskTags.text.clear()
                }
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun addTagChip(tagText: String) {
        val chip = Chip(context)
        chip.text = tagText
        chip.isCloseIconVisible = true
        chip.setOnCloseIconClickListener {
            chipGroupTags.removeView(chip)
        }
        chipGroupTags.addView(chip)
    }

    private fun setupEditMode(view: View) {
        val tvHeader = view.findViewById<TextView>(R.id.tvHeaderTitle)
        if (tvHeader != null) tvHeader.text = "Edit Task"
        view.findViewById<Button>(R.id.btnCreateTask).text = "Update Task"

        view.findViewById<EditText>(R.id.etTaskName).setText(initTitle)
        view.findViewById<EditText>(R.id.etDescription).setText(initDesc)

        initTags?.forEach { tag ->
            addTagChip(tag)
        }

        if (initDueDate > 0) {
            dueDate = initDueDate
            val calendar = Calendar.getInstance().apply { timeInMillis = initDueDate }
            etDueDate.setText(
                SimpleDateFormat("dd-MM-yyyy  |  HH:mm", Locale.getDefault()).format(calendar.time)
            )
        }

        initPriority?.let { priority ->
            val spinner = view.findViewById<Spinner>(R.id.spinnerPriority)
            spinner.post {
                val adapter = spinner.adapter
                if (adapter != null) {
                    for (i in 0 until adapter.count) {
                        if (adapter.getItem(i).toString().equals(priority, ignoreCase = true)) {
                            spinner.setSelection(i)
                            break
                        }
                    }
                }
            }
        }
    }

    private fun submitTask() {
        val currentTeamId = teamId ?: return
        val currentUserId = preferenceManager.getUserId() ?: return

        val title = view?.findViewById<EditText>(R.id.etTaskName)?.text.toString()
        val description = view?.findViewById<EditText>(R.id.etDescription)?.text.toString()

        val tagsList = mutableListOf<String>()
        for (i in 0 until chipGroupTags.childCount) {
            val chip = chipGroupTags.getChildAt(i) as? Chip
            chip?.let { tagsList.add(it.text.toString()) }
        }
        val pendingTag = etTaskTags.text.toString().trim()
        if (pendingTag.isNotEmpty()) {
            tagsList.add(pendingTag)
        }

        val priority = view?.findViewById<Spinner>(R.id.spinnerPriority)?.selectedItem.toString()
        val assigneeIds = selectedAssignees.map { it.id }

        if (title.isEmpty()) {
            Toast.makeText(context, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateTeamTaskRequest(
            currentTeamId, title, description, dueDate, priority, assigneeIds,
            tagsList,
            currentUserId
        )

        val callback = object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    val msg = if (taskId != null) "Task updated" else "Task created"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                } else {
                    Toast.makeText(context, "Action failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        }

        if (taskId != null) {
            RetrofitClient.teamTaskApiService.updateTeamTask(taskId!!, request).enqueue(callback)
        } else {
            RetrofitClient.teamTaskApiService.createTeamTask(request).enqueue(callback)
        }
    }

    private fun loadTeamMembers() {
        teamId?.let {
            RetrofitClient.teamApiService.getTeamMembers(it).enqueue(object : Callback<List<TeamMember>> {
                override fun onResponse(call: Call<List<TeamMember>>, response: Response<List<TeamMember>>) {
                    if (response.isSuccessful) {
                        allTeamMembers = response.body() ?: emptyList()
                        if (taskId != null && !initAssigneeIds.isNullOrEmpty()) {
                            selectedAssignees.clear()
                            val preSelected = allTeamMembers.filter { member -> initAssigneeIds!!.contains(member.id) }
                            selectedAssignees.addAll(preSelected)
                            updateAssigneesChips()
                        }
                    }
                }
                override fun onFailure(call: Call<List<TeamMember>>, t: Throwable) { }
            })
        }
    }

    private fun setupSpinner(view: View) {
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinnerPriority)
        ArrayAdapter.createFromResource(requireContext(), R.array.priorities_teamtask_spinner, android.R.layout.simple_spinner_item).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPriority.adapter = adapter
        }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener { findNavController().popBackStack() }
        view.findViewById<EditText>(R.id.etDueDate).setOnClickListener { showDatePickerDialog() }
        view.findViewById<Button>(R.id.btnAddMember).setOnClickListener { showAssigneeSelectionDialog() }
        view.findViewById<Button>(R.id.btnCreateTask).setOnClickListener { submitTask() }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        if (dueDate != null) calendar.timeInMillis = dueDate!!
        DatePickerDialog(requireContext(), { _, year, month, day -> showTimePickerDialog(year, month, day) },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePickerDialog(year: Int, month: Int, day: Int) {
        val calendar = Calendar.getInstance()
        if (dueDate != null) calendar.timeInMillis = dueDate!!
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val cal = Calendar.getInstance().apply { set(year, month, day, hour, minute, 0); set(Calendar.MILLISECOND, 0) }
            dueDate = cal.timeInMillis
            etDueDate.setText(SimpleDateFormat("dd-MM-yyyy  |  HH:mm", Locale.getDefault()).format(cal.time))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun showAssigneeSelectionDialog() {
        val memberNames = allTeamMembers.map { it.displayName }.toTypedArray()
        val checkedItems = BooleanArray(memberNames.size) { selectedAssignees.any { selected -> selected.id == allTeamMembers[it].id } }
        AlertDialog.Builder(requireContext()).setTitle("Assign to")
            .setMultiChoiceItems(memberNames, checkedItems) { _, which, isChecked ->
                val member = allTeamMembers[which]
                if (isChecked) if (!selectedAssignees.any { it.id == member.id }) selectedAssignees.add(member)
                else selectedAssignees.removeAll { it.id == member.id }
            }
            .setPositiveButton("OK") { _, _ -> updateAssigneesChips() }.setNegativeButton("Cancel", null).show()
    }

    private fun updateAssigneesChips() {
        chipGroupMembers.removeAllViews()
        selectedAssignees.forEach { member ->
            val chip = Chip(context)
            chip.text = member.displayName
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener { selectedAssignees.remove(member); updateAssigneesChips() }
            chipGroupMembers.addView(chip)
        }
    }
}