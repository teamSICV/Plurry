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
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory // 🚀 NEW: 마커 색상을 위한 임포트
import com.google.firebase.auth.FirebaseAuth
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

    private val SUSPICIOUS_ACCURACY_THRESHOLD_METERS = 2f
    private val MIN_ACCURACY_CONSIDERED_VALID = 0.5f

    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0L
    private val MAX_SPEED_KMH = 300.0

    private val visitedPlacesRepository = VisitedPlacesRepository()
    private val auth = FirebaseAuth.getInstance()

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

        onBackPressedDispatcher.addCallback(this) {
            // 뒤로가기 무시
        }

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
            val placeName = intent.getStringExtra("placeName") ?: "알 수 없는 장소"

            Log.d("MapViewActivity", "탐색 모드 시작: placeId=$placeId, lat=$lat, lng=$lng, placeName=$placeName")
            startExploreMode(placeId, lat, lng, imageUrl, placeName)
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
        btnAddPoint.setOnClickListener { AddPointDialogFragment().show(supportFragmentManager, "AddPointDialog") }
        btnExplore.setOnClickListener { PointSelectFragment().show(supportFragmentManager, "PointSelectDialog") }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            loadVisitedPlacesAndAddMarkers()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                googleMap?.isMyLocationEnabled = true
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        checkLocationIntegrityAndHandleExit(it, "지도 초기화")
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
                        lastLocation = it
                        lastLocationTime = System.currentTimeMillis()
                    }
                }
            } else {
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

    // 방문한 장소들을 불러와서 마커를 추가하는 함수
    private fun loadVisitedPlacesAndAddMarkers() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("MapViewActivity", "사용자 ID를 찾을 수 없습니다. 로그인이 필요합니다.")
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        visitedPlacesRepository.getVisitedPlaces(
            userId = userId,
            onSuccess = { places ->
                googleMap?.let { map ->
                    for (place in places) {
                        val placeLatLng = LatLng(place.latitude, place.longitude)
                        map.addMarker(
                            MarkerOptions()
                                .position(placeLatLng)
                                .title(place.placeName)
                                // 🚀 MODIFIED: VisitedPlace 객체의 markerColor를 사용해 마커 색상 적용
                                .icon(BitmapDescriptorFactory.defaultMarker(place.markerColor))
                        )
                    }
                }
            },
            onFailure = { exception ->
                Log.e("MapViewActivity", "Firestore에서 방문 장소 불러오기 실패", exception)
                Toast.makeText(this, "방문 장소를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    private fun checkAccuracyDiscrepancy(location: Location): Boolean {
        val accuracy = location.accuracy

        if (accuracy < SUSPICIOUS_ACCURACY_THRESHOLD_METERS || accuracy < MIN_ACCURACY_CONSIDERED_VALID) {
            Log.w("AccuracyDetection", "비정상적인 위치 정확도 감지됨: ${accuracy}m")
            Toast.makeText(this, "경고: 비정상적인 위치 정확도 감지! (${accuracy}m)", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    private fun isTeleporting(currentLocation: Location): Boolean {
        lastLocation?.let { prevLocation ->
            val timeElapsedSeconds = (System.currentTimeMillis() - lastLocationTime) / 1000.0
            if (timeElapsedSeconds <= 0) {
                Log.w("Teleportation", "시간 간격이 0 또는 음수입니다. 이전 위치 정보를 업데이트합니다.")
                lastLocation = currentLocation
                lastLocationTime = System.currentTimeMillis()
                return false
            }

            val distanceMeters = prevLocation.distanceTo(currentLocation)
            val speedMps = distanceMeters / timeElapsedSeconds
            val speedKmh = speedMps * 3.6

            Log.d("Teleportation", "거리: ${String.format("%.2f", distanceMeters)}m, 시간: ${String.format("%.2f", timeElapsedSeconds)}s, 속도: ${String.format("%.2f", speedKmh)}km/h")

            if (speedKmh > MAX_SPEED_KMH) {
                Log.w("Teleportation", "순간이동 감지! 허용 속도 초과: ${String.format("%.2f", speedKmh)}km/h")
                Toast.makeText(this, "경고: 비정상적인 속도 변화 감지 (순간이동 의심)!", Toast.LENGTH_LONG).show()
                return true
            }
        }
        lastLocation = currentLocation
        lastLocationTime = System.currentTimeMillis()
        return false
    }

    private fun checkLocationIntegrityAndHandleExit(location: Location, source: String) {
        val mockDetected = isMockLocation(location)
        val devOptionsEnabled = isDeveloperOptionsEnabled()
        val accuracyDiscrepancyDetected = checkAccuracyDiscrepancy(location)
        val teleportationDetected = isTeleporting(location)

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
            finish()
        } else {
            if (!mockDetected) {
                Log.i("Location", "$source: 현재 위치: ${location.latitude}, ${location.longitude}, 정확도: ${location.accuracy}m")
            } else {
                Log.i("Location", "$source: 모의 위치 감지됨 (단독): ${location.latitude}, ${location.longitude}, 정확도: ${location.accuracy}m")
            }
        }
    }

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
            if (ContextCompat.checkSelfPermission(this@MapViewActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        checkLocationIntegrityAndHandleExit(it, "주기적 업데이트")
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

    private fun startExploreMode(placeId: String, lat: Double, lng: Double, imageUrl: String, placeName: String) {
        try {
            val fragment = ExploreTrackingFragment.newInstance(placeId, lat, lng, imageUrl, placeName)
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
                            checkLocationIntegrityAndHandleExit(it, "권한 부여 후 초기 위치")
                            val currentLatLng = LatLng(it.latitude, it.longitude)
                            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
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
