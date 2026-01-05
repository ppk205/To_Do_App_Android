package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.morp_prj.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton

class TaskFilterBottomSheet(
    private val availableTags: List<String>,
    private val onApplyFilter: (String, DateFilterType, List<String>) -> Unit
) : BottomSheetDialogFragment() {

    enum class DateFilterType { ALL, TODAY, THIS_WEEK, OVERDUE }

    private lateinit var etSearch: TextInputEditText
    private lateinit var chipGroupDate: ChipGroup
    private lateinit var chipGroupTags: ChipGroup
    private lateinit var btnApply: MaterialButton
    private lateinit var btnReset: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_task_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etSearch = view.findViewById(R.id.etSearch)
        chipGroupDate = view.findViewById(R.id.chipGroupDate)
        chipGroupTags = view.findViewById(R.id.chipGroupTagsFilter)
        btnApply = view.findViewById(R.id.btnApply)
        btnReset = view.findViewById(R.id.btnReset)

        availableTags.forEach { tag ->
            val chip = Chip(context)
            chip.text = tag
            chip.isCheckable = true
            chip.isClickable = true
            chipGroupTags.addView(chip)
        }

        btnApply.setOnClickListener {
            val query = etSearch.text.toString().trim()

            val dateFilter = when (chipGroupDate.checkedChipId) {
                R.id.chipToday -> DateFilterType.TODAY
                R.id.chipThisWeek -> DateFilterType.THIS_WEEK
                R.id.chipOverdue -> DateFilterType.OVERDUE
                else -> DateFilterType.ALL
            }

            val selectedTags = mutableListOf<String>()
            for (i in 0 until chipGroupTags.childCount) {
                val chip = chipGroupTags.getChildAt(i) as Chip
                if (chip.isChecked) {
                    selectedTags.add(chip.text.toString())
                }
            }

            onApplyFilter(query, dateFilter, selectedTags)
            dismiss()
        }

        btnReset.setOnClickListener {
            etSearch.text?.clear()
            chipGroupDate.check(R.id.chipAllTime)
            chipGroupTags.clearCheck()
        }
    }
}