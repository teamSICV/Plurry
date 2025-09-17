// ExploreTrackingFragment.kt (SafetyOverlayManager 문제 해결 버전)
package com.SICV.plurry.goingwalk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.SICV.plurry.R
import com.SICV.plurry.di.RetrofitModule
import com.SICV.plurry.safety.SafetyRepo
import com.SICV.plurry.safety.viewmodel.SafetyVMFactory
import com.SICV.plurry.safety.viewmodel.SafetyViewModel
import com.SICV.plurry.safety.BottomSheet
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.location.*
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import kotlin.math.*
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.SICV.plurry.safety.model.SafetyDetail
import com.SICV.plurry.safety.SafetyOverlayManager
import com.SICV.plurry.safety.RouteAvoidanceManager

class ExploreTrackingFragment : Fragment() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var tvDistanceInfo: TextView
    private lateinit var arrowImageView: ImageView
    private lateinit var imgTargetPreview: ImageView
    private lateinit var btnExitExplore: Button
    private lateinit var tvSpeedWarning: TextView

    private var googleMap: com.google.android.gms.maps.GoogleMap? = null
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var lastVibrationLevel = Int.MAX_VALUE
    private var lastLoggedDistanceLevel = -1
    private var arrivalDialogShown = false
    private var targetImageUrl: String? = null
    private var placeId: String? = null
    private var targetPlaceName: String? = null

    private lateinit var fitnessOptions: FitnessOptions
    private var exploreStartTime: Long = 0L

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var isExploringActive = true
    private var lastLocationTime: Long = 0L
    private var lastLocation: Location? = null
    private var polylineManager: PolylineManager? = null

    // 안전도 관련
    private lateinit var safetyViewModel: SafetyViewModel
    private var lastSafetyEvalLoc: Location? = null
    private var lastSafetyEvalTime: Long = 0L
    private var latestSafetyLine: String = ""
    private var safetyBanner: View? = null
    private var safetyBannerText: TextView? = null
    private var safetyOverlayManager: SafetyOverlayManager? = null

    // 우회 경로 관련
    private lateinit var routeAvoidanceManager: RouteAvoidanceManager
    private var isDetourActive = false
    private var lastDetourMessage: String? = null

    // 지도 준비 상태 관리
    private var isMapReady = false
    private val pendingSafetyEvaluations = mutableListOf<PendingSafetyEvaluation>()

    data class PendingSafetyEvaluation(
        val lat: Double,
        val lng: Double,
        val safetyDetail: SafetyDetail
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.activity_goingwalk_exploremain, container, false)

        try {
            // UI 요소들 초기화
            tvDistanceInfo = view.findViewById(R.id.tvDistanceInfo)
            arrowImageView = view.findViewById(R.id.arrowImageView)
            imgTargetPreview = view.findViewById(R.id.imgTargetPreview)
            btnExitExplore = view.findViewById(R.id.btnExitExplore)
            tvSpeedWarning = view.findViewById(R.id.tvSpeedWarning)

            // 안전도 배너 (nullable 처리)
            //safetyBanner = view.findViewById(R.id.includeSafetyBanner)
            safetyBannerText = safetyBanner?.findViewById(R.id.safetyBannerText)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

            // Arguments 처리
            arguments?.let {
                placeId = it.getString("placeId")
                targetLat = it.getDouble("targetLat")
                targetLng = it.getDouble("targetLng")
                targetImageUrl = it.getString("targetImageUrl")
                targetPlaceName = it.getString("targetPlaceName")
            }

            // 이미지 로드
            targetImageUrl?.let { url ->
                if (isAdded && context != null) {
                    Glide.with(this).load(url).into(imgTargetPreview)
                }
            }

            // 버튼 리스너
            btnExitExplore.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            // 뒤로가기 처리
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {}
                }
            )

            // 맵 프래그먼트 설정
            val mapFrag = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            mapFrag?.getMapAsync { map ->
                Log.d("MapDebug", "지도 로드 완료 - 초기화 시작")

                try {
                    googleMap = map
                    polylineManager = PolylineManager(map)

                    // 지도 기본 설정 먼저
                    setupMapSettings(map)

                    // SafetyOverlayManager 초기화 (약간 지연)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            Log.d("MapDebug", "SafetyOverlayManager 초기화 시도")
                            safetyOverlayManager = SafetyOverlayManager(map)

                            if (safetyOverlayManager != null) {
                                isMapReady = true
                                Log.d("MapDebug", "SafetyOverlayManager 초기화 성공!")

                                // 대기 중인 안전도 평가들 처리
                                processPendingSafetyEvaluations()

                                // 테스트 오버레이 추가 (디버깅용)
                                addTestOverlay()

                            } else {
                                Log.e("MapDebug", "SafetyOverlayManager 초기화 실패 - null 반환")
                            }
                        } catch (e: Exception) {
                            Log.e("MapDebug", "SafetyOverlayManager 초기화 오류: ${e.message}")
                            e.printStackTrace()
                        }
                    }, 1000) // 1초 지연

                    // 우회 경로 관리자 초기화
                    routeAvoidanceManager = RouteAvoidanceManager()

                    Log.d("MapDebug", "지도 초기화 완료")
                } catch (e: Exception) {
                    Log.e("MapDebug", "지도 초기화 오류: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Fitness 옵션 설정
            exploreStartTime = System.currentTimeMillis()
            fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .build()

            try {
                val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
                Fitness.getRecordingClient(requireContext(), account).subscribe(DataType.TYPE_STEP_COUNT_DELTA)
                Fitness.getRecordingClient(requireContext(), account).subscribe(DataType.TYPE_DISTANCE_DELTA)
                Fitness.getRecordingClient(requireContext(), account).subscribe(DataType.TYPE_CALORIES_EXPENDED)
            } catch (e: Exception) {
                Log.w("ExploreTracking", "Fitness API 초기화 실패: ${e.message}")
            }

            // Firebase 초기화
            db = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()

            // 안전도 ViewModel 초기화
            try {
                val safetyRepo = SafetyRepo(
                    kakao = RetrofitModule.kakaoApi,
                    safetyService = RetrofitModule.safetyService
                )
                safetyViewModel = ViewModelProvider(
                    this,
                    SafetyVMFactory(safetyRepo)
                )[SafetyViewModel::class.java]

                // 안전도 관찰자 설정
                safetyViewModel.safety.observe(viewLifecycleOwner) { detail ->
                    detail ?: return@observe

                    Log.d("SafetyDebug", "안전도 평가 결과 - 점수: ${detail.score}, 레벨: ${detail.level}")

                    latestSafetyLine = " · 안전도 ${detail.score} (${detail.level.name})"

                    // 배너 업데이트
                    updateSafetyBanner(detail)

                    // 오버레이 처리
                    lastSafetyEvalLoc?.let { location ->
                        handleSafetyEvaluation(location.latitude, location.longitude, detail)
                    } ?: run {
                        Log.w("SafetyDebug", "lastSafetyEvalLoc이 null입니다!")
                    }
                }
            } catch (e: Exception) {
                Log.e("ExploreTracking", "SafetyViewModel 초기화 오류: ${e.message}")
            }

            startLocationTracking()

        } catch (e: Exception) {
            Log.e("ExploreTracking", "onCreateView 오류: ${e.message}")
            e.printStackTrace()
        }

        return view
    }

    private fun updateSafetyBanner(detail: SafetyDetail) {
        try {
            safetyBanner?.visibility = View.VISIBLE
            safetyBannerText?.text = "현재 안전도: ${detail.score} (${detail.level.name})"

            when (detail.level) {
                SafetyDetail.Level.SAFE -> {
                    safetyBanner?.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
                    )
                }
                SafetyDetail.Level.CAUTION -> {
                    safetyBanner?.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light)
                    )
                }
                SafetyDetail.Level.DANGER -> {
                    safetyBanner?.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
                    )
                    triggerDangerVibration()
                }
            }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "안전도 배너 업데이트 오류: ${e.message}")
        }
    }

    private fun handleSafetyEvaluation(lat: Double, lng: Double, safetyDetail: SafetyDetail) {
        if (isMapReady && safetyOverlayManager != null) {
            Log.d("SafetyDebug", "즉시 오버레이 추가 - 위치: $lat, $lng")
            try {
                safetyOverlayManager?.addSafetyEvaluation(lat, lng, safetyDetail)
                val dangerCount = safetyOverlayManager?.getDangerAreaCount() ?: 0
                Log.d("SafetyDebug", "현재 위험 지역 개수: $dangerCount")
            } catch (e: Exception) {
                Log.e("SafetyDebug", "오버레이 추가 실패: ${e.message}")
            }
        } else {
            Log.d("SafetyDebug", "지도 준비 안됨 - 대기열에 추가")
            pendingSafetyEvaluations.add(PendingSafetyEvaluation(lat, lng, safetyDetail))
        }
    }

    private fun processPendingSafetyEvaluations() {
        if (pendingSafetyEvaluations.isNotEmpty() && safetyOverlayManager != null) {
            Log.d("SafetyDebug", "대기 중인 안전도 평가 ${pendingSafetyEvaluations.size}개 처리")

            pendingSafetyEvaluations.forEach { pending ->
                try {
                    safetyOverlayManager?.addSafetyEvaluation(
                        pending.lat,
                        pending.lng,
                        pending.safetyDetail
                    )
                } catch (e: Exception) {
                    Log.e("SafetyDebug", "대기 중인 평가 처리 실패: ${e.message}")
                }
            }

            val finalCount = safetyOverlayManager?.getDangerAreaCount() ?: 0
            Log.d("SafetyDebug", "대기 중인 평가 처리 완료 - 총 위험 지역: $finalCount 개")

            pendingSafetyEvaluations.clear()
        }
    }

    private fun addTestOverlay() {
        try {
            Log.d("SafetyDebug", "테스트 오버레이 추가 시작")

            val testSafetyDetail = SafetyDetail(
                score = 2,
                level = SafetyDetail.Level.DANGER,
                convCount = 0,
                publicCount = 0,
                subwayCount = 0,
                cctvCount = 0,
                streetLightCount = 0,
                reasons = listOf("테스트 위험 지역")
            )

            safetyOverlayManager?.addSafetyEvaluation(
                37.5107092, 127.0941135, // 서울시청 좌표
                testSafetyDetail
            )

            // 카메라를 테스트 위치로 이동
            googleMap?.animateCamera(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    com.google.android.gms.maps.model.LatLng(37.5107092, 127.0941135),
                    15f
                )
            )

            val dangerCount = safetyOverlayManager?.getDangerAreaCount() ?: 0
            Log.d("SafetyDebug", "테스트 오버레이 추가 완료 - 총 위험 지역: $dangerCount 개")

            if (isAdded && context != null) {
                Toast.makeText(requireContext(), "서울시청에 테스트 위험 지역이 표시되었습니다!", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Log.e("SafetyDebug", "테스트 오버레이 추가 실패: ${e.message}")
            e.printStackTrace()
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
        } catch (e: SecurityException) {
            Log.e("MapDebug", "위치 권한 없음: ${e.message}")
        } catch (e: Exception) {
            Log.e("MapDebug", "지도 설정 오류: ${e.message}")
        }
    }

    private fun startLocationTracking() {
        val request = LocationRequest.create().apply {
            interval = 2000
            fastestInterval = 1000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val current = result.lastLocation ?: return

                if (!isAdded || activity == null || view == null) return

                requireActivity().runOnUiThread {
                    try {
                        // 속도 감지
                        handleSpeedDetection(current)

                        lastLocation = current
                        lastLocationTime = current.elapsedRealtimeNanos

                        // 위험 지역 진입 체크
                        checkDangerAreaEntry(current)

                        // 안전도 평가
                        evaluateSafetyIfNeeded(current)

                        // 우회 경로 계산 및 네비게이션
                        handleNavigationAndDetour(current)


                    val distance = calculateDistance(current.latitude, current.longitude)
                    // 🚀 MODIFIED: 장소 이름을 포함하여 텍스트 업데이트
                    tvDistanceInfo.text = "${targetPlaceName ?: "목표 장소"} 남은 거리: %.1f m".format(distance)

                    val destLoc = Location("dest").apply {
                        latitude = targetLat
                        longitude = targetLng
                    }
                    val bearing = calculateBearing(current, destLoc)
                    arrowImageView.rotation = bearing

                    val roundedLevel = (distance / 100).toInt()
                    if (roundedLevel < lastVibrationLevel) {
                        triggerVibration()
                        lastVibrationLevel = roundedLevel
                    }

                    val currentLevel50m = (distance / 50).toInt()
                    if (currentLevel50m != lastLoggedDistanceLevel) {
                        if (lastLoggedDistanceLevel != -1) {
                            if (currentLevel50m < lastLoggedDistanceLevel) {
                                Log.d("Explore", "🔵 더 가까워졌습니다: ${distance.toInt()}m")
                            } else {
                                Log.d("Explore", "🔴 더 멀어졌습니다: ${distance.toInt()}m")
                            }
                        }
                        lastLoggedDistanceLevel = currentLevel50m
                    }

                    if (distance < 30 && !arrivalDialogShown) {
                        arrivalDialogShown = true
                        onArriveAtPlace()
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (isAdded && context != null) {
                Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e("ExploreTracking", "위치 업데이트 시작 오류: ${e.message}")
        }
    }

    private fun handleSpeedDetection(current: Location) {
        if (lastLocation != null && lastLocationTime != 0L) {
            val timeDeltaSeconds = (current.elapsedRealtimeNanos - lastLocationTime) / 1_000_000_000.0
            val distanceDeltaMeters = current.distanceTo(lastLocation!!)
            val speedMs = if (timeDeltaSeconds > 0) (distanceDeltaMeters / timeDeltaSeconds) else 0.0
            val speedKmh = speedMs * 3.6

            if (speedKmh >= 30.0) {
                if (isExploringActive) {
                    isExploringActive = false
                    tvSpeedWarning.visibility = View.VISIBLE
                    tvDistanceInfo.visibility = View.GONE
                    arrowImageView.visibility = View.GONE
                    if (isAdded && context != null) {
                        Toast.makeText(requireContext(), "이동수단에서 내린 후 진행해주세요.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                if (!isExploringActive) {
                    isExploringActive = true
                    tvSpeedWarning.visibility = View.GONE
                    tvDistanceInfo.visibility = View.VISIBLE
                    arrowImageView.visibility = View.VISIBLE
                    if (isAdded && context != null) {
                        Toast.makeText(requireContext(), "탐색을 재개합니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun checkDangerAreaEntry(current: Location) {
        val isInDangerArea = safetyOverlayManager?.isLocationInDangerArea(
            current.latitude,
            current.longitude
        ) ?: false

        if (isInDangerArea) {
            showDangerAreaWarning()
        }
    }

    private fun evaluateSafetyIfNeeded(current: Location) {
        val now = System.currentTimeMillis()
        val needEval = when {
            lastSafetyEvalLoc == null -> true
            current.distanceTo(lastSafetyEvalLoc!!) >= 200f -> true
            (now - lastSafetyEvalTime) >= 30_000L -> true
            else -> false
        }
        if (needEval && ::safetyViewModel.isInitialized) {
            safetyViewModel.evaluate(current.latitude, current.longitude)
            lastSafetyEvalLoc = current
            lastSafetyEvalTime = now
        }
    }

    private fun handleNavigationAndDetour(current: Location) {
        if (::routeAvoidanceManager.isInitialized && safetyOverlayManager != null) {
            val currentLatLng = LatLng(current.latitude, current.longitude)
            val finalDestination = LatLng(targetLat, targetLng)
            val dangerAreas = safetyOverlayManager?.getDangerAreas() ?: emptyList()

            val navigationResult = routeAvoidanceManager.calculateNavigation(
                currentLatLng,
                finalDestination,
                dangerAreas
            )

            updateDetourStatus(navigationResult)

            if (isExploringActive) {
                polylineManager?.addPointToPath(LatLng(current.latitude, current.longitude))
                updateNavigationDisplay(current, navigationResult)

                val finalDistance = calculateDistance(current.latitude, current.longitude)
                val roundedLevel = (finalDistance / 100).toInt()
                if (roundedLevel < lastVibrationLevel) {
                    triggerVibration()
                    lastVibrationLevel = roundedLevel
                }

                if (finalDistance < 50 && !arrivalDialogShown) {
                    arrivalDialogShown = true
                    onArriveAtPlace()
                }
            }
        } else {
            // 기본 네비게이션
            if (isExploringActive) {
                polylineManager?.addPointToPath(LatLng(current.latitude, current.longitude))

                val distance = calculateDistance(current.latitude, current.longitude)
                tvDistanceInfo.text = "${targetPlaceName ?: "목표 장소"} 남은 거리: %.1f m".format(distance) + latestSafetyLine

                val destLoc = Location("dest").apply {
                    latitude = targetLat
                    longitude = targetLng
                }
                arrowImageView.rotation = calculateBearing(current, destLoc)

                val roundedLevel = (distance / 100).toInt()
                if (roundedLevel < lastVibrationLevel) {
                    triggerVibration()
                    lastVibrationLevel = roundedLevel
                }

                if (distance < 50 && !arrivalDialogShown) {
                    arrivalDialogShown = true
                    onArriveAtPlace()
                }
            }
        }
    }

    private fun updateDetourStatus(navigationResult: RouteAvoidanceManager.NavigationResult) {
        try {
            if (!isAdded || context == null) return

            val wasDetourActive = isDetourActive
            isDetourActive = navigationResult.isDetourActive

            if (!wasDetourActive && isDetourActive) {
                Toast.makeText(
                    requireContext(),
                    navigationResult.detourMessage ?: "우회 경로로 안내합니다",
                    Toast.LENGTH_LONG
                ).show()
                triggerDangerVibration()
                Log.i("ExploreTracking", "우회 경로 시작")
            }

            if (wasDetourActive && !isDetourActive && navigationResult.detourMessage != null) {
                Toast.makeText(
                    requireContext(),
                    navigationResult.detourMessage,
                    Toast.LENGTH_SHORT
                ).show()
                Log.i("ExploreTracking", "우회 경로 완료")
            }

            lastDetourMessage = navigationResult.detourMessage
        } catch (e: Exception) {
            Log.e("ExploreTracking", "우회 상태 업데이트 오류: ${e.message}")
        }
    }

    private fun updateNavigationDisplay(
        currentLocation: Location,
        navigationResult: RouteAvoidanceManager.NavigationResult
    ) {
        try {
            if (!isAdded || view == null) return

            val targetLocation = Location("target").apply {
                latitude = navigationResult.targetLocation.latitude
                longitude = navigationResult.targetLocation.longitude
            }
            arrowImageView.rotation = calculateBearing(currentLocation, targetLocation)

            val distanceText = if (navigationResult.isDetourActive) {
                val detourDistance = navigationResult.distanceToTarget.toInt()
                val finalDistance = calculateDistance(currentLocation.latitude, currentLocation.longitude).toInt()
                "🔄 우회 중: ${detourDistance}m → ${targetPlaceName ?: "목표"} (최종: ${finalDistance}m)"
            } else {
                val distance = calculateDistance(currentLocation.latitude, currentLocation.longitude)
                "${targetPlaceName ?: "목표 장소"} 남은 거리: %.1f m".format(distance)
            }

            tvDistanceInfo.text = distanceText + latestSafetyLine

        } catch (e: Exception) {
            Log.e("ExploreTracking", "네비게이션 표시 업데이트 오류: ${e.message}")
        }
    }

    private fun showDangerAreaWarning() {
        try {
            if (isAdded && context != null) {
                Toast.makeText(
                    requireContext(),
                    "⚠️ 위험 지역에 진입했습니다. 주의하세요!",
                    Toast.LENGTH_LONG
                ).show()
                triggerDangerVibration()
                Log.w("ExploreTracking", "사용자가 위험 지역에 진입했습니다")
            }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "위험 지역 경고 표시 오류: ${e.message}")
        }
    }

    private fun onArriveAtPlace() {
        if (!isExploringActive) return

        val endTime = System.currentTimeMillis()

        try {
            val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)

            val readRequest = DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_DISTANCE_DELTA)
                .aggregate(DataType.TYPE_CALORIES_EXPENDED)
                .setTimeRange(exploreStartTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.MINUTES)
                .build()

            Fitness.getHistoryClient(requireContext(), account)
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

                    if (isAdded) {
                        targetImageUrl?.let { imageUrl ->
                            ExploreResultDialogFragment
                                .newInstance("confirm", imageUrl, placeId ?: "", totalSteps, totalDistance, totalCalories)
                                .show(parentFragmentManager, "explore_confirm")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ExploreTracking", "Fitness 데이터 읽기 실패: ${e.message}")
                    if (isAdded) {
                        targetImageUrl?.let { imageUrl ->
                            ExploreResultDialogFragment
                                .newInstance("confirm", imageUrl, placeId ?: "", 0, 0.0, 0.0)
                                .show(parentFragmentManager, "explore_confirm")
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "도착 처리 오류: ${e.message}")
        }
    }

    private fun calculateDistance(currentLat: Double, currentLng: Double): Float {
        val curLoc = Location("cur").apply {
            latitude = currentLat
            longitude = currentLng
        }
        val destLoc = Location("dest").apply {
            latitude = targetLat
            longitude = targetLng
        }
        return curLoc.distanceTo(destLoc)
    }

    private fun calculateBearing(start: Location, end: Location): Float {
        val startLat = Math.toRadians(start.latitude)
        val startLng = Math.toRadians(start.longitude)
        val endLat = Math.toRadians(end.latitude)
        val endLng = Math.toRadians(end.longitude)
        val dLng = endLng - startLng
        val y = sin(dLng) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLng)
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }

    private fun triggerVibration() {
        try {
            if (isAdded && context != null) {
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
            }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "일반 진동 오류: ${e.message}")
        }
    }

    private fun triggerDangerVibration() {
        try {
            if (isAdded && context != null) {
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = longArrayOf(0, 200, 100, 400, 100, 200)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    val pattern = longArrayOf(0, 200, 100, 400, 100, 200)
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "위험 진동 오류: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            if (::fusedLocationClient.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }

            polylineManager?.clearPath()
            polylineManager = null

            safetyOverlayManager?.clearAllOverlays()
            safetyOverlayManager = null

            if (::routeAvoidanceManager.isInitialized) {
                routeAvoidanceManager.cancelDetour()
            }

            pendingSafetyEvaluations.clear()
            isMapReady = false

            Log.d("ExploreTracking", "Fragment 정리 완료")
        } catch (e: Exception) {
            Log.e("ExploreTracking", "Fragment 정리 중 오류: ${e.message}")
        }
    }

    fun onPhotoTaken(photoUri: android.net.Uri) {
        try {
            if (isAdded) {
                targetImageUrl?.let { url ->
                    ExploreResultDialogFragment
                        .newInstance("fail", url, placeId ?: "")
                        .show(parentFragmentManager, "explore_result")
                }
            }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "사진 촬영 처리 오류: ${e.message}")
        }
    }

    companion object {
        fun newInstance(placeId: String, lat: Double, lng: Double, imageUrl: String, placeName: String): ExploreTrackingFragment {
            return ExploreTrackingFragment().apply {
                arguments = Bundle().apply {
                    putString("placeId", placeId)
                    putDouble("targetLat", lat)
                    putDouble("targetLng", lng)
                    putString("targetImageUrl", imageUrl)
                    putString("targetPlaceName", placeName)
                }
            }
        }
    }
}