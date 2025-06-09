package com.SICV.plurry.crewstep

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WalkRecord(
    val name: String,
    val time: String,
    val distance: String,
    val duration: String,
    val calories: String
) {
    fun getParsedTime(): Date? {
        return try {
            val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
            val koreaLocale = Locale.KOREA
            val formatter = SimpleDateFormat("yy-MM-dd HH:mm", koreaLocale)
            formatter.timeZone = koreaTimeZone
            formatter.parse(time)
        } catch (e: Exception) {
            null
        }
    }
}
