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
import androidx.annotation.NonNull // <-- 이 import 문을 추가합니다.
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
            // 아무 동작도 하지 않음
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

            Log.d("MapViewActivity", "탐색 모드 시작: placeId=$placeId, lat=$lat, lng=$lng")
            startExploreMode(placeId, lat, lng, imageUrl)
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

                    // WalkEndDialogFragment는 이 코드에 포함되어 있지 않으므로,
                    // 해당 Fragment가 프로젝트에 정의되어 있어야 합니다.
                    val dialog = WalkEndDialogFragment.newInstance(distanceKm, totalSteps, calorieText, startTime)
                    dialog.show(supportFragmentManager, "WalkEndDialog")
                }
                .addOnFailureListener {
                    Log.e("GoogleFit", "산책 종료 시 데이터 로드 실패", it)
                }
        }

        btnRefreshLocation.setOnClickListener { refreshLocation() }

        btnAddPoint.setOnClickListener {
            // AddPointDialogFragment는 이 코드에 포함되어 있지 않으므로,
            // 해당 Fragment가 프로젝트에 정의되어 있어야 합니다.
            AddPointDialogFragment().show(supportFragmentManager, "AddPointDialog")
        }

        btnExplore.setOnClickListener {
            // PointSelectFragment는 이 코드에 포함되어 있지 않으므로,
            // 해당 Fragment가 프로젝트에 정의되어 있어야 합니다.
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
                        // 🚨 지도 초기화 시점의 위치에 대한 모의 위치 감지
                        checkAndWarnIfMockLocation(it, "지도 초기화")
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
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

    // 🚨 Location 객체가 모의 위치인지 확인하고 경고하는 메서드
    private fun checkAndWarnIfMockLocation(location: Location, source: String) {
        val isMock: Boolean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 (API 31) 이상
            isMock = location.isMock
        } else { // Android 12 미만 버전
            @Suppress("DEPRECATION") // deprecated 경고 무시
            isMock = location.isFromMockProvider
        }

        if (isMock) {
            Log.w("MockLocation", "$source: 모의 위치 감지됨: ${location.latitude}, ${location.longitude}")
            Toast.makeText(this, "경고: 모의 위치가 감지되었습니다! ($source)", Toast.LENGTH_SHORT).show()
        } else {
            Log.i("MockLocation", "$source: 실제 위치: ${location.latitude}, ${location.longitude}")
        }
    }

    private fun refreshLocation() {
        // 권한 체크 추가: lastLocation을 호출하기 전에 권한이 있는지 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                // 🚨 수동 새로고침 시점의 위치에 대한 모의 위치 감지
                checkAndWarnIfMockLocation(it, "수동 새로고침")
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
                        checkAndWarnIfMockLocation(it, "주기적 업데이트")
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

                walkInfoText.text = "거리: ${distanceText}km | 걸음: ${totalSteps} 걸음 | 칼로리: ${calorieText}kcal"
            }
            .addOnFailureListener {
                Log.e("GoogleFit", "피트니스 데이터 읽기 실패", it)
            }
    }

    private fun startExploreMode(placeId: String, lat: Double, lng: Double, imageUrl: String) {
        try {
            // ExploreTrackingFragment 클래스가 정의되어 있어야 합니다.
            // 이 코드를 실행하기 전에 해당 Fragment가 프로젝트에 있는지 확인해주세요.
            val fragment = ExploreTrackingFragment.newInstance(placeId, lat, lng, imageUrl)

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
                // 권한이 부여되면 지도에 내 위치 표시 활성화
                // isMyLocationEnabled를 true로 설정하기 전에 권한이 있는지 다시 확인
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    googleMap?.isMyLocationEnabled = true
                }

                // 초기 위치 설정 및 모의 위치 감지
                // lastLocation은 권한이 부여된 후에만 안전하게 호출될 수 있습니다.
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            checkAndWarnIfMockLocation(it, "권한 부여 후 초기 위치")
                            val currentLatLng = LatLng(it.latitude, it.longitude)
                            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
                        }
                    }
                }
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }
        // Google Fit 권한 요청 결과도 여기에 처리할 수 있습니다.
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startWalk()
            } else {
                Toast.makeText(this, "Google Fit 권한이 없어 일부 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }
}