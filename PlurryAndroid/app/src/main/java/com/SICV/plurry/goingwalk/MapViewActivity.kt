package com.SICV.plurry.goingwalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                googleMap?.isMyLocationEnabled = true
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
                    }
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1002
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

    private fun refreshLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val currentLatLng = LatLng(it.latitude, it.longitude)
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f))
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
}
