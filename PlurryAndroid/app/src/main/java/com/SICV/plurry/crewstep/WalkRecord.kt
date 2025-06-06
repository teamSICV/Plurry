package com.SICV.plurry.crewstep

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WalkRecord(
    val userId: String,
    val time: String, // 예: "2025-04-10 10:10"
    val distance: String,
    val duration: String,
    val calories: String
) {
    fun getParsedTime(): Date? {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(time)
        } catch (e: Exception) {
            null
        }
    }
}
