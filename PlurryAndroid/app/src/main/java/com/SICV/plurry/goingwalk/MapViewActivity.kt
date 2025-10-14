/*





//////////////////////////
이 코드 사용 안함!!!
GoingWalkMainFragment로 이동하세요
//////////////////////////











package com.SICV.plurry.goingwalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.annotation.NonNull
import com.SICV.plurry.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.TimeUnit

class MapViewActivity : AppCompatActivity() {

    private lateinit var walkInfoText: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

    private var startTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L

    private val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1003
    private val LOCATION_PERMISSION_REQUEST_CODE = 1002

    // 🚨 정확도 불일치 감지를 위한 상수
    private val SUSPICIOUS_ACCURACY_THRESHOLD_METERS = 2f // 2미터 이하의 지나치게 완벽한 정확도
    private val MIN_ACCURACY_CONSIDERED_VALID = 0.5f // 0에 너무 가까운 값은 비정상으로 간주

    // 🚀 순간이동 감지를 위한 변수 추가
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0L
    private val MAX_SPEED_KMH = 300.0 // 허용 가능한 최대 속도 임계값 (km/h)

    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_view)

        // 🔒 뒤로가기 무시
        onBackPressedDispatcher.addCallback(this) {
        }

        // 앱 시작 시 개발자 옵션 활성화 여부만 먼저 확인 (위치 조작과 별개로 정보 제공)
        if (isDeveloperOptionsEnabled()) {
            Toast.makeText(this, "참고: 개발자 옵션이 활성화되어 있습니다.", Toast.LENGTH_LONG).show()
            Log.i("DeveloperOptions", "개발자 옵션이 활성화되어 있습니다.")
        }

        walkInfoText = findViewById(R.id.walkIZnfo)
        val btnEndWalk = findViewById<Button>(R.id.btnEndWalk)
        val btnRefreshLocation = findViewById<Button>(R.id.btnRefreshLocation)
        val btnAddPoint = findViewById<Button>(R.id.btnAddPoint)
        val btnExplore = findViewById<Button>(R.id.btnExplore)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (intent.getBooleanExtra("startExplore", false)) {
            val placeId = intent.getStringExtra("placeId") ?: ""
            val lat = intent.getDoubleExtra("lat", 0.0)
            val lng = intent.getDoubleExtra("lng", 0.0)
            val imageUrl = intent.getStringExtra("imageUrl") ?: ""
            val placeName = intent.getStringExtra("placeName") ?: "알 수 없는 장소" // 🚀 NEW: placeName 가져오기

            Log.d("MapViewActivity", "탐색 모드 시작: placeId=$placeId, lat=$lat, lng=$lng, placeName=$placeName")
            startExploreMode(placeId, lat, lng, imageUrl, placeName) // 🚀 MODIFIED: placeName 전달
        }

        btnEndWalk.setOnClickListener {
            handler.removeCallbacks(updateRunnable)

            val endTime = System.currentTimeMillis()
            val readRequest = DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_DISTANCE_DELTA)
                .aggregate(DataType.TYPE_CALORIES_EXPENDED)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.MINUTES)
                .build()

            val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

            Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener { response ->
                    var totalSteps = 0
                    var totalDistance = 0.0
                    var totalCalories = 0.0

                    for (bucket in response.buckets) {
                        for (dataSet in bucket.dataSets) {
                            for (dp in dataSet.dataPoints) {
                                when (dp.dataType) {
                                    DataType.TYPE_STEP_COUNT_DELTA -> totalSteps += dp.getValue(Field.FIELD_STEPS).asInt()
                                    DataType.TYPE_DISTANCE_DELTA -> totalDistance += dp.getValue(Field.FIELD_DISTANCE).asFloat()
                                    DataType.TYPE_CALORIES_EXPENDED -> totalCalories += dp.getValue(Field.FIELD_CALORIES).asFloat()
                                }
                            }
                        }
                    }

                    val distanceKm = String.format("%.2f", totalDistance / 1000)
                    val calorieText = String.format("%.1f", totalCalories)
                    val dialog = WalkEndDialogFragment.newInstance(distanceKm, totalSteps, calorieText, startTime)
                    dialog.show(supportFragmentManager, "WalkEndDialog")
                }
                .addOnFailureListener {
                    Log.e("GoogleFit", "산책 종료 시 데이터 로드 실패", it)
                }
        }

        btnRefreshLocation.setOnClickListener { refreshLocation() }

        btnAddPoint.setOnClickListener {
            AddPointDialogFragment().show(supportFragmentManager, "AddPointDialog")
        }

        btnExplore.setOnClickListener {
            PointSelectFragment().show(supportFragmentManager, "PointSelectDialog")
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            // 권한 체크 후 내 위치 활성화 및 초기 위치 설정
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                googleMap?.isMyLocationEnabled = true
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        // 🚨 지도 초기화 시점의 위치에 대한 무결성 검사
                        checkLocationIntegrityAndHandleExit(it, "지도 초기화")
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
                        // 🚀 초기 위치를 lastLocation으로 설정
                        lastLocation = it
                        lastLocationTime = System.currentTimeMillis()
                    }
                }
            } else {
                // 권한이 없으면 사용자에게 요청
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this,
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                GoogleSignIn.getLastSignedInAccount(this),
                fitnessOptions
            )
        } else {
            startWalk()
        }
    }

    // 🚨 Location 객체가 모의 위치인지 확인하는 메서드
    private fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    // 🚨 위치 정확도 불일치를 확인하는 함수
    private fun checkAccuracyDiscrepancy(location: Location): Boolean {
        val accuracy = location.accuracy // 미터 단위

        // 정확도가 비정상적으로 낮거나(매우 정확) 0에 가까운 경우를 의심
        if (accuracy < SUSPICIOUS_ACCURACY_THRESHOLD_METERS || accuracy < MIN_ACCURACY_CONSIDERED_VALID) {
            Log.w("AccuracyDetection", "비정상적인 위치 정확도 감지됨: ${accuracy}m")
            Toast.makeText(this, "경고: 비정상적인 위치 정확도 감지! (${accuracy}m)", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    // 🚀 순간이동을 감지하는 함수
    private fun isTeleporting(currentLocation: Location): Boolean {
        lastLocation?.let { prevLocation ->
            val timeElapsedSeconds = (System.currentTimeMillis() - lastLocationTime) / 1000.0 // 초 단위
            if (timeElapsedSeconds <= 0) { // 시간이 흐르지 않았거나 음수일 경우 (이상 케이스)
                Log.w("Teleportation", "시간 간격이 0 또는 음수입니다. 이전 위치 정보를 업데이트합니다.")
                lastLocation = currentLocation
                lastLocationTime = System.currentTimeMillis()
                return false
            }

            val distanceMeters = prevLocation.distanceTo(currentLocation) // 미터 단위
            val speedMps = distanceMeters / timeElapsedSeconds // 미터/초
            val speedKmh = speedMps * 3.6 // km/h (m/s를 km/h로 변환)

            Log.d("Teleportation", "거리: ${String.format("%.2f", distanceMeters)}m, 시간: ${String.format("%.2f", timeElapsedSeconds)}s, 속도: ${String.format("%.2f", speedKmh)}km/h")

            if (speedKmh > MAX_SPEED_KMH) {
                Log.w("Teleportation", "순간이동 감지! 허용 속도 초과: ${String.format("%.2f", speedKmh)}km/h")
                Toast.makeText(this, "경고: 비정상적인 속도 변화 감지 (순간이동 의심)!", Toast.LENGTH_LONG).show()
                return true
            }
        }
        // 현재 위치를 다음 비교를 위해 저장
        lastLocation = currentLocation
        lastLocationTime = System.currentTimeMillis()
        return false
    }

    // 🚨 위치 무결성을 확인하고, 모의 위치 + (개발자 옵션 OR 정확도 불일치 OR 순간이동) 감지 시 GoingWalkMainActivity로 이동
    private fun checkLocationIntegrityAndHandleExit(location: Location, source: String) {
        val mockDetected = isMockLocation(location)
        val devOptionsEnabled = isDeveloperOptionsEnabled()
        val accuracyDiscrepancyDetected = checkAccuracyDiscrepancy(location)
        val teleportationDetected = isTeleporting(location) // 🚀 순간이동 감지

        // 모의 위치 감지 시 사용자에게 먼저 알림 (단독 경고)
        if (mockDetected) {
            Log.w("MockLocation", "$source: 모의 위치 감지됨: ${location.latitude}, ${location.longitude}")
            Toast.makeText(this, "경고: 모의 위치가 감지되었습니다! ($source)", Toast.LENGTH_SHORT).show()
        }

        if (mockDetected && (devOptionsEnabled || accuracyDiscrepancyDetected || teleportationDetected)) {
            Log.e("Security", "보안 위협 감지: 비정상적인 위치 환경. GoingWalkMainActivity로 이동.")
            Toast.makeText(this, "비정상적인 환경이 감지되어 초기 화면으로 돌아갑니다.", Toast.LENGTH_LONG).show()

            val intent = Intent(this, GoingWalkMainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish() // 현재 MapViewActivity 종료
        } else {
            // 정상적인 위치 또는 단일 경고만 발생한 경우
            if (!mockDetected) {
                Log.i("Location", "$source: 현재 위치: ${location.latitude}, ${location.longitude}, 정확도: ${location.accuracy}m")
            } else {
                Log.i("Location", "$source: 모의 위치 감지됨 (단독): ${location.latitude}, ${location.longitude}, 정확도: ${location.accuracy}m")
            }
        }
    }

    // 🚨 개발자 옵션 활성화 여부를 확인하는 함수
    private fun isDeveloperOptionsEnabled(): Boolean {
        return Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }

    private fun refreshLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                // 🚨 수동 새로고침 시점의 위치에 대한 무결성 검사
                checkLocationIntegrityAndHandleExit(it, "수동 새로고침")
                val currentLatLng = LatLng(it.latitude, it.longitude)
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
            } ?: run {
                Toast.makeText(this, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startWalk() {
        startTime = System.currentTimeMillis()

        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        arrayOf(
            DataType.TYPE_STEP_COUNT_DELTA,
            DataType.TYPE_DISTANCE_DELTA,
            DataType.TYPE_CALORIES_EXPENDED
        ).forEach { dataType ->
            Fitness.getRecordingClient(this, account)
                .subscribe(dataType)
                .addOnSuccessListener {
                    Log.d("GoogleFit", "${dataType.name} 데이터 기록 시작됨!")
                }
                .addOnFailureListener {
                    Log.e("GoogleFit", "${dataType.name} 기록 실패", it)
                }
        }

        handler.post(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            readFitnessData()
            // 🚨 주기적인 위치 업데이트를 위한 로직 추가
            if (ContextCompat.checkSelfPermission(this@MapViewActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        checkLocationIntegrityAndHandleExit(it, "주기적 업데이트") // 🚨 주기적 업데이트 시 무결성 검사
                        // 주기적 업데이트 시 지도 이동은 선택 사항 (너무 자주 움직이면 불편할 수 있음)
                        // val currentLatLng = LatLng(it.latitude, it.longitude)
                        // googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
                    }
                }
            }

            handler.postDelayed(this, updateInterval)
        }
    }

    private fun readFitnessData() {
        val endTime = System.currentTimeMillis()

        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
            .aggregate(DataType.TYPE_DISTANCE_DELTA)
            .aggregate(DataType.TYPE_CALORIES_EXPENDED)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .bucketByTime(1, TimeUnit.MINUTES)
            .build()

        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                var totalSteps = 0
                var totalDistance = 0.0
                var totalCalories = 0.0

                for (bucket in response.buckets) {
                    for (dataSet in bucket.dataSets) {
                        for (dp in dataSet.dataPoints) {
                            when (dp.dataType) {
                                DataType.TYPE_STEP_COUNT_DELTA -> totalSteps += dp.getValue(Field.FIELD_STEPS).asInt()
                                DataType.TYPE_DISTANCE_DELTA -> totalDistance += dp.getValue(Field.FIELD_DISTANCE).asFloat()
                                DataType.TYPE_CALORIES_EXPENDED -> totalCalories += dp.getValue(Field.FIELD_CALORIES).asFloat()
                            }
                        }
                    }
                }

                val distanceKm = totalDistance / 1000
                val distanceText = String.format("%.2f", distanceKm)
                val calorieText = String.format("%.1f", totalCalories)

                Log.d("GoogleFit", "걸음 수: $totalSteps, 거리: $distanceText km, 칼로리: $calorieText kcal")

                walkInfoText.text = "${distanceText}km | ${totalSteps} 걸음 | ${calorieText}kcal"
            }
            .addOnFailureListener {
                Log.e("GoogleFit", "피트니스 데이터 읽기 실패", it)
            }
    }

    private fun startExploreMode(placeId: String, lat: Double, lng: Double, imageUrl: String, placeName: String) { // 🚀 MODIFIED: placeName 파라미터 추가
        try {
            val fragment = ExploreTrackingFragment.newInstance(placeId, lat, lng, imageUrl, placeName) // 🚀 MODIFIED: placeName 전달

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerExplore, fragment)
                .addToBackStack(null)
                .commit()

            Log.d("MapViewActivity", "ExploreTrackingFragment 시작됨")
        } catch (e: Exception) {
            Log.e("MapViewActivity", "ExploreTrackingFragment 시작 실패", e)
            Toast.makeText(this, "탐색 모드 시작 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    googleMap?.isMyLocationEnabled = true
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            checkLocationIntegrityAndHandleExit(it, "권한 부여 후 초기 위치") // 🚨 권한 부여 후 초기 위치 무결성 검사
                            val currentLatLng = LatLng(it.latitude, it.longitude)
                            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
                            // 🚀 권한 부여 후 초기 위치 설정 시 lastLocation 초기화
                            lastLocation = it
                            lastLocationTime = System.currentTimeMillis()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startWalk()
            } else {
                Toast.makeText(this, "Google Fit 권한이 없어 일부 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

*/
