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
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class CrewLineMainActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var handler: Handler
    private lateinit var timeRunnable: Runnable
    private lateinit var recyclerView: RecyclerView
    private lateinit var walkRecordAdapter: WalkRecordAdapter
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_line_main)

        auth = FirebaseAuth.getInstance()

        val timeTextView = findViewById<TextView>(R.id.timeTextView)
        val crewNameTextView = findViewById<TextView>(R.id.textView3)
        val pointButtonContainer = findViewById<LinearLayout>(R.id.pointButtonContainer)
        val joinCrewMemberTextView = findViewById<TextView>(R.id.joinCrewMember)
        val crewId = intent.getStringExtra("crewId") ?: ""

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

        joinCrewMemberTextView.setOnClickListener {
            joinCrewMember(crewId, db)
        }

        db.collection("Crew").document(crewId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    Log.d("CrewLineMain", "크루 문서 데이터: ${doc.data}")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "Firebase 연결 실패", e)
            }

        db.collection("Crew").document(crewId).get()
            .addOnSuccessListener { document ->
                val crewName = document.getString("name") ?: "크루"
                crewNameTextView.text = crewName
                Log.d("CrewLineMain", "크루 이름 설정: $crewName")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "크루 이름 가져오기 실패", e)
            }

        db.collection("Crew").document(crewId).collection("crewPlace").get()
            .addOnSuccessListener { querySnapshot ->
                pointButtonContainer.removeAllViews()

                for (doc in querySnapshot.documents) {
                    val placeId = doc.id
                    val isActive = doc.getBoolean(placeId) ?: false
                    if (!isActive) continue

                    db.collection("Places").document(placeId).get()
                        .addOnSuccessListener { placeDoc ->
                            val imageUrl = placeDoc.getString("myImgUrl")
                            val imageButton = ImageButton(this)
                            val layoutParams = LinearLayout.LayoutParams(
                                (100 * resources.displayMetrics.density).toInt(),
                                (120 * resources.displayMetrics.density).toInt()
                            )
                            layoutParams.setMargins(0, 0, 16, 0)
                            imageButton.layoutParams = layoutParams
                            imageButton.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            imageButton.background = null

                            Glide.with(this)
                                .load(imageUrl)
                                .placeholder(R.drawable.placeholder)
                                .error(R.drawable.basiccrewprofile)
                                .into(imageButton)

                            imageButton.setOnClickListener {
                                Log.d("CrewLineMain", "장소 버튼 클릭: $placeId")
                            }

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

        barChart = findViewById(R.id.barChart)
        val tabLayout = findViewById<TabLayout>(R.id.tablayout)

        val imageButton1 = findViewById<ImageButton>(R.id.imageButton1)
        imageButton1.setOnClickListener {
            val intent = Intent(this, PointRecordMainActivity::class.java)
            startActivity(intent)
        }

        recyclerView = findViewById<RecyclerView>(R.id.timelineRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val loadingData = listOf(
            WalkRecord("로딩 중...", "${SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", "0.0km", "0분", "0kcal")
        )
        walkRecordAdapter = WalkRecordAdapter(loadingData)
        recyclerView.adapter = walkRecordAdapter

        if (crewId.isNotEmpty()) {
            loadCrewWalkRecords(crewId, db)
            loadVisitedPlaces(crewId, db, pointButtonContainer)
        } else {
            updateRecyclerView(emptyList())
        }

        setChartData(getDayData(), dayLabels, "시")

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

    private fun joinCrewMember(crewId: String, db: FirebaseFirestore) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (crewId.isEmpty()) {
            Toast.makeText(this, "크루 ID가 유효하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = currentUser.uid

        db.collection("Crew").document(crewId).collection("member").document("members").get()
            .addOnSuccessListener { document ->
                val memberData = document.data?.toMutableMap() ?: mutableMapOf<String, Any>()

                if (memberData.containsKey(uid)) {
                    Toast.makeText(this, "이미 크루 멤버입니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val joinTime = com.google.firebase.Timestamp.now()
                memberData[uid] = joinTime

                db.collection("Crew").document(crewId).collection("member").document("members")
                    .set(memberData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "크루에 성공적으로 가입했습니다!", Toast.LENGTH_SHORT).show()

                        loadCrewWalkRecords(crewId, db)
                        loadVisitedPlaces(crewId, db, findViewById(R.id.pointButtonContainer))
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "크루 가입에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "크루 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadVisitedPlaces(crewId: String, db: FirebaseFirestore, container: LinearLayout) {
        Log.d("CrewLineMain", "방문한 장소 로드 시작")

        val childCount = container.childCount
        for (i in childCount - 1 downTo 1) {
            container.removeViewAt(i)
        }

        db.collection("Crew").document(crewId).collection("member").document("members").get()
            .addOnSuccessListener { memberDoc ->
                if (memberDoc.exists()) {
                    val memberData = memberDoc.data
                    if (memberData != null && memberData.isNotEmpty()) {
                        val memberUids = memberData.keys.toList()
                        fetchVisitedPlacesForMembers(memberUids, db, container)
                    } else {
                        Log.w("CrewLineMain", "멤버 데이터가 빔")
                    }
                } else {
                    Log.w("CrewLineMain", "멤버 문서 존재x")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "멤버 정보 가져오기 실패", e)
            }
    }

    data class VisitedPlace(
        val placeId: String,
        val visitTime: Long,
        val imageUrl: String
    )

    private fun fetchVisitedPlacesForMembers(memberUids: List<String>, db: FirebaseFirestore, container: LinearLayout) {
        val visitedPlaces = mutableListOf<VisitedPlace>()
        var completedRequests = 0

        for (uid in memberUids) {
            db.collection("Users").document(uid).collection("walk").document("visitedPlace").get()
                .addOnSuccessListener { visitedDoc ->
                    if (visitedDoc.exists()) {
                        val visitedData = visitedDoc.data

                        visitedData?.forEach { (placeId, visitTimeData) ->
                            if (placeId.isNotEmpty()) {
                                val visitTime = try {
                                    when (visitTimeData) {
                                        is com.google.firebase.Timestamp -> visitTimeData.toDate().time
                                        is Long -> visitTimeData
                                        else -> System.currentTimeMillis()
                                    }
                                } catch (e: Exception) {
                                    System.currentTimeMillis()
                                }

                                db.collection("Places").document(placeId).get()
                                    .addOnSuccessListener { placeDoc ->
                                        if (placeDoc.exists()) {
                                            val imageUrl = placeDoc.getString("myImgUrl") ?: placeDoc.getString("imageUrl") ?: ""
                                            if (imageUrl.isNotEmpty()) {
                                                visitedPlaces.add(VisitedPlace(placeId, visitTime, imageUrl))
                                            }
                                        }

                                        updateVisitedPlacesUI(visitedPlaces, container)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("CrewLineMain", "장소 $placeId 정보 가져오기 실패", e)
                                    }
                            }
                        }
                    } else {
                        Log.w("CrewLineMain", "사용자 $uid 의 visitedPlace 문서가 존재하지 않습니다.")
                    }

                    completedRequests++
                    if (completedRequests == memberUids.size) {
                        updateVisitedPlacesUI(visitedPlaces, container)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CrewLineMain", "사용자 $uid 방문 장소 가져오기 실패", e)
                    completedRequests++
                    if (completedRequests == memberUids.size) {
                        updateVisitedPlacesUI(visitedPlaces, container)
                    }
                }
        }
    }

    private fun updateVisitedPlacesUI(visitedPlaces: MutableList<VisitedPlace>, container: LinearLayout) {
        val uniquePlaces = visitedPlaces
            .groupBy { it.placeId }
            .map { (_, places) -> places.maxByOrNull { it.visitTime }!! }
            .sortedByDescending { it.visitTime }

        val childCount = container.childCount
        for (i in childCount - 1 downTo 1) {
            container.removeViewAt(i)
        }

        for (place in uniquePlaces) {
            addPlaceImageToContainer(place.imageUrl, place.placeId, container)
        }

        Log.d("CrewLineMain", "방문한 장소 UI 업데이트 완료. 총 ${uniquePlaces.size}개 장소")
    }

    private fun addPlaceImageToContainer(imageUrl: String, placeId: String, container: LinearLayout) {
        val imageButton = ImageButton(this)
        val layoutParams = LinearLayout.LayoutParams(
            (100 * resources.displayMetrics.density).toInt(),
            (120 * resources.displayMetrics.density).toInt()
        )
        layoutParams.setMargins(0, 0, 8, 0)
        imageButton.layoutParams = layoutParams
        imageButton.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        imageButton.background = null
        imageButton.contentDescription = "visited_place_$placeId"

        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.basiccrewprofile)
            .into(imageButton)

        imageButton.setOnClickListener {
            Log.d("CrewLineMain", "방문한 장소 클릭: $placeId")
        }

        container.addView(imageButton)
        Log.d("CrewLineMain", "방문한 장소 이미지 추가됨: $placeId")
    }

    private fun loadCrewWalkRecords(crewId: String, db: FirebaseFirestore) {

        if (crewId.isEmpty()) {
            updateRecyclerView(emptyList())
            return
        }

        val memberPath = "Crew/$crewId/member/members"
        Log.d("CrewLineMain", "멤버 문서 경로: $memberPath")

        db.collection("Crew").document(crewId).collection("member").document("members").get()
            .addOnSuccessListener { memberDoc ->

                if (memberDoc.exists()) {
                    val memberData = memberDoc.data

                    if (memberData != null && memberData.isNotEmpty()) {
                        val memberUids = memberData.keys.toList()
                        fetchWalkRecordsForMembers(memberUids, db)
                    } else {
                        updateRecyclerView(emptyList())
                    }
                } else {
                    updateRecyclerView(emptyList())
                }
            }
            .addOnFailureListener { e ->
                updateRecyclerView(emptyList())
            }
    }

    private fun fetchWalkRecordsForMembers(memberUids: List<String>, db: FirebaseFirestore) {
        val walkRecords = mutableListOf<WalkRecord>()
        var completedRequests = 0

        if (memberUids.isEmpty()) {
            updateRecyclerView(emptyList())
            return
        }

        for (uid in memberUids) {

            db.collection("Users").document(uid).get()
                .addOnSuccessListener { userDoc ->
                    val userName = if (userDoc.exists()) {
                        val name = userDoc.getString("name") ?: uid
                        name
                    } else {
                        uid
                    }

                    db.collection("Users").document(uid).collection("goWalk").get()
                        .addOnSuccessListener { walkDocs ->

                            if (walkDocs.isEmpty) {
                                Log.w("CrewLineMain", "goWalk 비어있음")
                            }

                            for (walkDoc in walkDocs.documents) {
                                try {

                                    val calories = walkDoc.getLong("calories") ?: 0L
                                    val distance = walkDoc.getDouble("distance") ?: 0.0
                                    val startTime = try {
                                        walkDoc.getTimestamp("startTime")?.toDate()?.time ?: 0L
                                    } catch (e: Exception) {
                                        0L
                                    }

                                    val endTime = try {
                                        walkDoc.getTimestamp("endTime")?.toDate()?.time ?: 0L
                                    } catch (e: Exception) {
                                        0L
                                    }

                                    if (endTime > 0 && distance > 0) {
                                        val distanceFormatted = String.format("%.1fkm", distance)
                                        val walkDuration = if (endTime > startTime && startTime > 0) {
                                            val durationMinutes = (endTime - startTime) / (1000 * 60)
                                            "${durationMinutes}분"
                                        } else {
                                            "0분"
                                        }

                                        val endTimeFormatted = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
                                            .format(Date(endTime))

                                        val walkRecord = WalkRecord(
                                            userName,
                                            endTimeFormatted,
                                            distanceFormatted,
                                            walkDuration,
                                            "${calories}kcal"
                                        )

                                        walkRecords.add(walkRecord)
                                    } else {
                                        Log.w("CrewLineMain", "무효 데이터 스킵 - endTime: $endTime, distance: $distance")
                                    }
                                } catch (e: Exception) {
                                    Log.e("CrewLineMain", "문서 파싱 오류: ${walkDoc.id}", e)
                                }
                            }

                            completedRequests++

                            if (completedRequests == memberUids.size) {
                                updateRecyclerView(walkRecords)
                            }
                        }
                        .addOnFailureListener { e ->
                            completedRequests++

                            if (completedRequests == memberUids.size) {
                                updateRecyclerView(walkRecords)
                            }
                        }
                }
                .addOnFailureListener { e ->
                    completedRequests++

                    if (completedRequests == memberUids.size) {
                        updateRecyclerView(walkRecords)
                    }
                }
        }
    }

    private fun updateRecyclerView(walkRecords: List<WalkRecord>) {

        walkRecords.forEachIndexed { index, record ->
        }

        try {
            val sortedRecords = if (walkRecords.isNotEmpty()) {
                walkRecords.sortedByDescending {
                    try {
                        it.getParsedTime()?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
            } else {
                listOf(WalkRecord("데이터 없음", "${SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", "0.0km", "0분", "0kcal"))
            }

            runOnUiThread {
                walkRecordAdapter = WalkRecordAdapter(sortedRecords)
                recyclerView.adapter = walkRecordAdapter
            }
        } catch (e: Exception) {
            runOnUiThread {
                val errorRecord = listOf(WalkRecord("오류 발생", "${SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", "0.0km", "0분", "0kcal"))
                walkRecordAdapter = WalkRecordAdapter(errorRecord)
                recyclerView.adapter = walkRecordAdapter
            }
        }
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable)
    }
}