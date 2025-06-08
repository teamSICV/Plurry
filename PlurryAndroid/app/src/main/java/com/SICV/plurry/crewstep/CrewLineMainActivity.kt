package com.SICV.plurry.crewstep

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.SICV.plurry.pointrecord.PointRecordMainActivity
import com.bumptech.glide.Glide
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.*

class CrewLineMainActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var handler: Handler
    private lateinit var timeRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_line_main)

        val timeTextView = findViewById<TextView>(R.id.timeTextView)
        val crewNameTextView = findViewById<TextView>(R.id.textView3)
        val pointButtonContainer = findViewById<LinearLayout>(R.id.pointButtonContainer)
        val crewId = intent.getStringExtra("crewId") ?: ""

        // 시간 표시 업데이트
        handler = Handler(Looper.getMainLooper())
        timeRunnable = object : Runnable {
            override fun run() {
                val currentTime = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", Locale.getDefault()).format(Date())
                timeTextView.text = currentTime
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timeRunnable)

        val db = FirebaseFirestore.getInstance()

        // 크루 이름 가져오기
        db.collection("Crew").document(crewId).get()
            .addOnSuccessListener { document ->
                val crewName = document.getString("name") ?: "크루"
                crewNameTextView.text = crewName
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "크루 이름 가져오기 실패", e)
            }

        // 크루 장소(crewPlace) 컬렉션 가져오기
        db.collection("Crew").document(crewId).collection("crewPlace").get()
            .addOnSuccessListener { querySnapshot ->
                pointButtonContainer.removeAllViews()

                for (doc in querySnapshot.documents) {
                    val placeId = doc.id
                    val isActive = doc.getBoolean(placeId) ?: false
                    if (!isActive) continue

                    Log.d("CrewLineMain", "활성화된 장소 ID: $placeId, isActive: $isActive")

                    // Places 컬렉션에서 이미지 URL 가져오기
                    db.collection("Places").document(placeId).get()
                        .addOnSuccessListener { placeDoc ->
                            val imageUrl = placeDoc.getString("myImgUrl")
                            Log.d("CrewLineMain", "placeId: $placeId, imageUrl: $imageUrl")

                            // ImageButton 생성
                            val imageButton = ImageButton(this)
                            val layoutParams = LinearLayout.LayoutParams(
                                (100 * resources.displayMetrics.density).toInt(),
                                (120 * resources.displayMetrics.density).toInt()
                            )
                            layoutParams.setMargins(0, 0, 16, 0)
                            imageButton.layoutParams = layoutParams
                            imageButton.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            imageButton.background = null

                            // 이미지 로드, 없으면 기본 이미지(basiccrewprofile) 사용
                            Glide.with(this)
                                .load(imageUrl)
                                .placeholder(R.drawable.placeholder)
                                .error(R.drawable.basiccrewprofile)
                                .into(imageButton)

                            // 버튼 클릭 시 동작 예시(원하면 삭제 가능)
                            imageButton.setOnClickListener {
                                // 예: 장소 상세 페이지 이동
                                Log.d("CrewLineMain", "장소 버튼 클릭: $placeId")
                            }

                            // 레이아웃에 추가
                            pointButtonContainer.addView(imageButton)
                        }
                        .addOnFailureListener {
                            Log.e("CrewLineMain", "Places/$placeId 문서 가져오기 실패", it)
                        }
                }
            }
            .addOnFailureListener {
                Log.e("CrewLineMain", "crewPlace 컬렉션 가져오기 실패", it)
            }

        // 나머지 UI 초기화 등 아래에 위치 (차트, 리사이클러뷰 등)

        barChart = findViewById(R.id.barChart)
        val tabLayout = findViewById<TabLayout>(R.id.tablayout)

        val imageButton1 = findViewById<ImageButton>(R.id.imageButton1)
        imageButton1.setOnClickListener {
            val intent = Intent(this, PointRecordMainActivity::class.java)
            startActivity(intent)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.timelineRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 여기 sampleData는 임시 테스트용 데이터
        val sampleData = listOf(
            WalkRecord("user1", "2025-04-10 10:10", "1.2km", "15분", "80kcal"),
            WalkRecord("user2", "2025-04-10 09:30", "2.1km", "25분", "130kcal"),
            WalkRecord("user3", "2025-04-10 10:30", "3.5km", "45분", "160kcal"),
            WalkRecord("user4", "2025-04-10 11:20", "1.5km", "20분", "90kcal"),
            WalkRecord("user5", "2025-04-11 11:20", "2.4km", "30분", "140kcal")
        ).sortedByDescending { it.getParsedTime() }

        recyclerView.adapter = WalkRecordAdapter(sampleData)

        // 초기 차트 데이터 설정
        setChartData(getDayData(), dayLabels, "시")

        // 탭 레이아웃 선택 리스너
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> setChartData(getDayData(), dayLabels, "시")
                    1 -> setChartData(getWeekData(), weekLabels, "요일")
                    2 -> setChartData(getMonthData(), monthLabels, "일")
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setChartData(entries: List<BarEntry>, labels: List<String>, labelName: String) {
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

        barChart.invalidate()
    }

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

    private val monthLabels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
        "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
        "21", "22", "23", "24", "25", "26", "27", "28", "29", "30")

    private fun getMonthData() = listOf(
        BarEntry(0f, 33f), BarEntry(1f, 40f), BarEntry(2f, 20f), BarEntry(3f, 37f),
        BarEntry(4f, 15f), BarEntry(5f, 55f), BarEntry(6f, 60f), BarEntry(7f, 12f),
        BarEntry(8f, 23f), BarEntry(9f, 31f), BarEntry(10f, 18f), BarEntry(11f, 19f),
        BarEntry(12f, 55f), BarEntry(13f, 60f), BarEntry(14f, 20f), BarEntry(15f, 30f),
        BarEntry(16f, 40f), BarEntry(17f, 17f), BarEntry(18f, 35f), BarEntry(19f, 15f),
        BarEntry(20f, 70f), BarEntry(21f, 52f), BarEntry(22f, 33f), BarEntry(23f, 31f),
        BarEntry(24f, 18f), BarEntry(25f, 33f), BarEntry(26f, 55f), BarEntry(27f, 60f),
        BarEntry(28f, 17f), BarEntry(29f, 45f), BarEntry(30f, 15f)
    )
}
