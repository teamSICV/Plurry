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

class CrewLineMainActivity : AppCompatActivity(), CrewWalkManager.WalkDataUpdateListener {

    private lateinit var barChart: BarChart
    private lateinit var handler: Handler
    private lateinit var timeRunnable: Runnable
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var chartManager: CrewChartManager
    private lateinit var crewWalkManager: CrewWalkManager

    private var currentCrewId = ""

    private val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
    private val koreaLocale = Locale.KOREA

    private var myLatitude: Double? = null
    private var myLongitude: Double? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private lateinit var refreshHandler: Handler
    private lateinit var refreshRunnable: Runnable
    private val REFRESH_INTERVAL = 20000L

    private var isJustJoined = false
    private lateinit var crewRecentPlaceManager: CrewRecentPlaceManager

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
        crewRecentPlaceManager = CrewRecentPlaceManager(this, supportFragmentManager)

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
                checkCrewMemberStatus(crewId, db, joinCrewMemberTextView, exitCrewMemberTextView, morePointButton)

            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "크루 이름 가져오기 실패", e)
            }

        barChart = findViewById(R.id.barChart)
        chartManager = CrewChartManager(barChart)
        val tabLayout = findViewById<TabLayout>(R.id.tablayout)

        val imageButton1 = findViewById<ImageButton>(R.id.imageButton1)
        imageButton1.setOnClickListener {
            val intent = Intent(this, PointRecordMainActivity::class.java)
            startActivity(intent)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.timelineRecyclerView)
        crewWalkManager = CrewWalkManager(recyclerView, this)
        crewWalkManager.setDataUpdateListener(this)

        if (crewId.isNotEmpty()) {
            crewWalkManager.loadCrewWalkRecords(crewId, db)
        }

        chartManager.setChartData(listOf(), chartManager.getDayLabels(), "시간대별")

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val walkData = crewWalkManager.getCrewWalkData()
                when (tab?.position) {
                    0 -> chartManager.setChartData(chartManager.getDayData(walkData), chartManager.getDayLabels(), "시간대별")
                    1 -> chartManager.setChartData(chartManager.getWeekData(walkData), chartManager.getWeekLabels(), "요일별")
                    2 -> chartManager.setChartData(chartManager.getMonthData(walkData), chartManager.getMonthLabels(), "일별")
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkAndRequestLocationPermission()
    }

    override fun onBackPressed() {
        var shouldCallSuper = true

        if (isJustJoined) {
            isJustJoined = false
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
            shouldCallSuper = false
        } else {
            checkCrewMembershipAndNavigate()
            shouldCallSuper = false
        }

        if (shouldCallSuper) {
            super.onBackPressed()
        }
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
            val dialog = PointRecordDialog.newInstance(imageUrl, name, description, placeId, lat, lng, currentCrewId)
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
                                isJustJoined = true
                                addUserPlacesToCrew(crewId, uid, db)

                                db.collection("Game").document("users").collection("userReward").document(uid)
                                    .update("crewRewardItem", 0)
                                    .addOnSuccessListener {
                                        Log.d("CrewLineMain", "crewRewardItem 초기화 완료")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("CrewLineMain", "crewRewardItem 초기화 실패", e)
                                    }
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
                                      joinTextView: TextView, exitTextView: TextView, morePointButton: TextView) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            joinTextView.visibility = android.view.View.VISIBLE
            exitTextView.visibility = android.view.View.GONE
            morePointButton.visibility = android.view.View.GONE
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
                        morePointButton.visibility = android.view.View.VISIBLE
                    } else {
                        joinTextView.visibility = android.view.View.VISIBLE
                        exitTextView.visibility = android.view.View.GONE
                        morePointButton.visibility = android.view.View.GONE
                    }
                } else {
                    joinTextView.visibility = android.view.View.VISIBLE
                    exitTextView.visibility = android.view.View.GONE
                    morePointButton.visibility = android.view.View.GONE
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "사용자 정보 확인 실패", e)
                joinTextView.visibility = android.view.View.VISIBLE
                exitTextView.visibility = android.view.View.GONE
                morePointButton.visibility = android.view.View.GONE
            }
    }

    private fun exitCrewMember(crewId: String, db: FirebaseFirestore) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = currentUser.uid

        db.collection("Crew").document(crewId).collection("member").document("leader").get()
            .addOnSuccessListener { leaderDoc ->
                val leaderId = leaderDoc.getString("leader")
                val isLeader = leaderId == uid

                val exitDialog = CrewExit(this, isLeader, crewId) {
                    performExitCrew(crewId, db)
                }
                exitDialog.show()
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMain", "리더 정보 확인 실패", e)
                val exitDialog = CrewExit(this, false, crewId) {
                    performExitCrew(crewId, db)
                }
                exitDialog.show()
            }
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

                        crewWalkManager.loadCrewWalkRecords(crewId, db)
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
        crewRecentPlaceManager.loadCrewPlacesInOrder(
            crewId = crewId,
            db = db,
            container = container,
            myLatitude = myLatitude,
            myLongitude = myLongitude,
            limit = 10
        )
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
                        crewWalkManager.loadCrewWalkRecords(crewId, db)
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

                    val crewId = intent.getStringExtra("crewId") ?: ""
                    if (crewId.isNotEmpty()) {
                        loadCrewPlaces(crewId, db, findViewById(R.id.pointButtonContainer))
                    }

                    callback(true)
                } else {
                    Log.w("CrewLineMain", "위치 정보를 가져올 수 없습니다.")
                    val crewId = intent.getStringExtra("crewId") ?: ""
                    if (crewId.isNotEmpty()) {
                        loadCrewPlaces(crewId, db, findViewById(R.id.pointButtonContainer))
                    }
                    callback(false)
                }
            }.addOnFailureListener { e ->
                Log.e("CrewLineMain", "위치 정보 가져오기 실패", e)
                val crewId = intent.getStringExtra("crewId") ?: ""
                if (crewId.isNotEmpty()) {
                    loadCrewPlaces(crewId, db, findViewById(R.id.pointButtonContainer))
                }
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
                            crewWalkManager.loadCrewWalkRecords(crewId, FirebaseFirestore.getInstance())
                            loadCrewPlaces(crewId, FirebaseFirestore.getInstance(), findViewById(R.id.pointButtonContainer))
                        }
                    }
                }
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                val crewId = intent.getStringExtra("crewId") ?: ""
                if (crewId.isNotEmpty()) {
                    crewWalkManager.loadCrewWalkRecords(crewId, FirebaseFirestore.getInstance())
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
            Log.d("CrewLineMain", "새로고침 실행")
            crewWalkManager.loadCrewWalkRecords(crewId, db)

            crewRecentPlaceManager.refreshCrewPlaces(
                crewId = crewId,
                db = db,
                container = findViewById(R.id.pointButtonContainer),
                myLatitude = myLatitude,
                myLongitude = myLongitude,
                limit = 10
            )
            checkAndAddNewMemberPlaces(crewId, db)
        }
    }

    // WalkDataUpdateListener 인터페이스 구현
    override fun onWalkDataUpdated(walkRecords: List<WalkRecord>) {
        val currentTab = findViewById<TabLayout>(R.id.tablayout).selectedTabPosition
        chartManager.refreshCurrentChart(currentTab, walkRecords)
    }

    private fun checkAndAddNewMemberPlaces(crewId: String, db: FirebaseFirestore) {
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

        if (::crewWalkManager.isInitialized) {
            crewWalkManager.clearData()
        }
        if (::crewRecentPlaceManager.isInitialized) {
            crewRecentPlaceManager.clearCache()
        }
    }
}