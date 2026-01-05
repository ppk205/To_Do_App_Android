package com.example.morp_prj.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.data.model.CalendarDate
import com.example.morp_prj.R
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

class CalendarDateAdapter(
    private val dates: List<CalendarDate>,
    private val onDateClick: (CalendarDate) -> Unit
) : RecyclerView.Adapter<CalendarDateAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDayOfWeek: TextView = view.findViewById(R.id.tvDayOfWeek)
        val tvDayNumber: TextView = view.findViewById(R.id.tvDayNumber)

        fun bind(item: CalendarDate) {
            tvDayOfWeek.text = item.dayOfWeek
            tvDayNumber.text = item.dayNumber

            if (item.isSelected) {
                tvDayNumber.background = ContextCompat.getDrawable(itemView.context, R.drawable.bg_calendar_date_selected)
                tvDayNumber.setTextColor(Color.WHITE)
                tvDayOfWeek.setTextColor(Color.BLACK)
            } else {
                tvDayNumber.background = null
                tvDayNumber.setTextColor(Color.GRAY)
                tvDayOfWeek.setTextColor(Color.GRAY)
            }

            itemView.setOnClickListener {
                onDateClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_date, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dates[position])
    }

    override fun getItemCount() = dates.size
}