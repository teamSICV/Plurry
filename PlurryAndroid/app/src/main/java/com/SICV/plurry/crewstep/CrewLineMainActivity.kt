package com.SICV.plurry.crewstep

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout

class CrewLineMainActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private val labels = listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_line_main)

        barChart = findViewById(R.id.barChart)
        val tabLayout = findViewById<TabLayout>(R.id.tablayout)

        // 초기: 일간 데이터
        setChartData(getDayData(),dayLabels, "시")

        // 탭 선택 리스너 등록
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> setChartData(getDayData(),dayLabels, "시")   // 일간
                    1 -> setChartData(getWeekData(),weekLabels, "요일")  // 주간
                    2 -> setChartData(getMonthData(),monthLabels, "일") // 월간
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setChartData(entries: List<BarEntry>,labels: List<String>, labelName: String) {
        val dataSet = BarDataSet(entries, labelName)
        dataSet.color = Color.parseColor("#4CAF50")

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

        Log.d("ChartDebug", "entryCount: ${barData.entryCount}")

        barChart.invalidate()
    }

    // 각각의 데이터
    private val dayLabels = listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00")
    private fun getDayData() = listOf(
        BarEntry(0f, 5f), BarEntry(1f, 10f), BarEntry(2f, 3f),
        BarEntry(3f, 7f), BarEntry(4f, 0f), BarEntry(5f, 8f)
    )

    private val weekLabels = listOf("월", "화", "수", "목", "금", "토", "일")
    private fun getWeekData() = listOf(
        BarEntry(0f, 2f), BarEntry(1f, 1.5f), BarEntry(2f, 3f),
        BarEntry(3f, 2.5f), BarEntry(4f, 1f), BarEntry(5f, 4f), BarEntry(6f, 2f)
    )

    private val monthLabels = listOf("1주", "2주", "3주", "4주")
    private fun getMonthData() = listOf(
        BarEntry(0f, 1.2f), BarEntry(1f, 2.3f), BarEntry(2f, 3.1f), BarEntry(3f, 1.8f)
    )}