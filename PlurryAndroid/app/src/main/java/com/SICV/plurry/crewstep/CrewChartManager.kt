package com.SICV.plurry.crewstep

import android.graphics.Color
import android.util.Log
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.*

class CrewChartManager(private val barChart: BarChart) {

    private val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
    private val koreaLocale = Locale.KOREA

    fun setChartData(entries: List<BarEntry>, labels: List<String>, labelName: String) {
        val dataSet = BarDataSet(entries, labelName)
        dataSet.color = Color.parseColor("#4CAF50")

        dataSet.setDrawValues(true)
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value == 0f) "" else String.format("%.1f", value)
            }
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.9f

        barChart.data = barData
        barChart.setFitBars(true)
        barChart.description.isEnabled = false

        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        barChart.axisRight.isEnabled = false

        barChart.invalidate()
    }

    fun getDayLabels() = listOf("0-4", "4-8", "8-12", "12-16", "16-20", "20-24")

    fun getDayData(crewWalkData: List<WalkRecord>): List<BarEntry> {
        val timeSlots = FloatArray(6) { 0f }
        val today = Calendar.getInstance(koreaTimeZone, koreaLocale)
        val todayYear = today.get(Calendar.YEAR)
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay = today.get(Calendar.DAY_OF_MONTH)

        for (record in crewWalkData) {
            try {
                val time = record.getParsedTime()
                if (time != null) {
                    val calendar = Calendar.getInstance(koreaTimeZone, koreaLocale)
                    calendar.time = time

                    if (calendar.get(Calendar.YEAR) == todayYear &&
                        calendar.get(Calendar.MONTH) == todayMonth &&
                        calendar.get(Calendar.DAY_OF_MONTH) == todayDay) {

                        val hour = calendar.get(Calendar.HOUR_OF_DAY)
                        val slotIndex = when (hour) {
                            in 0..3 -> 0
                            in 4..7 -> 1
                            in 8..11 -> 2
                            in 12..15 -> 3
                            in 16..19 -> 4
                            in 20..23 -> 5
                            else -> 0
                        }
                        val distance = record.distance.replace("km", "").toFloatOrNull() ?: 0f
                        timeSlots[slotIndex] += distance
                    }
                }
            } catch (e: Exception) {
                Log.e("CrewChartManager", "시간 파싱 오류: ${record.time}", e)
            }
        }

        return timeSlots.mapIndexed { index, value ->
            BarEntry(index.toFloat(), value)
        }
    }

    fun getWeekLabels() = listOf("월", "화", "수", "목", "금", "토", "일")

    fun getWeekData(crewWalkData: List<WalkRecord>): List<BarEntry> {
        val weeklyData = FloatArray(7) { 0f }
        val today = Calendar.getInstance(koreaTimeZone, koreaLocale)

        val startOfWeek = Calendar.getInstance(koreaTimeZone, koreaLocale)
        startOfWeek.time = today.time
        startOfWeek.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        startOfWeek.set(Calendar.HOUR_OF_DAY, 0)
        startOfWeek.set(Calendar.MINUTE, 0)
        startOfWeek.set(Calendar.SECOND, 0)
        startOfWeek.set(Calendar.MILLISECOND, 0)

        val endOfWeek = Calendar.getInstance(koreaTimeZone, koreaLocale)
        endOfWeek.time = startOfWeek.time
        endOfWeek.add(Calendar.DAY_OF_WEEK, 6)
        endOfWeek.set(Calendar.HOUR_OF_DAY, 23)
        endOfWeek.set(Calendar.MINUTE, 59)
        endOfWeek.set(Calendar.SECOND, 59)

        for (record in crewWalkData) {
            try {
                val time = record.getParsedTime()
                if (time != null) {
                    val calendar = Calendar.getInstance(koreaTimeZone, koreaLocale)
                    calendar.time = time

                    if (calendar.timeInMillis >= startOfWeek.timeInMillis &&
                        calendar.timeInMillis <= endOfWeek.timeInMillis) {

                        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        val index = when (dayOfWeek) {
                            Calendar.MONDAY -> 0
                            Calendar.TUESDAY -> 1
                            Calendar.WEDNESDAY -> 2
                            Calendar.THURSDAY -> 3
                            Calendar.FRIDAY -> 4
                            Calendar.SATURDAY -> 5
                            Calendar.SUNDAY -> 6
                            else -> 0
                        }
                        val distance = record.distance.replace("km", "").toFloatOrNull() ?: 0f
                        weeklyData[index] += distance
                    }
                }
            } catch (e: Exception) {
                Log.e("CrewChartManager", "요일 파싱 오류: ${record.time}", e)
            }
        }

        return weeklyData.mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
    }

    fun getMonthLabels(): List<String> {
        val labels = mutableListOf<String>()
        val calendar = Calendar.getInstance(koreaTimeZone, koreaLocale)

        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        val maxDayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (day in 1..maxDayOfMonth) {
            labels.add("${currentMonth + 1}/$day")
        }

        return labels
    }

    fun getMonthData(crewWalkData: List<WalkRecord>): List<BarEntry> {
        val currentCalendar = Calendar.getInstance(koreaTimeZone, koreaLocale)
        val currentMonth = currentCalendar.get(Calendar.MONTH)
        val currentYear = currentCalendar.get(Calendar.YEAR)
        val maxDayOfMonth = currentCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val monthlyData = FloatArray(maxDayOfMonth) { 0f }

        for (record in crewWalkData) {
            try {
                val time = record.getParsedTime()
                if (time != null) {
                    val recordCalendar = Calendar.getInstance(koreaTimeZone, koreaLocale)
                    recordCalendar.time = time

                    val recordYear = recordCalendar.get(Calendar.YEAR)
                    val recordMonth = recordCalendar.get(Calendar.MONTH)
                    val recordDay = recordCalendar.get(Calendar.DAY_OF_MONTH)

                    if (recordYear == currentYear && recordMonth == currentMonth) {
                        val index = recordDay - 1
                        if (index >= 0 && index < maxDayOfMonth) {
                            val distance = record.distance.replace("km", "").toFloatOrNull() ?: 0f
                            monthlyData[index] += distance
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CrewChartManager", "월간 데이터 파싱 오류: ${record.time}", e)
            }
        }

        return monthlyData.mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
    }

    fun refreshCurrentChart(currentTabPosition: Int, crewWalkData: List<WalkRecord>) {
        when (currentTabPosition) {
            0 -> setChartData(getDayData(crewWalkData), getDayLabels(), "시간대별")
            1 -> setChartData(getWeekData(crewWalkData), getWeekLabels(), "요일별")
            2 -> setChartData(getMonthData(crewWalkData), getMonthLabels(), "일별")
            else -> setChartData(getDayData(crewWalkData), getDayLabels(), "시간대별")
        }
    }
}