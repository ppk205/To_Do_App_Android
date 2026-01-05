package com.example.morp_prj.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    fun formatTime(timeInMillis: Long): String {
        if (timeInMillis == 0L) return ""
        return try {
            val date = Date(timeInMillis)
            val format = SimpleDateFormat("hh:mm a", Locale.getDefault()) // 12:12 PM
            format.format(date)
        } catch (e: Exception) {
            ""
        }
    }

    fun formatTime(isoString: String?): String {
        if (isoString.isNullOrEmpty()) return ""
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(isoString) ?: return ""
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            return outputFormat.format(date)
        } catch (e: Exception) {
            return ""
        }
    }

    fun isSameDay(date1Millis: Long, date2Millis: Long): Boolean {
        val calendar1 = Calendar.getInstance().apply { timeInMillis = date1Millis }
        val calendar2 = Calendar.getInstance().apply { timeInMillis = date2Millis }
        return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) &&
                calendar1.get(Calendar.DAY_OF_YEAR) == calendar2.get(Calendar.DAY_OF_YEAR)
    }

    fun isToday(timeInMillis: Long): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    fun isThisWeek(timeInMillis: Long): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.WEEK_OF_YEAR) == target.get(Calendar.WEEK_OF_YEAR)
    }
}