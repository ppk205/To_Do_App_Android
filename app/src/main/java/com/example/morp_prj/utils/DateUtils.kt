package com.example.morp_prj.utils

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    // Input: "2023-10-25T14:30:00.000Z" (ISO 8601 từ Server JS)
    // Output: "14:30"
    fun formatTime(isoString: String?): String {
        if (isoString.isNullOrEmpty()) return ""
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")

            val date = inputFormat.parse(isoString) ?: return ""

            // Format output
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            outputFormat.timeZone = TimeZone.getDefault() // Giờ theo máy người dùng
            return outputFormat.format(date)
        } catch (e: Exception) {
            // Fallback
            return try {
                val simpleInput = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = simpleInput.parse(isoString)
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date ?: return "")
            } catch (ex: Exception) {
                ""
            }
        }
    }
}