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
import android.widget.ImageView
import android.widget.Toast
import com.SICV.plurry.ranking.RankingMainActivity
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
    private var crewWalkData = mutableListOf<WalkRecord>()
    private var currentCrewId = ""

    private val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
    private val koreaLocale = Locale.KOREA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_line_main)

        auth = FirebaseAuth.getInstance()

        val timeTextView = findViewById<TextView>(R.id.timeTextView)
        val crewNameTextView = findViewById<TextView>(R.id.textView3)
        val pointButtonContainer = findViewById<LinearLayout>(R.id.pointButtonContainer)
        val joinCrewMemberTextView = findViewById<TextView>(R.id.joinCrewMember)
        val crewId = intent.getStringExtra("crewId") ?: ""
        currentCrewId = crewId
        val rankingButton = findViewById<ImageView>(R.id.rankingButton)

        handler = Handler(Looper.getMainLooper())
        timeRunnable = object : Runnable {
            override fun run() {
                val calendar = Calendar.getInstance(koreaTimeZone, koreaLocale)
                val currentTime = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", koreaLocale).apply {
                    timeZone = koreaTimeZone
                }.format(calendar.time)
                timeTextView.text = currentTime
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timeRunnable)

        rankingButton.setOnClickListener {
            val intent = Intent(this, RankingMainActivity::class.java)
            startActivity(intent)
        }

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
            WalkRecord("로딩 중...", "${getKoreaTimeString()}", "0.0km", "0분", "0kcal")
        )
        walkRecordAdapter = WalkRecordAdapter(loadingData)
        recyclerView.adapter = walkRecordAdapter

        if (crewId.isNotEmpty()) {
            loadCrewWalkRecords(crewId, db)
            loadVisitedPlaces(crewId, db, pointButtonContainer)
        } else {
            updateRecyclerView(emptyList())
        }

        setChartData(listOf(), getDayLabels(), "시간대별")

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> setChartData(getDayData(), getDayLabels(), "시간대별")
                    1 -> setChartData(getWeekData(), getWeekLabels(), "요일별")
                    2 -> setChartData(getMonthData(), getMonthLabels(), "일별")
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun getKoreaTimeString(): String {
        val calendar = Calendar.getInstance(koreaTimeZone, koreaLocale)
        return SimpleDateFormat("yy-MM-dd HH:mm", koreaLocale).apply {
            timeZone = koreaTimeZone
        }.format(calendar.time)
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
                    }

                    completedRequests++
                    if (completedRequests == memberUids.size) {
                        updateVisitedPlacesUI(visitedPlaces, container)
                    }
                }
                .addOnFailureListener { e ->
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

        container.addView(imageButton)
    }

    private fun loadCrewWalkRecords(crewId: String, db: FirebaseFirestore) {

        if (crewId.isEmpty()) {
            updateRecyclerView(emptyList())
            return
        }

        val memberPath = "Crew/$crewId/member/members"

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

                                        val endTimeFormatted = SimpleDateFormat("yy-MM-dd HH:mm", koreaLocale).apply {
                                            timeZone = koreaTimeZone
                                        }.format(Date(endTime))

                                        val walkRecord = WalkRecord(
                                            userName,
                                            endTimeFormatted,
                                            distanceFormatted,
                                            walkDuration,
                                            "${calories}kcal"
                                        )

                                        walkRecords.add(walkRecord)
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
        crewWalkData.clear()
        crewWalkData.addAll(walkRecords)

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
                listOf(WalkRecord("데이터 없음", "${getKoreaTimeString()}", "0.0km", "0분", "0kcal"))
            }

            runOnUiThread {
                walkRecordAdapter = WalkRecordAdapter(sortedRecords)
                recyclerView.adapter = walkRecordAdapter

                refreshCurrentChart()
            }
        } catch (e: Exception) {
            runOnUiThread {
                val errorRecord = listOf(WalkRecord("오류 발생", "${getKoreaTimeString()}", "0.0km", "0분", "0kcal"))
                walkRecordAdapter = WalkRecordAdapter(errorRecord)
                recyclerView.adapter = walkRecordAdapter
            }
        }
    }

    private fun refreshCurrentChart() {
        val currentTab = findViewById<TabLayout>(R.id.tablayout).selectedTabPosition
        when (currentTab) {
            0 -> setChartData(getDayData(), getDayLabels(), "시간대별")
            1 -> setChartData(getWeekData(), getWeekLabels(), "요일별")
            2 -> setChartData(getMonthData(), getMonthLabels(), "일별")
            else -> setChartData(getDayData(), getDayLabels(), "시간대별")
        }
    }

    private fun setChartData(entries: List<BarEntry>, labels: List<String>, labelName: String) {
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

    private fun getDayLabels() = listOf("0-4", "4-8", "8-12", "12-16", "16-20", "20-24")

    private fun getDayData(): List<BarEntry> {
        val timeSlots = FloatArray(6) { 0f }

        for (record in crewWalkData) {
            try {
                val time = record.getParsedTime()
                if (time != null) {
                    val calendar = Calendar.getInstance(koreaTimeZone, koreaLocale)
                    calendar.time = time
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
            } catch (e: Exception) {
                Log.e("CrewLineMain", "시간 파싱 오류: ${record.time}", e)
            }
        }

        return timeSlots.mapIndexed { index, value ->
            BarEntry(index.toFloat(), value)
        }
    }

    private fun getWeekLabels() = listOf("월", "화", "수", "목", "금", "토", "일")

    private fun getWeekData(): List<BarEntry> {
        val weeklyData = FloatArray(7) { 0f }

        for (record in crewWalkData) {
            try {
                val time = record.getParsedTime()
                if (time != null) {
                    val calendar = Calendar.getInstance(koreaTimeZone, koreaLocale)
                    calendar.time = time
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

                    val dayName = when(dayOfWeek) {
                        Calendar.SUNDAY -> "일요일"
                        Calendar.MONDAY -> "월요일"
                        Calendar.TUESDAY -> "화요일"
                        Calendar.WEDNESDAY -> "수요일"
                        Calendar.THURSDAY -> "목요일"
                        Calendar.FRIDAY -> "금요일"
                        Calendar.SATURDAY -> "토요일"
                        else -> "알수없음"
                    }
                }
            } catch (e: Exception) {
                Log.e("CrewLineMain", "요일 파싱 오류: ${record.time}", e)
            }
        }

        return weeklyData.mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
    }


    private fun getMonthLabels(): List<String> {
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

    private fun getMonthData(): List<BarEntry> {
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
                        val distance = record.distance.replace("km", "").toFloatOrNull() ?: 0f
                        monthlyData[index] += distance

                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", koreaLocale)
                        dateFormat.timeZone = koreaTimeZone
                    }
                }
            } catch (e: Exception) {
                Log.e("CrewLineMain", "월간 데이터 파싱 오류: ${record.time}", e)
            }
        }

        return monthlyData.mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable)
    }
}