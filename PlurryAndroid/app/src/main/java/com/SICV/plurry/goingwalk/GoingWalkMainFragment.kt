package com.SICV.plurry.goingwalk

import LogLS
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.SICV.plurry.MainActivity
import com.SICV.plurry.R
import com.SICV.plurry.safety.RouteAvoidanceManager
import com.SICV.plurry.safety.SafetyOverlayManager
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

class GoingWalkMainFragment : Fragment() {

/* ******************
*
* Property Define
*
* ******************/

//Permission
    private val PERMISSION_REQUEST_CODE = 100
    private val ALL_PERMISSIONS_REQUEST_CODE = 101
    private val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1003

//Locate
    private lateinit var walkInfoText: TextView
    private var googleMap: GoogleMap? = null

//Google Fit
    private var startTime: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L
    private var postSteps = 0

    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .build()
    }

//Locate Accuracy
    private val SUSPICIOUS_ACCURACY_THRESHOLD_METERS = 2f
    private val MIN_ACCURACY_CONSIDERED_VALID = 0.5f
    public var lastLocation: Location? = null
    private var lastLocationTime: Long = 0L
    private val MAX_SPEED_KMH = 300.0


//Map
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    public lateinit var mapFragment: SupportMapFragment
    private var polylineManager: PolylineManager? = null

    // 지도 준비 상태 관리
    //private var isMapReady = false
    //private val pendingSafetyEvaluations = mutableListOf<PendingSafetyEvaluation>()
    private var hasInitialCameraMove = false

/* ******************
*
* Create View
*
* ******************/

    // Fragment의 UI를 생성하고 반환하는 곳
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_goingwalk_main, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // MapFragment 찾기 - childFragmentManager 사용!
//        val mapFrag = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
//
//        if (mapFrag == null) {
//            Log.e("MapDebug", "MapFragment를 찾을 수 없습니다!")
//            LogLS.e("MapFragment를 찾을 수 없습니다!")
//            Toast.makeText(requireContext(), "지도 로딩 실패", Toast.LENGTH_LONG).show()
//        }

        return view

    }

    // onCreateView에서 View 생성이 완료된 후 호출되는 곳
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //LogLS.d("Begin")

        // 뷰 초기화
        walkInfoText = view.findViewById(R.id.walkIZnfo)
        val btnEndWalk = view.findViewById<Button>(R.id.btnEndWalk)
        val btnRefreshLocation = view.findViewById<Button>(R.id.btnRefreshLocation)
        val btnAddPoint = view.findViewById<Button>(R.id.btnAddPoint)
        val btnExplore = view.findViewById<Button>(R.id.btnExplore)

        // 개발자 옵션 활성화 여부 확인
        if (isDeveloperOptionsEnabled()) {
            Toast.makeText(requireContext(), "참고: 개발자 옵션이 활성화되어 있습니다.", Toast.LENGTH_LONG).show()
        }

        // 버튼 리스너 설정
        setupButtonListeners(btnEndWalk, btnRefreshLocation, btnAddPoint, btnExplore)

        // 🚀 프래그먼트가 생성되면 바로 권한 확인 및 산책 시작 로직 실행
        checkPermissionsAndStart()

        // 탐색 모드 인텐트 처리 (Activity의 arguments로 전달받았을 경우)
        arguments?.let {
            if (it.getBoolean("startExplore", false)) {
                val placeId = it.getString("placeId") ?: ""
                val lat = it.getDouble("lat", 0.0)
                val lng = it.getDouble("lng", 0.0)
                val imageUrl = it.getString("imageUrl") ?: ""
                val placeName = it.getString("placeName") ?: "알 수 없는 장소"
                startExploreMode(placeId, lat, lng, imageUrl, placeName)
                setButtonStateStartExplore()
            }
        }
    }

    private fun setupButtonListeners(
        btnEndWalk: Button,
        btnRefreshLocation: Button,
        btnAddPoint: Button,
        btnExplore: Button
    ) {
        btnEndWalk.setOnClickListener { endWalk() }
        btnRefreshLocation.setOnClickListener { refreshLocation() }
        btnAddPoint.setOnClickListener {
            // childFragmentManager를 사용하여 Fragment 내에서 DialogFragment를 관리
            AddPointDialogFragment().show(childFragmentManager, "AddPointDialog")
        }
        btnExplore.setOnClickListener {
            PointSelectFragment().show(childFragmentManager, "PointSelectDialog")
            setButtonStateStartExplore()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Handler 콜백 제거하여 메모리 누수 방지
        handler.removeCallbacks(updateRunnable)
    }

    //Set Button Visible
    public fun setButtonStateStartExplore() {
        val btnEndWalk = view?.findViewById<Button>(R.id.btnEndWalk)
        val tvEndWalk = view?.findViewById<TextView>(R.id.tvEndWalk)
        val btnExplore = view?.findViewById<Button>(R.id.btnExplore)
        val tvExplore = view?.findViewById<TextView>(R.id.tvExplore)

        btnEndWalk?.visibility = View.INVISIBLE
        tvEndWalk?.visibility = View.INVISIBLE
        btnExplore?.visibility = View.INVISIBLE
        tvExplore?.visibility = View.INVISIBLE
    }

    public fun setButtonStateEndExplore() {
        //LogLS.d("Begin")
        val btnEndWalk = view?.findViewById<Button>(R.id.btnEndWalk)
        val tvEndWalk = view?.findViewById<TextView>(R.id.tvEndWalk)
        val btnExplore = view?.findViewById<Button>(R.id.btnExplore)
        val tvExplore = view?.findViewById<TextView>(R.id.tvExplore)

        btnEndWalk?.visibility = View.VISIBLE
        tvEndWalk?.visibility = View.VISIBLE
        btnExplore?.visibility = View.VISIBLE
        tvExplore?.visibility = View.VISIBLE
    }


/* ******************
*
* Permission Session
*
* ******************/

    // 권한 확인 후 모든 기능 시작
    private fun checkPermissionsAndStart() {
        //LogLS.d("Begin")

        if (hasAllPermissions()) {
            // 모든 권한이 있으면, 지도 설정 및 Google Fit 권한 확인 시작
            initializeMap()
            checkFitPermissionsAndStartWalk()
        } else {
            // 권한이 없으면 요청
            requestAllPermissions()
        }
    }

    // Google Fit 권한 확인 및 산책 시작
    private fun checkFitPermissionsAndStartWalk() {
        //LogLS.d("Begin")

        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            startWalk()
        } else {
            //LogLS.d("requestPermissions")
            LogLS.t(requireContext(), "requestPermissions")
            // Google Fit 권한 요청
            GoogleSignIn.requestPermissions(
                requireActivity(),
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                account,
                fitnessOptions
            )
        }
    }

    private fun hasAllPermissions(): Boolean {
        //LogLS.d("Begin")
        val permissions = getRequiredPermissions()
        return permissions.all {
            // Context가 필요하므로 requireContext() 사용
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllPermissions() {
        //LogLS.d("Begin")
        val permissions = getRequiredPermissions()
        // Fragment 자체의 requestPermissions 메서드 사용
        requestPermissions(permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun getRequiredPermissions(): MutableList<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )

        // 안드로이드 10(Q) 이상에서만 ACTIVITY_RECOGNITION 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        }

        //LogLS.d("요청할 권한 목록: $permissions")
        return permissions
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //LogLS.d("Begin")

        when (requestCode) {
            ALL_PERMISSIONS_REQUEST_CODE, PERMISSION_REQUEST_CODE  -> {
                val deniedPermissions = permissions.zip(grantResults.toTypedArray())
                    .filter { it.second != PackageManager.PERMISSION_GRANTED }
                    .map { it.first }

                if (deniedPermissions.isNotEmpty()) {
                    // ⭐️ 이 부분을 수정하여 어떤 권한이 거부되었는지 상세히 알려줍니다.
                    val message = deniedPermissions.joinToString("\n") {
                        when (it) {
                            Manifest.permission.CAMERA -> "카메라 권한"
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION -> "위치 권한"
                            Manifest.permission.ACTIVITY_RECOGNITION -> "신체 활동 권한"
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO -> "저장소 접근 권한"
                            else -> "알 수 없는 권한"
                        }
                    }
                    Toast.makeText(
                        requireContext(),
                        "다음 권한이 거부되었습니다:\n$message\n\n일부 기능이 제한될 수 있습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // 위치 권한이 허용되었는지 다시 확인 후 지도 초기화
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    initializeMap()
                }

                // 모든 권한이 허용되었는지와 관계없이 Google Fit 권한 확인 로직은 진행
                checkFitPermissionsAndStartWalk()
            }
            GOOGLE_FIT_PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startWalk()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Google Fit 권한이 없어 일부 기능을 사용할 수 없습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun handleGoogleFitResult(resultCode: Int) {
        //LogLS.d("handleGoogleFitResult - resultCode: $resultCode")

        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            //LogLS.d("Google Fit 권한 확인 완료 - startWalk 호출")
            startWalk()
        } else {
            LogLS.e("Google Fit 권한 없음")
            Toast.makeText(requireContext(), "Google Fit 권한이 없어 일부 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }


/* ******************
*
* Map View
*
* ******************/
    // 지도 초기화
    private fun initializeMap() {
        val mapFrag = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment

        if (mapFrag == null) {
            Log.e("MapDebug", "MapFragment를 찾을 수 없습니다!")
            LogLS.e("MapFragment를 찾을 수 없습니다!")
            Toast.makeText(requireContext(), "지도 로딩 실패", Toast.LENGTH_LONG).show()
            return
        }

        mapFragment = mapFrag
        mapFragment.getMapAsync { map ->
            //LogLS.d("지도 로드 완료 - 초기화 시작")

            try {
                googleMap = map
                polylineManager = PolylineManager(map)

                // 지도 기본 설정
                setupMapSettings(map)

                // 일단 서울 기본 위치로 설정 (GPS 잡히기 전까지 임시)
                map.moveCamera(
                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                        LatLng(37.5665, 126.9780), // 서울시청
                        21f
                    )
                )
                //Log.d("MapDebug", "기본 위치(서울)로 카메라 설정")
                //LogLS.d("기본 위치(서울)로 카메라 설정")

                // 현재 위치로 카메라 이동
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // lastLocation 시도
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            map.animateCamera(
                                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                                    LatLng(location.latitude, location.longitude),
                                    21f
                                )
                            )
                            hasInitialCameraMove = true
                            Log.d(
                                "MapDebug",
                                "lastLocation으로 카메라 이동: ${location.latitude}, ${location.longitude}"
                            )
                            //LogLS.d("lastLocation으로 카메라 이동: ${location.latitude}, ${location.longitude}")
                        } else {
                            Log.w("MapDebug", "lastLocation이 null입니다. 실시간 위치 업데이트를 기다립니다")
                            LogLS.w("lastLocation이 null입니다. 실시간 위치 업데이트를 기다립니다")
                            // hasInitialCameraMove를 false로 유지하여 실시간 위치가 들어오면 이동하도록
                        }
                    }.addOnFailureListener { e ->
                        Log.e("MapDebug", "lastLocation 가져오기 실패: ${e.message}")
                        LogLS.e("lastLocation 가져오기 실패: ${e.message}")
                    }
                } else {
                    Log.w("MapDebug", "위치 권한이 없습니다")
                    LogLS.t(requireContext(), "위치 권한이 없습니다")
                }

                //Log.d("MapDebug", "지도 초기화 완료")
                //LogLS.d("지도 초기화 완료")
            } catch (e: Exception) {
                Log.e("MapDebug", "지도 초기화 오류: ${e.message}")
                LogLS.e("지도 초기화 오류: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun setupMapSettings(map: com.google.android.gms.maps.GoogleMap) {
        try {
            map.mapType = com.google.android.gms.maps.GoogleMap.MAP_TYPE_NORMAL
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                map.isMyLocationEnabled = true
            }

            Log.d("MapDebug", "지도 기본 설정 완료")
            //LogLS.d("지도 기본 설정 완료")
        } catch (e: SecurityException) {
            Log.e("MapDebug", "위치 권한 없음: ${e.message}")
            LogLS.t(requireContext(),"위치 권한 없음: ${e.message}")
        } catch (e: Exception) {
            Log.e("MapDebug", "지도 설정 오류: ${e.message}")
            LogLS.e("지도 설정 오류: ${e.message}")
        }
    }

    private fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock
        else @Suppress("DEPRECATION") location.isFromMockProvider
    }

    private fun checkAccuracyDiscrepancy(location: Location): Boolean {
        val accuracy = location.accuracy
        if (accuracy < SUSPICIOUS_ACCURACY_THRESHOLD_METERS || accuracy < MIN_ACCURACY_CONSIDERED_VALID) {
            Log.w("AccuracyDetection", "비정상적인 위치 정확도 감지됨: ${accuracy}m")
            return true
        }
        return false
    }

    private fun isTeleporting(currentLocation: Location): Boolean {
        lastLocation?.let { prevLocation ->
            val timeElapsedSeconds = (System.currentTimeMillis() - lastLocationTime) / 1000.0
            if (timeElapsedSeconds <= 0) return false

            val distanceMeters = prevLocation.distanceTo(currentLocation)
            val speedKmh = (distanceMeters / timeElapsedSeconds) * 3.6

            if (speedKmh > MAX_SPEED_KMH) {
                Log.w("Teleportation", "순간이동 감지! 허용 속도 초과: ${String.format("%.2f", speedKmh)}km/h")
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

        if (mockDetected && (devOptionsEnabled || accuracyDiscrepancyDetected || teleportationDetected)) {
            Log.e("Security", "보안 위협 감지: 비정상적인 위치 환경.")
            Toast.makeText(requireContext(), "비정상적인 환경이 감지되어 이전 화면으로 돌아갑니다.", Toast.LENGTH_LONG).show()
            // Activity를 종료하는 대신, Fragment 스택에서 현재 Fragment를 제거
            parentFragmentManager.popBackStack()
        } else {
            //LogLS.d("$source: 현재 위치: ${location.latitude}, ${location.longitude}, 정확도: ${location.accuracy}m")
            polylineManager?.addPointToPath(LatLng(location.latitude, location.longitude))
        }
    }

    private fun isDeveloperOptionsEnabled(): Boolean {
        return Settings.Global.getInt(requireActivity().contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }

    private fun refreshLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                checkLocationIntegrityAndHandleExit(it, "수동 새로고침")
                val currentLatLng = LatLng(it.latitude, it.longitude)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 21f))
            } ?: run {
                Toast.makeText(requireContext(), "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startWalk() {
        startTime = System.currentTimeMillis()

        //LogLS.d("산책 시작 - startTime: $startTime")
        //LogLS.t(requireContext(),"산책 시작 - startTime: $startTime")

        val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
        arrayOf(
            DataType.TYPE_STEP_COUNT_DELTA,
            DataType.TYPE_DISTANCE_DELTA,
            DataType.TYPE_CALORIES_EXPENDED
        ).forEach { dataType ->
            Fitness.getRecordingClient(requireActivity(), account)
                .subscribe(dataType)
                .addOnSuccessListener { Log.d("GoogleFit", "${dataType.name} 데이터 기록 시작됨!") }
                .addOnFailureListener { Log.e("GoogleFit", "${dataType.name} 기록 실패", it) }
        }
        handler.post(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            //LogLS.d("Begin");
            readFitnessData()
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let { checkLocationIntegrityAndHandleExit(it, "주기적 업데이트") }
                }
            }
            handler.postDelayed(this, updateInterval)
        }
    }

    private fun readFitnessData() {
        //LogLS.d("Begin");
        val endTime = System.currentTimeMillis()

        //Test Log
        if(!(endTime - startTime > 0)) LogLS.t(requireContext(),"endTime error!")

        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
            .aggregate(DataType.TYPE_DISTANCE_DELTA)
            .aggregate(DataType.TYPE_CALORIES_EXPENDED)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .bucketByTime(1, TimeUnit.MINUTES).build()

        val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
        Fitness.getHistoryClient(requireActivity(), account)
            .readData(readRequest)
            .addOnSuccessListener { response ->

                if(response.buckets.isEmpty()) LogLS.t(requireContext(),"Bucket Error!")


                var totalSteps = 0
                var totalDistance = 0.0
                var totalCalories = 0.0

                for (bucket in response.buckets) {
                    for (dataSet in bucket.dataSets) {
                        for (dp in dataSet.dataPoints) {
                            when (dp.dataType.name) {
                                "com.google.step_count.delta" -> {
                                    totalSteps += dp.getValue(Field.FIELD_STEPS).asInt()
                                    if(dp.getValue(Field.FIELD_STEPS).asInt()>postSteps) {
                                        postSteps = dp.getValue(Field.FIELD_STEPS).asInt()
                                        val activity = requireActivity()
                                        if (activity is MainActivity) {
                                            activity.SendMessageToUnity("MoveShip")
                                            //LogLS.t(requireContext(), "FIELD_STEPS : ${postSteps}")
                                        }
                                        else {
                                            LogLS.e("No MainActivity Error!")
                                        }
                                    }
                                }
                                "com.google.distance.delta" -> totalDistance += dp.getValue(Field.FIELD_DISTANCE).asFloat()
                                "com.google.calories.expended" -> totalCalories += dp.getValue(Field.FIELD_CALORIES).asFloat()
                            }
                        }
                    }
                }

                //if( totalSteps==0 && totalDistance==0.0 && totalCalories==0.0) LogLS.t(requireContext(),"no Data!")

                val distanceText = String.format("%.2f", totalDistance / 1000)
                val calorieText = String.format("%.1f", totalCalories)
                walkInfoText.text = "${distanceText}km | ${totalSteps} 걸음 | ${calorieText}kcal"
                //LogLS.t(requireContext(), "${distanceText}km | ${totalSteps} 걸음 | ${calorieText}kcal")
            }
            .addOnFailureListener { e ->
                LogLS.e("Fitness 데이터 읽기 실패: ${e.message}")
                e.printStackTrace()
                LogLS.t(requireContext(),"readRequest or account error!")
            }
    }

    private fun startExploreMode(placeId: String, lat: Double, lng: Double, imageUrl: String, placeName: String) {
        val fragment = GoingWalkExploreFragment.newInstance(placeId, lat, lng, imageUrl, placeName)
        childFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerExplore, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun endWalk() {

        //LogLS.d("Begin")

        handler.removeCallbacks(updateRunnable)

        val endTime = System.currentTimeMillis()
        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
            .aggregate(DataType.TYPE_DISTANCE_DELTA)
            .aggregate(DataType.TYPE_CALORIES_EXPENDED)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .bucketByTime(1, TimeUnit.MINUTES)
            .build()

        val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)

        Fitness.getHistoryClient(requireActivity(), account)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                var totalSteps = 0
                var totalDistance = 0.0
                var totalCalories = 0.0

                for (bucket in response.buckets) {
                    for (dataSet in bucket.dataSets) {
                        for (dp in dataSet.dataPoints) {
                            when (dp.dataType.name) {
                                "com.google.step_count.delta" -> totalSteps += dp.getValue(Field.FIELD_STEPS).asInt()
                                "com.google.distance.delta" -> totalDistance += dp.getValue(Field.FIELD_DISTANCE).asFloat()
                                "com.google.calories.expended" -> totalCalories += dp.getValue(Field.FIELD_CALORIES).asFloat()
                            }
                        }
                    }
                }

                val distanceKm = String.format("%.2f", totalDistance / 1000)
                val calorieText = String.format("%.1f", totalCalories)

                val dialog = WalkEndDialogFragment.newInstance(distanceKm, totalSteps, calorieText, startTime)
                dialog.show(childFragmentManager, "WalkEndDialog")
            }
            .addOnFailureListener {
                LogLS.e("산책 종료 시 데이터 로드 실패")
            }
    }
}