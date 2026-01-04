package com.example.morp_prj.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import java.util.Calendar

class CalendarGridAdapter(
    private val dateStatusMap: Map<String, Int>,
    private var selectedDateMillis: Long?,
    private val onDayClick: (Long) -> Unit
) : RecyclerView.Adapter<CalendarGridAdapter.ViewHolder>() {

    private var days = listOf<Long?>()
    private var currentMonth: Int = 0

    fun submitList(newDays: List<Long?>, month: Int) {
        days = newDays
        currentMonth = month
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tvDayNumber)
        val viewSelected: View = view.findViewById(R.id.viewSelectedBg)
        val viewPending: View = view.findViewById(R.id.viewPendingRing)
        val ivDoneX: ImageView = view.findViewById(R.id.ivDoneX)

        fun bind(dateMillis: Long?) {
            if (dateMillis == null) {
                itemView.visibility = View.INVISIBLE
                return
            }
            itemView.visibility = View.VISIBLE

            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val key = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"

            tvDay.text = dayOfMonth.toString()

            val isSelected = selectedDateMillis != null && isSameDay(dateMillis, selectedDateMillis!!)
            if (isSelected) {
                viewSelected.visibility = View.VISIBLE
                tvDay.setTextColor(Color.WHITE)
            } else {
                viewSelected.visibility = View.INVISIBLE
                tvDay.setTextColor(Color.BLACK)
            }

            val status = dateStatusMap[key] ?: 0

            viewPending.visibility = View.INVISIBLE
            ivDoneX.visibility = View.GONE

            if (!isSelected) {
                if (status == 1) {
                    // Has Pending -> Circle Ring
                    viewPending.visibility = View.VISIBLE
                } else if (status == 2) {
                    // All Done -> X Icon
                    ivDoneX.visibility = View.VISIBLE
                }
            }

            itemView.setOnClickListener {
                onDayClick(dateMillis)
            }
        }
    }

    private fun isSameDay(d1: Long, d2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = d1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = d2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_grid_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount() = days.size
}