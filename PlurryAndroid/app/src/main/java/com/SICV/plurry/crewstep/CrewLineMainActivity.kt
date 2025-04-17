package com.SICV.plurry.crewstep

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R
import android.graphics.Color
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter


class CrewLineMainActivity : AppCompatActivity() {
    /*
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_line_main)


        val barChart = findViewById<BarChart>(R.id.barChart)

        // 예제 데이터 (시간대별 산책 시간)
        val entries = listOf(
            BarEntry(0f, 5f),  // 00:00 → 5분
            BarEntry(1f, 10f), // 04:00 → 10분
            BarEntry(2f, 3f),  // 08:00
            BarEntry(3f, 7f),  // 12:00
            BarEntry(4f, 0f),  // 16:00
            BarEntry(5f, 8f)   // 20:00
        )

        val labels = listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00")

        val dataSet = BarDataSet(entries, "산책 시간 (분)")
        dataSet.color = Color.parseColor("#4CAF50")

        val barData = BarData(dataSet)
        barData.barWidth = 0.9f

        barChart.data = barData
        barChart.setFitBars(true)
        barChart.description.isEnabled = false

        // X축 설정
        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        // 오른쪽 Y축 제거
        barChart.axisRight.isEnabled = false
        Log.d("ChartDebug", "entryCount: ${barData.entryCount}")

        // 그래프 업데이트
        barChart.invalidate()
    }
    */
}