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
import com.SICV.plurry.pointrecord.PointRecordDialog
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
import androidx.core.location.LocationManagerCompat.getCurrentLocation
import com.SICV.plurry.MainActivity
import com.SICV.plurry.ranking.RankingMainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
    private lateinit var db: FirebaseFirestore

    private var crewWalkData = mutableListOf<WalkRecord>()
    private var currentCrewId = ""

    private val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
    private val koreaLocale = Locale.KOREA

    private var myLatitude: Double? = null
    private var myLongitude: Double? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private lateinit var refreshHandler: Handler
    private lateinit var refreshRunnable: Runnable
    private val REFRESH_INTERVAL = 5000L

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_line_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val timeTextView = findViewById<TextView>(R.id.timeTextView)
        val crewNameTextView = findViewById<TextView>(R.id.textView3)
        val pointButtonContainer = findViewById<LinearLayout>(R.id.pointButtonContainer)
        val joinCrewMemberTextView = findViewById<TextView>(R.id.joinCrewMember)
        val crewId = intent.getStringExtra("crewId") ?: ""
        currentCrewId = crewId
        val rankingButton = findViewById<ImageView>(R.id.rankingButton)
        val crewBackBtn = findViewById<ImageView>(R.id.crewBackButton)
        val exitCrewMemberTextView = findViewById<TextView>(R.id.ExitCrewMember)
        val morePointButton = findViewById<TextView>(R.id.morePointBtn)

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
        setupRefreshTimer()

        morePointButton.setOnClickListener{
            val intent = Intent(this, PointRecordMainActivity::class.java)
            startActivity(intent)
        }

        exitCrewMemberTextView.setOnClickListener {
            exitCrewMember(crewId, db)
        }

        rankingButton.setOnClickListener {
            val intent = Intent(this, RankingMainActivity::class.java)
            startActivity(intent)
        }

        crewBackBtn.setOnClickListener {
            checkCrewMembershipAndNavigate()
        }

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
                checkCrewMemberStatus(crewId, db, joinCrewMemberTextView, exitCrewMemberTextView)

            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "크루 이름 가져오기 실패", e)
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
            loadCrewPlaces(crewId, db, findViewById(R.id.pointButtonContainer))
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkAndRequestLocationPermission()
    }

    private fun checkCrewMembershipAndNavigate() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            val intent = Intent(this, CrewLineChooseActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
            return
        }

        val uid = currentUser.uid
        val crewId = intent.getStringExtra("crewId") ?: ""

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val crewAt = userDoc.getString("crewAt")

                    if (crewAt == crewId) {
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    } else {
                        val intent = Intent(this, CrewLineChooseActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
                } else {
                    val intent = Intent(this, CrewLineChooseActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "사용자 정보 확인 실패", e)
                val intent = Intent(this, CrewLineChooseActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
    }

    private fun showPlaceDetailDialog(imageUrl: String, name: String, description: String, placeId: String, lat: Double, lng: Double) {
        try {
            val dialog = PointRecordDialog.newInstance(imageUrl, name, description, placeId, lat, lng)
            dialog.show(supportFragmentManager, "PlaceDetailDialog")
        } catch (e: Exception) {
            Log.e("CrewLineMain", "팝업 다이얼로그 표시 오류", e)
            Toast.makeText(this, "장소 정보를 표시할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
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
                        val joinTime = com.google.firebase.Timestamp.now()
                        val updates = hashMapOf<String, Any>(
                            "crewAt" to crewId,
                            "crewAtTime" to joinTime
                        )
                        db.collection("Users").document(uid).update(updates)
                            .addOnSuccessListener {
                                Log.d("CrewLineMain", "사용자 crewAt 필드 업데이트 완료")
                                addUserPlacesToCrew(crewId, uid, db)
                            }
                            .addOnFailureListener { e ->
                                Log.e("CrewLineMain", "사용자 crewAt 필드 업데이트 실패", e)
                            }
                        val joinTextView = findViewById<TextView>(R.id.joinCrewMember)
                        val exitTextView = findViewById<TextView>(R.id.ExitCrewMember)
                        joinTextView.visibility = android.view.View.GONE
                        exitTextView.visibility = android.view.View.VISIBLE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "크루 가입에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "크루 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkCrewMemberStatus(crewId: String, db: FirebaseFirestore,
                                      joinTextView: TextView, exitTextView: TextView) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            joinTextView.visibility = android.view.View.VISIBLE
            exitTextView.visibility = android.view.View.GONE
            return
        }

        val uid = currentUser.uid

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val crewAt = userDoc.getString("crewAt")

                    if (crewAt == crewId) {
                        joinTextView.visibility = android.view.View.GONE
                        exitTextView.visibility = android.view.View.VISIBLE
                    } else {
                        joinTextView.visibility = android.view.View.VISIBLE
                        exitTextView.visibility = android.view.View.GONE
                    }
                } else {
                    joinTextView.visibility = android.view.View.VISIBLE
                    exitTextView.visibility = android.view.View.GONE
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "사용자 정보 확인 실패", e)
                joinTextView.visibility = android.view.View.VISIBLE
                exitTextView.visibility = android.view.View.GONE
            }
    }

    private fun exitCrewMember(crewId: String, db: FirebaseFirestore) {
        val exitDialog = CrewExit(this) {
            performExitCrew(crewId, db)
        }
        exitDialog.show()
    }

    private fun performExitCrew(crewId: String, db: FirebaseFirestore) {
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

                if (!memberData.containsKey(uid)) {
                    Toast.makeText(this, "크루 멤버가 아닙니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                memberData.remove(uid)

                db.collection("Crew").document(crewId).collection("member").document("members")
                    .set(memberData)
                    .addOnSuccessListener {
                        removeFromCrewReward(crewId, uid, db)
                        removeUserPlacesFromCrew(crewId, uid, db)
                        Toast.makeText(this, "크루에서 성공적으로 탈퇴했습니다.", Toast.LENGTH_SHORT).show()

                        val updates = hashMapOf<String, Any>(
                            "crewAt" to com.google.firebase.firestore.FieldValue.delete(),
                            "crewAtTime" to com.google.firebase.firestore.FieldValue.delete()
                        )

                        db.collection("Users").document(uid).update(updates)
                            .addOnSuccessListener {
                                Log.d("CrewLineMain", "사용자 crewAt 필드 제거 완료")

                                val intent = Intent(this@CrewLineMainActivity, CrewLineChooseActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Log.e("CrewLineMain", "사용자 crewAt 필드 제거 실패", e)
                            }

                        val joinTextView = findViewById<TextView>(R.id.joinCrewMember)
                        val exitTextView = findViewById<TextView>(R.id.ExitCrewMember)
                        joinTextView.visibility = android.view.View.VISIBLE
                        exitTextView.visibility = android.view.View.GONE

                        loadCrewWalkRecords(crewId, db)
                        loadCrewPlaces(crewId, db, findViewById(R.id.pointButtonContainer))
                    }
                    .addOnFailureListener { e ->
                        Log.e("CrewLineMain", "크루 탈퇴 실패", e)
                        Toast.makeText(this, "크루 탈퇴에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "멤버 정보 확인 실패", e)
                Toast.makeText(this, "크루 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun removeFromCrewReward(crewId: String, uid: String, db: FirebaseFirestore) {
        db.collection("Game").document("users").collection("userReward").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    db.collection("Game").document("users").collection("userReward").document(uid)
                        .update("crewRewardItem", null)
                        .addOnSuccessListener {
                            Log.d("CrewLineMain", "userReward에서 crewRewardItem 제거 완료")
                        }
                        .addOnFailureListener { e ->
                            Log.e("CrewLineMain", "userReward에서 crewRewardItem 제거 실패", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "userReward 문서 확인 실패", e)
            }
    }

    private fun loadCrewPlaces(crewId: String, db: FirebaseFirestore, container: LinearLayout) {
        db.collection("Crew").document(crewId).collection("crewPlace").get()
            .addOnSuccessListener { querySnapshot ->
                val activePlaces = mutableListOf<Triple<String, Boolean, Long>>()

                for (doc in querySnapshot.documents) {
                    val placeId = doc.id
                    val isActive = doc.getBoolean(placeId) ?: false
                    val imageTime = doc.getLong("imageTime") ?: 0L

                    if (isActive) {
                        activePlaces.add(Triple(placeId, isActive, imageTime))
                    }
                }

                val sortedPlaces = activePlaces.sortedByDescending { it.third }.take(10)

                container.removeAllViews()
                for ((index, place) in sortedPlaces.withIndex()) {
                    val placeId = place.first
                    loadAndAddPlaceImageOptimized(placeId, db, container, index)
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "crewPlace 컬렉션 가져오기 실패", e)
            }
    }

    private fun isPlaceAlreadyInContainer(container: LinearLayout, placeId: String): Boolean {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val existingPlaceId = child.contentDescription?.toString()?.removePrefix("visited_place_")
            if (existingPlaceId == placeId) {
                return true
            }
        }
        return false
    }

    private fun loadAndAddPlaceImage(placeId: String, db: FirebaseFirestore, container: LinearLayout) {
        db.collection("Places").document(placeId).get()
            .addOnSuccessListener { placeDoc ->
                if (placeDoc.exists()) {
                    val imageUrl = placeDoc.getString("myImgUrl") ?: ""
                    val placeName = placeDoc.getString("name") ?: "장소 이름 없음"
                    val addedBy = placeDoc.getString("addedBy") ?: "알 수 없음"
                    val geoPoint = placeDoc.getGeoPoint("geo")
                    val lat = geoPoint?.latitude ?: 0.0
                    val lng = geoPoint?.longitude ?: 0.0

                    val distanceText = if (geoPoint != null && myLatitude != null && myLongitude != null) {
                        val distance = calculateDistance(myLatitude!!, myLongitude!!, geoPoint.latitude, geoPoint.longitude)
                        String.format("%.2f", distance) + "km"
                    } else {
                        "거리 계산 불가"
                    }

                    val detailInfo = "추가한 유저: $addedBy\n거리: $distanceText"

                    if (imageUrl.isNotEmpty()) {
                        addPlaceImageToContainer(imageUrl, placeId, "장소: $placeName", detailInfo, container, lat, lng)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "Places/$placeId 문서 가져오기 실패", e)
            }
    }

    private fun addPlaceImageToContainer(imageUrl: String, placeId: String, name: String, detailInfo: String, container: LinearLayout, lat: Double, lng: Double) {
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
            showPlaceDetailDialog(imageUrl, name, detailInfo, placeId, lat, lng)
        }
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
                    val crewAtTime = if (userDoc.exists()) {
                        userDoc.getTimestamp("crewAtTime")?.toDate()?.time
                    } else {
                        null
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
                                        val shouldInclude = if (crewAtTime != null) {
                                            endTime >= crewAtTime
                                        } else {
                                            true
                                        }

                                        if (shouldInclude) {
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

    private fun addUserPlacesToCrew(crewId: String, uid: String, db: FirebaseFirestore) {
        db.collection("Crew").document(crewId).collection("crewPlace").get()
            .addOnSuccessListener { existingPlaces ->
                val existingPlaceIds = existingPlaces.documents.map { it.id }.toSet()

                db.collection("Places")
                    .whereEqualTo("addedBy", uid)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        val batch = db.batch()
                        var newPlaceCount = 0

                        for (document in querySnapshot.documents) {
                            val placeId = document.id
                            if (!existingPlaceIds.contains(placeId)) {
                                val imageTime = System.currentTimeMillis()
                                val crewPlaceRef = db.collection("Crew").document(crewId)
                                    .collection("crewPlace").document(placeId)
                                batch.set(crewPlaceRef, mapOf(
                                    placeId to true,
                                    "imageTime" to imageTime
                                ))
                                newPlaceCount++
                            }
                        }

                        if (newPlaceCount > 0) {
                            batch.commit()
                                .addOnSuccessListener {
                                    Log.d("CrewLineMain", "사용자($uid) 새 장소 ${newPlaceCount}개가 크루에 추가되었습니다.")
                                    loadCrewPlaces(crewId, db, findViewById(R.id.pointButtonContainer))
                                }
                                .addOnFailureListener { e ->
                                    Log.e("CrewLineMain", "사용자 장소 추가 실패", e)
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CrewLineMain", "사용자 장소 조회 실패", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "기존 crewPlace 조회 실패", e)
            }
    }

    private fun removeUserPlacesFromCrew(crewId: String, uid: String, db: FirebaseFirestore) {
        db.collection("Places")
            .whereEqualTo("addedBy", uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch()

                for (document in querySnapshot.documents) {
                    val placeId = document.id
                    val crewPlaceRef = db.collection("Crew").document(crewId)
                        .collection("crewPlace").document(placeId)
                    batch.delete(crewPlaceRef)
                }

                batch.commit()
                    .addOnSuccessListener {
                        Log.d("CrewLineMain", "사용자 장소들이 크루에서 제거되었습니다.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("CrewLineMain", "사용자 장소 제거 실패", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "사용자 장소 조회 실패", e)
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
                        if (index >= 0 && index < maxDayOfMonth) {
                            val distance = record.distance.replace("km", "").toFloatOrNull() ?: 0f
                            monthlyData[index] += distance
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CrewLineMain", "월간 데이터 파싱 오류: ${record.time}", e)
            }
        }

        return monthlyData.mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
    }

    private fun checkAndRequestLocationPermission() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation { success ->
                if (success) {
                    val crewId = intent.getStringExtra("crewId") ?: ""
                    if (crewId.isNotEmpty()) {
                        loadCrewWalkRecords(crewId, db)
                    }
                }
            }
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun getCurrentLocation(callback: (Boolean) -> Unit) {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    myLatitude = location.latitude
                    myLongitude = location.longitude
                    Log.d("CrewLineMain", "현재 위치: $myLatitude, $myLongitude")
                    callback(true)
                } else {
                    Log.w("CrewLineMain", "위치 정보를 가져올 수 없습니다.")
                    callback(false)
                }
            }.addOnFailureListener { e ->
                Log.e("CrewLineMain", "위치 정보 가져오기 실패", e)
                callback(false)
            }
        } else {
            callback(false)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation { success ->
                    if (success) {
                        val crewId = intent.getStringExtra("crewId") ?: ""
                        if (crewId.isNotEmpty()) {
                            loadCrewWalkRecords(crewId, FirebaseFirestore.getInstance())
                            loadCrewPlaces(crewId, FirebaseFirestore.getInstance(), findViewById(R.id.pointButtonContainer))
                        }
                    }
                }
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                val crewId = intent.getStringExtra("crewId") ?: ""
                if (crewId.isNotEmpty()) {
                    loadCrewWalkRecords(crewId, FirebaseFirestore.getInstance())
                }
            }
        }
    }

    private fun syncAllMemberPlacesToCrew(crewId: String, db: FirebaseFirestore) {
        db.collection("Crew").document(crewId).collection("member").document("members").get()
            .addOnSuccessListener { memberDoc ->
                if (memberDoc.exists()) {
                    val memberData = memberDoc.data
                    if (memberData != null && memberData.isNotEmpty()) {
                        val memberUids = memberData.keys.toList()

                        for (uid in memberUids) {
                            addUserPlacesToCrew(crewId, uid, db)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "크루 멤버 동기화 실패", e)
            }
    }

    private fun checkAndUpdatePlaces(crewId: String, db: FirebaseFirestore, container: LinearLayout) {
        db.collection("Crew").document(crewId).collection("crewPlace").get()
            .addOnSuccessListener { querySnapshot ->
                val activePlaces = mutableListOf<Triple<String, Boolean, Long>>()

                for (doc in querySnapshot.documents) {
                    val placeId = doc.id
                    val isActive = doc.getBoolean(placeId) ?: false
                    val imageTime = doc.getLong("imageTime") ?: 0L

                    if (isActive) {
                        activePlaces.add(Triple(placeId, isActive, imageTime))
                    }
                }

                val sortedPlaces = activePlaces.sortedByDescending { it.third }.take(10)
                val newPlaceIds = sortedPlaces.map { it.first }.toSet()

                val currentPlaceIds = mutableSetOf<String>()
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    val placeId = child.contentDescription?.toString()?.removePrefix("visited_place_")
                    if (placeId != null) {
                        currentPlaceIds.add(placeId)
                    }
                }

                for (place in sortedPlaces) {
                    val placeId = place.first
                    if (!currentPlaceIds.contains(placeId)) {
                        loadAndAddPlaceImageOptimized(placeId, db, container, sortedPlaces.indexOf(place))
                    }
                }

                for (i in container.childCount - 1 downTo 0) {
                    val child = container.getChildAt(i)
                    val placeId = child.contentDescription?.toString()?.removePrefix("visited_place_")
                    if (placeId != null && !newPlaceIds.contains(placeId)) {
                        container.removeViewAt(i)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "crewPlace 확인 실패", e)
            }
    }

    private fun loadAndAddPlaceImageOptimized(placeId: String, db: FirebaseFirestore, container: LinearLayout, position: Int) {
        db.collection("Places").document(placeId).get()
            .addOnSuccessListener { placeDoc ->
                if (placeDoc.exists()) {
                    val imageUrl = placeDoc.getString("myImgUrl") ?: ""
                    val placeName = placeDoc.getString("name") ?: "장소 이름 없음"
                    val addedBy = placeDoc.getString("addedBy") ?: "알 수 없음"
                    val geoPoint = placeDoc.getGeoPoint("geo")
                    val lat = geoPoint?.latitude ?: 0.0
                    val lng = geoPoint?.longitude ?: 0.0

                    val distanceText = if (geoPoint != null && myLatitude != null && myLongitude != null) {
                        val distance = calculateDistance(myLatitude!!, myLongitude!!, geoPoint.latitude, geoPoint.longitude)
                        String.format("%.2f", distance) + "km"
                    } else {
                        "거리 계산 불가"
                    }

                    val detailInfo = "추가한 유저: $addedBy\n거리: $distanceText"

                    if (imageUrl.isNotEmpty()) {
                        addPlaceImageToContainerAtPosition(imageUrl, placeId, "장소: $placeName", detailInfo, container, position, lat, lng)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "Places/$placeId 문서 가져오기 실패", e)
            }
    }

    private fun addPlaceImageToContainerAtPosition(imageUrl: String, placeId: String, name: String, detailInfo: String, container: LinearLayout, position: Int, lat: Double, lng: Double) {
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
            showPlaceDetailDialog(imageUrl, name, detailInfo, placeId, lat, lng)
        }

        if (position < container.childCount) {
            container.addView(imageButton, position)
        } else {
            container.addView(imageButton)
        }
    }

    private fun setupRefreshTimer() {
        refreshHandler = Handler(Looper.getMainLooper())
        refreshRunnable = object : Runnable {
            override fun run() {
                refreshCrewData()
                refreshHandler.postDelayed(this, REFRESH_INTERVAL)
            }
        }
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL)
    }

    private fun refreshCrewData() {
        val crewId = intent.getStringExtra("crewId") ?: ""
        if (crewId.isNotEmpty()) {
            Log.d("CrewLineMain", "5초 새로고침 실행")
            loadCrewWalkRecords(crewId, db)
            syncAllMemberPlacesToCrew(crewId, db)
            checkAndUpdatePlaces(crewId, db, findViewById(R.id.pointButtonContainer))
        }
    }

    override fun onPause() {
        super.onPause()
        if (::refreshHandler.isInitialized) {
            refreshHandler.removeCallbacks(refreshRunnable)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::refreshHandler.isInitialized) {
            refreshHandler.removeCallbacks(refreshRunnable)
            refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable)

        if (::refreshHandler.isInitialized) {
            refreshHandler.removeCallbacks(refreshRunnable)
        }
    }
}