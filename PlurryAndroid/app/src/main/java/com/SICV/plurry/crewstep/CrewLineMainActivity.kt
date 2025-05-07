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
import android.widget.ImageButton
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.*
import android.os.Handler
import android.os.Looper
import com.SICV.plurry.pointrecord.CrewPointBottomFragment
import android.widget.TextView
import java.util.Date
import java.util.Locale
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.pointrecord.PointRecordMainActivity


class CrewLineMainActivity : AppCompatActivity() {


    private lateinit var barChart: BarChart
    private val labels = listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00")

    private lateinit var handler: Handler
    private lateinit var timeRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_line_main)

        val timeTextView = findViewById<TextView>(R.id.timeTextView)

        val handler = Handler(Looper.getMainLooper())
        val timeRunnable = object : Runnable {
            override fun run() {
                val currentTime = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", Locale.getDefault()).format(Date())
                timeTextView.text = currentTime
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timeRunnable)


        barChart = findViewById(R.id.barChart)
        val tabLayout = findViewById<TabLayout>(R.id.tablayout)

        val imageButton1 = findViewById<ImageButton>(R.id.imageButton1)
        imageButton1.setOnClickListener {
            val intent = Intent(this, PointRecordMainActivity::class.java)
            startActivity(intent)

        }
        val recyclerView = findViewById<RecyclerView>(R.id.timelineRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val sampleData = listOf(
            WalkRecord("user1", "2025-04-10 10:10", "1.2km", "15분", "80kcal"),
            WalkRecord("user2", "2025-04-10 09:30", "2.1km", "25분", "130kcal"),
            WalkRecord("user3", "2025-04-10 10:30", "3.5km", "45분", "160kcal"),
            WalkRecord("user4", "2025-04-10 11:20", "1.5km", "20분", "90kcal"),
            WalkRecord("user5", "2025-04-11 11:20", "2.4km", "30분", "140kcal")
        ).sortedByDescending { it.getParsedTime() } // ⬅️ 최신순 정렬

        val sortedData = sampleData.sortedByDescending { it.getParsedTime() }


        val adapter = WalkRecordAdapter(sampleData)
        recyclerView.adapter = adapter





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
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable) // 꼭 해줘야 메모리 누수 방지됨
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
        BarEntry(0f, 33f), BarEntry(1f, 40f), BarEntry(2f, 20f),
        BarEntry(3f, 37f), BarEntry(4f, 15f), BarEntry(5f, 55f), BarEntry(6f, 60f)
    )

    private val monthLabels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30")
    private fun getMonthData() = listOf(
        BarEntry(0f, 33f), BarEntry(1f, 40f), BarEntry(2f, 20f), BarEntry(3f, 37f), BarEntry(4f, 15f), BarEntry(5f, 55f), BarEntry(6f, 60f),
        BarEntry(7f, 12f), BarEntry(8f, 23f), BarEntry(9f, 31f), BarEntry(10f, 18f), BarEntry(11f, 19f), BarEntry(12f, 55f), BarEntry(13f, 60f),
        BarEntry(14f, 20f), BarEntry(15f, 30f), BarEntry(16f, 40f), BarEntry(17f, 17f), BarEntry(18f, 35f), BarEntry(19f, 15f), BarEntry(20f, 70f),
        BarEntry(21f, 52f), BarEntry(22f, 33f), BarEntry(23f, 31f), BarEntry(24f, 18f), BarEntry(25f, 33f), BarEntry(26f, 55f), BarEntry(27f, 60f),
        BarEntry(28f, 17f), BarEntry(29f, 45f), BarEntry(30f, 15f)
    )}