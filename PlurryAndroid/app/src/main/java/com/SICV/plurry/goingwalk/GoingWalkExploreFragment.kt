package com.SICV.plurry.goingwalk

import LogLS
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
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
import com.SICV.plurry.R
import com.SICV.plurry.di.RetrofitModule
import com.SICV.plurry.safety.SafetyRepo
import com.SICV.plurry.safety.viewmodel.SafetyVMFactory
import com.SICV.plurry.safety.viewmodel.SafetyViewModel
import com.SICV.plurry.safety.model.SafetyDetail
import com.SICV.plurry.safety.SafetyOverlayManager
import com.SICV.plurry.safety.RouteAvoidanceManager

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.SICV.plurry.MainActivity
import com.SICV.plurry.safety.CustomDanger
import android.view.Surface

class GoingWalkExploreFragment : Fragment(), SensorEventListener {

    //private lateinit var fusedLocationClient: FusedLocationProviderClient //Map
    //private lateinit var locationCallback: LocationCallback
    private lateinit var tvDistanceInfo: TextView
    private lateinit var tvPlaceName: TextView
    private lateinit var arrowImageView: ImageView
    private lateinit var imgTargetPreview: ImageView
    private lateinit var btnExitExplore: Button
    //private lateinit var mapFragment: SupportMapFragment //Map
    private lateinit var tvSpeedWarning: TextView

    // 나침반 및 방향 센서 관련 변수
    private lateinit var sensorManager: SensorManager
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val R_Matrix = FloatArray(9)
    private val I = FloatArray(9)
    private val orientation = FloatArray(3)
    private var currentAzimuth = 0f
    //정확도 높이기
    private var rotationVectorSensor: Sensor? = null
    private var lastTrueHeading = 0f

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
    //private var polylineManager: PolylineManager? = null //Map

    private var isImageZoomed = false

    // 안전도 관련
    private lateinit var safetyViewModel: SafetyViewModel
    private var lastSafetyEvalLoc: Location? = null
    private var lastSafetyEvalTime: Long = 0L
    private var latestSafetyLine: String = ""
    private var safetyBanner: View? = null
    private var safetyBannerText: TextView? = null
    private var safetyOverlayManager: SafetyOverlayManager? = null
    private var currentSafety: SafetyDetail.Level = SafetyDetail.Level.SAFE

    // 우회 경로 관련
    private lateinit var routeAvoidanceManager: RouteAvoidanceManager
    private var isDetourActive = false
    private var lastDetourMessage: String? = null

    // 지도 준비 상태 관리
    private var isMapReady = false  //Map
    private val pendingSafetyEvaluations = mutableListOf<PendingSafetyEvaluation>()
    //private var hasInitialCameraMove = false  //Map

    // Danger gate (위험지역 경고 1회 + 이탈 후에만 재발)
    private var inDanger = false
    private var safeStreak = 0
    private var lastDangerNotifyAt = 0L
    private val DANGER_COOLDOWN_MS = 10_000L
    private val SAFE_CLEAR_COUNT = 3

    //탐색 범위 거리 조정
    private val distanceLevel1 = 10
    private val distanceLevel2 = 8
    private val distancearrive = 5

    // Arrive gate (목표 도착 1회 호출)
    private var hasArrived = false
    private var lastArriveTime = 0L
    private val ARRIVE_RADIUS_M = distancearrive
    private val EXIT_RADIUS_M = distancearrive + 5
    private val ARRIVE_COOLDOWN_MS = 10_000L

    //실내시연 타이머 호출 Handler
    private val indoorExploregHandler = Handler(Looper.getMainLooper())
    private var indoorExploreRunnable: Runnable? = null
    private val dangerAlertInterval = 15
    private val safeAlertInterval = 10
    private var indoorExploreDistance = 20
    private var indoorExploreSpeed = 3
    private val indoorExploreTargetName = "플루리실내시연"

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
        val view = inflater.inflate(R.layout.fragment_goingwalk_explore, container, false)

        //LogLS.d("Begin")

        try {
            tvDistanceInfo = view.findViewById(R.id.tvDistanceInfo)
            tvPlaceName = view.findViewById(R.id.tvPlaceName)
            arrowImageView = view.findViewById(R.id.arrowImageView)
            imgTargetPreview = view.findViewById(R.id.imgTargetPreview)
            btnExitExplore = view.findViewById(R.id.btnExitExplore)
            tvSpeedWarning = view.findViewById(R.id.tvSpeedWarning)

            // 안전도 배너 (nullable 처리)
            safetyBannerText = safetyBanner?.findViewById(R.id.safetyBannerText)

            //fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext()) //Map

            arguments?.let {
                placeId = it.getString("placeId")
                targetLat = it.getDouble("targetLat")
                targetLng = it.getDouble("targetLng")
                targetImageUrl = it.getString("targetImageUrl")
                targetPlaceName = it.getString("targetPlaceName")
            }

            // 이미지 로드
            targetImageUrl?.let { url ->
                Glide.with(this)
                    .load(url)
                    .override(80.dpToPx(), 80.dpToPx())
                    .into(imgTargetPreview)
            }

            // 이미지 클릭 시 확대/축소 토글 로직
            imgTargetPreview.setOnClickListener {
                if (isImageZoomed) {
                    imgTargetPreview.layoutParams.width = 80.dpToPx()
                    imgTargetPreview.layoutParams.height = 80.dpToPx()
                    targetImageUrl?.let { url ->
                        Glide.with(this)
                            .load(url)
                            .override(80.dpToPx(), 80.dpToPx())
                            .into(imgTargetPreview)
                    }
                } else {
                    imgTargetPreview.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    imgTargetPreview.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    targetImageUrl?.let { url ->
                        Glide.with(this)
                            .load(url)
                            .override(Target.SIZE_ORIGINAL)
                            .into(imgTargetPreview)
                    }
                }
                imgTargetPreview.requestLayout()
                isImageZoomed = !isImageZoomed
            }

            btnExitExplore.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            })


            // parentFragmentManager를 통해 부모 Fragment 찾기
            val parentMainFragment = parentFragmentManager.fragments.firstOrNull { it is GoingWalkMainFragment } as? GoingWalkMainFragment

            // 디버깅
//            Log.d("MapDebug", "parentFragmentManager.fragments: ${parentFragmentManager.fragments}")
//            LogLS.d("parentFragmentManager.fragments: ${parentFragmentManager.fragments}")
//            Log.d("MapDebug", "parentMainFragment: $parentMainFragment")
//            LogLS.d("parentMainFragment: $parentMainFragment")

            val mapFrag = parentMainFragment?.mapFragment

            if (mapFrag == null) {
                Log.e("MapDebug", "MapFragment를 찾을 수 없습니다!")
                LogLS.e("MapFragment를 찾을 수 없습니다!")
                Toast.makeText(requireContext(), "지도 로딩 실패", Toast.LENGTH_LONG).show()
                return view
            }

            //mapFragment = mapFrag
            mapFrag.getMapAsync { map ->
                //Log.d("MapDebug", "지도 로드 완료 - 초기화 시작")
                //LogLS.d("지도 로드 완료 - 초기화 시작")
                //Log.d("MapDebug", "전달된 목표 위치: lat=$targetLat, lng=$targetLng")
                //LogLS.d("전달된 목표 위치: lat=$targetLat, lng=$targetLng")

                try {
                    // SafetyOverlayManager 초기화 (약간 지연)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            //Log.d("MapDebug", "SafetyOverlayManager 초기화 시도")
                            //LogLS.d("SafetyOverlayManager 초기화 시도")
                            safetyOverlayManager = SafetyOverlayManager(map)

                            if (safetyOverlayManager != null) {
                                isMapReady = true
                                //Log.d("MapDebug", "SafetyOverlayManager 초기화 성공!")
                                //LogLS.d("SafetyOverlayManager 초기화 성공!")
                                CustomDanger.applyToOverlay(safetyOverlayManager)
                                processPendingSafetyEvaluations()
                            } else {
                                Log.e("MapDebug", "SafetyOverlayManager 초기화 실패 - null 반환")
                                LogLS.e("SafetyOverlayManager 초기화 실패 - null 반환")
                            }
                        } catch (e: Exception) {
                            Log.e("MapDebug", "SafetyOverlayManager 초기화 오류: ${e.message}")
                            LogLS.e("SafetyOverlayManager 초기화 오류: ${e.message}")
                            e.printStackTrace()
                        }
                    }, 1000)

                    // 우회 경로 관리자 초기화
                    routeAvoidanceManager = RouteAvoidanceManager()

                    //Log.d("MapDebug", "지도 초기화 완료")
                    //LogLS.d("지도 초기화 완료")
                } catch (e: Exception) {
                    Log.e("MapDebug", "지도 초기화 오류: ${e.message}")
                    LogLS.e("지도 초기화 오류: ${e.message}")
                    e.printStackTrace()
                }
            }

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
                LogLS.w("Fitness API 초기화 실패: ${e.message}")
            }

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

                safetyViewModel.safety.observe(viewLifecycleOwner) { detail ->
                    detail ?: return@observe

                    //Log.d("SafetyDebug", "안전도 평가 결과 - 점수: ${detail.score}, 레벨: ${detail.level}")
                    //LogLS.d("안전도 평가 결과 - 점수: ${detail.score}, 레벨: ${detail.level}")

                    latestSafetyLine = " · 안전도 ${detail.score} (${detail.level.name})"
                    updateSafetyBanner(detail)

                    lastSafetyEvalLoc?.let { location ->
                        handleSafetyEvaluation(location.latitude, location.longitude, detail)
                    } ?: run {
                        Log.w("SafetyDebug", "lastSafetyEvalLoc이 null입니다!")
                        LogLS.w("lastSafetyEvalLoc이 null입니다!")
                    }
                }
            } catch (e: Exception) {
                Log.e("ExploreTracking", "SafetyViewModel 초기화 오류: ${e.message}")
                LogLS.e("SafetyViewModel 초기화 오류: ${e.message}")
            }

            // 센서 관리자 및 센서 초기화
            sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            startLocationTracking()

        } catch (e: Exception) {
            Log.e("ExploreTracking", "onCreateView 오류: ${e.message}")
            LogLS.e("onCreateView 오류: ${e.message}")
            e.printStackTrace()
        }

        return view
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // SensorEventListener 구현
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val R = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(R, event.values)

                // 디스플레이 회전에 맞춰 좌표계 재맵핑 (세로/가로 모드 보정)
                val outR = FloatArray(9)
                val rotation = getDisplayRotation()
                when (rotation) {
                    Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                        R, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR
                    )
                    Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                        R, SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, outR
                    )
                    Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                        R, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z, outR
                    )
                    Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                        R, SensorManager.AXIS_MINUS_Z, SensorManager.AXIS_X, outR
                    )
                    else -> System.arraycopy(R, 0, outR, 0, 9)
                }

                val orientation = FloatArray(3)
                SensorManager.getOrientation(outR, orientation)

                // 자북(자기북) → 진북 보정
                var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuthDeg < 0f) azimuthDeg += 360f

                // 현재 위치가 있으면 편차(Declination) 보정
                lastLocation?.let { loc ->
                    val gmf = android.hardware.GeomagneticField(
                        loc.latitude.toFloat(),
                        loc.longitude.toFloat(),
                        loc.altitude.toFloat(),
                        System.currentTimeMillis()
                    )
                    azimuthDeg = (azimuthDeg + gmf.declination) % 360f
                    if (azimuthDeg < 0f) azimuthDeg += 360f
                }

                // (선택) 원형 EMA 스무딩으로 바늘 흔들림 억제
                currentAzimuth = smoothHeading(lastTrueHeading, azimuthDeg, alpha = 0.25f)
                lastTrueHeading = currentAzimuth
            }

            // ↓ 회전벡터가 없을 때를 대비한 fallback (원하면 삭제)
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD -> {
                // no-op 또는 기존 코드 유지
            }
        }
    }

    private fun smoothHeading(prev: Float, now: Float, alpha: Float): Float {
        val diff = (((now - prev + 540f) % 360f) - 180f) // -180~180
        var out = (prev + alpha * diff) % 360f
        if (out < 0f) out += 360f
        return out
    }

    private fun getDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+
            requireActivity().display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.rotation  // API 24~29
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun updateSafetyBanner(detail: SafetyDetail) {
        try {
            safetyBanner?.visibility = View.VISIBLE
            safetyBannerText?.text = "현재 안전도: ${detail.score} (${detail.level.name})"

            when (detail.level) {
                SafetyDetail.Level.SAFE -> {
                    safetyBanner?.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
                    )
                    if(currentSafety!=SafetyDetail.Level.SAFE) {
                        currentSafety=SafetyDetail.Level.SAFE
                        updateSea("SAFE")
                    }
                }
                SafetyDetail.Level.CAUTION -> {
                    safetyBanner?.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light)
                    )
                    if(currentSafety!=SafetyDetail.Level.CAUTION) {
                        currentSafety=SafetyDetail.Level.CAUTION
                        updateSea("CAUTION")
                    }
                }
                SafetyDetail.Level.DANGER -> {
                    safetyBanner?.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
                    )
                    if(currentSafety!=SafetyDetail.Level.DANGER) {
                        currentSafety=SafetyDetail.Level.DANGER
                        updateSea("DANGER")
                    }
                    triggerDangerVibration()
                }
            }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "안전도 배너 업데이트 오류: ${e.message}")
            LogLS.e("안전도 배너 업데이트 오류: ${e.message}")
        }
    }

    private fun updateSea(safety:String) {
        val activity = requireActivity()
        if (activity is MainActivity) {
            activity.SetSafety(safety)
        } else {
            LogLS.e("MainActivity가 아닙니다!")
        }
    }

    private fun handleSafetyEvaluation(lat: Double, lng: Double, safetyDetail: SafetyDetail) {
        if (isMapReady && safetyOverlayManager != null) {
            Log.d("SafetyDebug", "즉시 오버레이 추가 - 위치: $lat, $lng")
            //LogLS.d("즉시 오버레이 추가 - 위치: $lat, $lng")
            try {
                safetyOverlayManager?.addSafetyEvaluation(lat, lng, safetyDetail)
                val dangerCount = safetyOverlayManager?.getDangerAreaCount() ?: 0
                Log.d("SafetyDebug", "현재 위험 지역 개수: $dangerCount")
                //LogLS.d("현재 위험 지역 개수: $dangerCount")
            } catch (e: Exception) {
                Log.e("SafetyDebug", "오버레이 추가 실패: ${e.message}")
                LogLS.e("오버레이 추가 실패: ${e.message}")
            }
        } else {
            Log.d("SafetyDebug", "지도 준비 안됨 - 대기열에 추가")
            //LogLS.d("지도 준비 안됨 - 대기열에 추가")
            pendingSafetyEvaluations.add(PendingSafetyEvaluation(lat, lng, safetyDetail))
        }
    }

    private fun processPendingSafetyEvaluations() {
        if (pendingSafetyEvaluations.isNotEmpty() && safetyOverlayManager != null) {
            Log.d("SafetyDebug", "대기 중인 안전도 평가 ${pendingSafetyEvaluations.size}개 처리")
            //LogLS.d("대기 중인 안전도 평가 ${pendingSafetyEvaluations.size}개 처리")

            pendingSafetyEvaluations.forEach { pending ->
                try {
                    safetyOverlayManager?.addSafetyEvaluation(
                        pending.lat,
                        pending.lng,
                        pending.safetyDetail
                    )
                } catch (e: Exception) {
                    Log.e("SafetyDebug", "대기 중인 평가 처리 실패: ${e.message}")
                    LogLS.e("대기 중인 평가 처리 실패: ${e.message}")
                }
            }

            val finalCount = safetyOverlayManager?.getDangerAreaCount() ?: 0
            Log.d("SafetyDebug", "대기 중인 평가 처리 완료 - 총 위험 지역: $finalCount 개")
            //LogLS.d("대기 중인 평가 처리 완료 - 총 위험 지역: $finalCount 개")

            pendingSafetyEvaluations.clear()
        }
    }

    private fun startLocationTracking() {
        LogLS.d("Begin")

        hasArrived = false
        lastArriveTime = 0L
        inDanger = false
        safeStreak = 0
        lastDangerNotifyAt = 0L
        arrivalDialogShown = false



        if(targetPlaceName==indoorExploreTargetName) {
            Handler(Looper.getMainLooper()).postDelayed({
                startIndoorExplore()
            }, 3 * 1000L) // n초를 밀리초로
            return
        }


        // trackingTimeInterval마다 processLocationTracking 호출
/*        locationTrackingRunnable = object : Runnable {
            override fun run() {
                processLocationTracking()
                locationTrackingHandler.postDelayed(this, trackingTimeInterval)
            }
        }
        locationTrackingHandler.post(locationTrackingRunnable!!)*/

        // parentFragmentManager를 통해 부모 Fragment 찾기
        val parentMainFragment = parentFragmentManager.fragments.firstOrNull { it is GoingWalkMainFragment } as? GoingWalkMainFragment
        if(parentMainFragment!=null) {
            parentMainFragment.isExploreTracking = true
//            LogLS.d("isExploreTracking true됨")
        }
    }

    private fun startIndoorExplore() {
        Toast.makeText(requireContext(), "실내시연을 시작합니다.", Toast.LENGTH_SHORT).show()
        //if (!isAdded || activity == null || view == null) return
        var currentLocation:Location = Location("").apply {
            latitude = 37.6501888
            longitude = 127.0195337
        }

        // parentFragmentManager를 통해 부모 Fragment 찾기
        val parentMainFragment = parentFragmentManager.fragments.firstOrNull { it is GoingWalkMainFragment } as? GoingWalkMainFragment
        if(parentMainFragment!=null) {
            parentMainFragment.isIndoorExplore = true
            lastLocation?.let { currentLocation = it }
            LogLS.d("isExploreTracking true됨")
        }
        else {
            LogLS.e("parentMainFragment is null")
        }

/*        requireActivity().runOnUiThread {
            lastLocation = currentLocation
            lastLocationTime = currentLocation.elapsedRealtimeNanos

            // 위험 지역 진입 체크
            checkDangerAreaEntry(currentLocation)

            // 안전도 평가
            evaluateSafetyIfNeeded(currentLocation)

            // 우회 경로 계산 및 네비게이션
            handleNavigationAndDetour(currentLocation)
        }*/

        tvDistanceInfo.text = "${targetPlaceName ?: "목표 장소"} 남은 거리: %d m".format(indoorExploreDistance) + latestSafetyLine
    }

    public fun updateIndoorExploreRemainDistance() {
        LogLS.d("Begin")
        indoorExploreDistance-=indoorExploreSpeed

        if(indoorExploreDistance<dangerAlertInterval) {
            indoorCheckDangerAreaEntry(true)
        }

        if(indoorExploreDistance<safeAlertInterval) {
            indoorCheckDangerAreaEntry(false)
        }

        if(indoorExploreDistance<distancearrive) {
            onArriveAtPlace()
        }

        if(indoorExploreDistance<=0) {
            indoorExploreDistance = 0
        }

        tvDistanceInfo.text = "${targetPlaceName ?: "목표 장소"} 남은 거리: %d m".format(indoorExploreDistance) + latestSafetyLine
    }

    private fun indoorCheckDangerAreaEntry(isDanger:Boolean) {
        inDanger = isDanger
        safetyBanner?.visibility = View.VISIBLE

        if(inDanger) {
            //showDangerAreaWarning()
            safetyBannerText?.text = "현재 안전도: 위험"
            safetyBanner?.setBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
            )
            if(currentSafety!=SafetyDetail.Level.DANGER) {
                currentSafety=SafetyDetail.Level.DANGER
                updateSea("DANGER")
            }
            triggerDangerVibration()
        }
        else {
            safetyBanner?.setBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
            )
            if(currentSafety!=SafetyDetail.Level.SAFE) {
                currentSafety=SafetyDetail.Level.SAFE
                updateSea("SAFE")
            }
        }

    }

    public fun processLocationTracking(location: Location) {
//        LogLS.d("Begin")

/*        if (!isAdded || view == null) return

        val parentFragment = parentFragmentManager.fragments.firstOrNull { it is GoingWalkMainFragment } as? GoingWalkMainFragment
        val current = parentFragment?.lastLocation ?: return*/

        if (!isAdded || activity == null || view == null) return

        val current = location
        lastLocation = location

        requireActivity().runOnUiThread {
            // 속도 감지 및 탐색 기능 제어
            handleSpeedDetection(current)

            lastLocation = current
            lastLocationTime = current.elapsedRealtimeNanos

            // 위험 지역 진입 체크
            checkDangerAreaEntry(current)

            // 안전도 평가
            evaluateSafetyIfNeeded(current)

            // 우회 경로 계산 및 네비게이션
            handleNavigationAndDetour(current)

            // 탐색이 활성화된 상태에서만 거리 계산 및 진동 처리
            if (isExploringActive) {
                val distance = calculateDistance(current.latitude, current.longitude)

                val roundedLevel = (distance / distanceLevel1).toInt()
                if (roundedLevel < lastVibrationLevel) {
                    triggerVibration()
                    lastVibrationLevel = roundedLevel
                }

                val currentLevel50m = (distance / distanceLevel2).toInt()
                if (currentLevel50m != lastLoggedDistanceLevel) {
                    if (lastLoggedDistanceLevel != -1) {
                        if (currentLevel50m < lastLoggedDistanceLevel) {
                            Log.d("Explore", "🔵 더 가까워졌습니다: ${distance.toInt()}m")
                            //LogLS.d("🔵 더 가까워졌습니다: ${distance.toInt()}m")
                        } else {
                            Log.d("Explore", "🔴 더 멀어졌습니다: ${distance.toInt()}m")
                            //LogLS.d("🔴 더 멀어졌습니다: ${distance.toInt()}m")
                        }
                    }
                    lastLoggedDistanceLevel = currentLevel50m
                }

//                if (distance < distancearrive && !arrivalDialogShown) {
//                    arrivalDialogShown = true
//                    onArriveAtPlace()
//                }
                val now = System.currentTimeMillis()

                if (!hasArrived && distance < ARRIVE_RADIUS_M) {
                    if (now - lastArriveTime > ARRIVE_COOLDOWN_MS) {
                        hasArrived = true
                        lastArriveTime = now
                        onArriveAtPlace()
                    }
                } else if (hasArrived && distance > EXIT_RADIUS_M) {
                    // 50m 이상 벗어나야 다시 도착 가능
                    hasArrived = false
                }
            }
        }
    }

    private fun handleSpeedDetection(current: Location) {
        if (lastLocation != null && lastLocationTime != 0L) {
            val timeDeltaSeconds = (current.elapsedRealtimeNanos - lastLocationTime) / 1_000_000_000.0
            val distanceDeltaMeters = current.distanceTo(lastLocation!!)
            val speedMs = if (timeDeltaSeconds > 0) (distanceDeltaMeters / timeDeltaSeconds) else 0.0
            val speedKmh = speedMs * 3.6

            Log.d("ExploreTrackingFragment", "현재 속도: %.2f km/h".format(speedKmh))
            //LogLS.d("현재 속도: %.2f km/h".format(speedKmh))

            if (speedKmh >= 30.0) {
                if (isExploringActive) {
                    isExploringActive = false
                    tvSpeedWarning.visibility = View.VISIBLE
                    tvDistanceInfo.visibility = View.GONE
                    arrowImageView.visibility = View.GONE
                    tvPlaceName.visibility = View.GONE
                    Toast.makeText(requireContext(), "이동수단에서 내린 후 진행해주세요.", Toast.LENGTH_LONG).show()
                    Log.d("ExploreTrackingFragment", "속도 제한 초과: 탐색 기능 중지.")
                    //LogLS.d("속도 제한 초과: 탐색 기능 중지.")
                }
            } else {
                if (!isExploringActive) {
                    isExploringActive = true
                    tvSpeedWarning.visibility = View.GONE
                    tvDistanceInfo.visibility = View.VISIBLE
                    arrowImageView.visibility = View.VISIBLE
                    tvPlaceName.visibility = View.GONE
                    Toast.makeText(requireContext(), "탐색을 재개합니다.", Toast.LENGTH_SHORT).show()
                    Log.d("ExploreTrackingFragment", "속도 정상: 탐색 기능 재개.")
                    //LogLS.d("속도 정상: 탐색 기능 재개.")
                }
            }
        }
    }

    private fun checkDangerAreaEntry(current: Location) {
        val isInDangerArea = safetyOverlayManager?.isLocationInDangerArea(
            current.latitude,
            current.longitude
        ) ?: false

//        if (isInDangerArea) {
//            showDangerAreaWarning()
//        }
        val now = System.currentTimeMillis()

        if (!inDanger && isInDangerArea) {
            // 처음 진입: 쿨다운 내 재알림 방지
            if (now - lastDangerNotifyAt > DANGER_COOLDOWN_MS) {
                inDanger = true
                safeStreak = 0
                lastDangerNotifyAt = now
                showDangerAreaWarning() // ← 토스트+진동 1회
            }
        } else if (inDanger && !isInDangerArea) {
            // 연속 안전 판정 누적(히스테리시스 역할)
            safeStreak++
            if (safeStreak >= SAFE_CLEAR_COUNT) {
                inDanger = false
                safeStreak = 0
            }
        } else if (inDanger && isInDangerArea) {
            // 위험 상태 유지 중에는 추가 알림 없음
            safeStreak = 0
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
                //polylineManager?.addPointToPath(LatLng(current.latitude, current.longitude))
                updateNavigationDisplay(current, navigationResult)
            }
        } else {
            // 기본 네비게이션 (나침반 기능 포함)
            if (isExploringActive) {
                //polylineManager?.addPointToPath(LatLng(current.latitude, current.longitude))

                val distance = calculateDistance(current.latitude, current.longitude)
                tvDistanceInfo.text = "${targetPlaceName ?: "목표 장소"} 남은 거리: %.1f m".format(distance) + latestSafetyLine

                val destLoc = Location("dest").apply {
                    latitude = targetLat
                    longitude = targetLng
                }
                val bearingToTarget = calculateBearing(current, destLoc)

                // 화살표 회전 계산 (목표 방향 - 현재 휴대폰 방향)
                val relativeBearing = (bearingToTarget - currentAzimuth + 360) % 360
                arrowImageView.rotation = relativeBearing
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
                //LogLS.d("우회 경로 시작")
            }

            if (wasDetourActive && !isDetourActive && navigationResult.detourMessage != null) {
                Toast.makeText(
                    requireContext(),
                    navigationResult.detourMessage,
                    Toast.LENGTH_SHORT
                ).show()
                Log.i("ExploreTracking", "우회 경로 완료")
                //LogLS.d("우회 경로 완료")
            }

            lastDetourMessage = navigationResult.detourMessage
        } catch (e: Exception) {
            Log.e("ExploreTracking", "우회 상태 업데이트 오류: ${e.message}")
            LogLS.e("우회 상태 업데이트 오류: ${e.message}")
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

            // 나침반 기능을 활용한 화살표 회전
            val bearingToTarget = calculateBearing(currentLocation, targetLocation)
            val relativeBearing = (bearingToTarget - currentAzimuth + 360) % 360
            arrowImageView.rotation = relativeBearing

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
            LogLS.e("네비게이션 표시 업데이트 오류: ${e.message}")
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
                LogLS.w("사용자가 위험 지역에 진입했습니다")
            }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "위험 지역 경고 표시 오류: ${e.message}")
            LogLS.e("위험 지역 경고 표시 오류: ${e.message}")
        }
    }

    private fun onArriveAtPlace() {
        if (!isExploringActive) {
            Log.d("ExploreTrackingFragment", "탐색 기능이 비활성화되어 도착 처리를 건너뜁니다.")
            //LogLS.d("탐색 기능이 비활성화되어 도착 처리를 건너뜁니다.")
            return
        }

        LogLS.d("Begin")

        val parentMainFragment = parentFragmentManager.fragments.firstOrNull { it is GoingWalkMainFragment } as? GoingWalkMainFragment
        if(parentMainFragment!=null) {
            parentMainFragment.isExploreTracking = false
        }

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

                    Log.d("GoogleFit", "탐색 중 측정 결과 - 거리: ${"%.2f".format(totalDistance / 1000)}km, 걸음: $totalSteps, 칼로리: ${"%.1f".format(totalCalories)}kcal")
                    //LogLS.d("탐색 중 측정 결과 - 거리: ${"%.2f".format(totalDistance / 1000)}km, 걸음: $totalSteps, 칼로리: ${"%.1f".format(totalCalories)}kcal")

                    if (isAdded) {
                        targetImageUrl?.let { imageUrl ->
                            ExploreResultDialogFragment
                                .newInstance("confirm", imageUrl, placeId ?: "", totalSteps, totalDistance, totalCalories)
                                .show(parentFragmentManager, "explore_confirm")
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("GoogleFit", "탐색 GoogleFit 데이터 로딩 실패", it)
                    LogLS.e("탐색 GoogleFit 데이터 로딩 실패")
                }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "도착 처리 오류: ${e.message}")
            LogLS.e("도착 처리 오류: ${e.message}")
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
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e("ExploreTracking", "일반 진동 오류: ${e.message}")
            LogLS.e("일반 진동 오류: ${e.message}")
        }
    }

    private fun triggerDangerVibration() {
        LogLS.d("Begin")
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
            LogLS.e("위험 진동 오류: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LogLS.d("Begin")

//        locationTrackingHandler.removeCallbacksAndMessages(null)

        try {
            // MainActivity를 통해 현재 표시된 GoingWalkMainFragment 찾기
            val mainActivity = requireActivity() as? MainActivity
            val currentFragment = mainActivity?.supportFragmentManager?.findFragmentById(R.id.fragment_container)

            //LogLS.d("currentFragment: $currentFragment")

            if (currentFragment is GoingWalkMainFragment) {
                currentFragment.setButtonStateEndExplore()
                //LogLS.d("버튼 상태 복원 완료")
            } else {
                LogLS.w("GoingWalkMainFragment를 찾을 수 없습니다")
            }

            parentFragmentManager.popBackStack()


            //fusedLocationClient.removeLocationUpdates(locationCallback)
            sensorManager.unregisterListener(this)

            //polylineManager?.clearPath()
            //polylineManager = null

            safetyOverlayManager?.clearAllOverlays()
            safetyOverlayManager = null

            if (::routeAvoidanceManager.isInitialized) {
                routeAvoidanceManager.cancelDetour()
            }

            pendingSafetyEvaluations.clear()
            isMapReady = false

            Log.d("ExploreTracking", "Fragment 정리 완료")
            //LogLS.d("Fragment 정리 완료")
        } catch (e: Exception) {
            Log.e("ExploreTracking", "Fragment 정리 중 오류: ${e.message}")
            LogLS.e("Fragment 정리 중 오류: ${e.message}")
        }
    }

    fun onPhotoTaken(photoUri: android.net.Uri) {
        Log.d("Explore", "onPhotoTaken 호출됨! URI: $photoUri")
        //LogLS.d("onPhotoTaken 호출됨! URI: $photoUri")

        targetImageUrl?.let { url ->
            Log.d("Explore", "imageUrl 전달됨: $url")
            //LogLS.d("imageUrl 전달됨: $url")

            ExploreResultDialogFragment
                .newInstance("fail", url, placeId ?: "")
                .show(parentFragmentManager, "explore_result")

            Log.d("Explore", "팝업 show() 호출 완료!")
            //LogLS.d("팝업 show() 호출 완료!")
        } ?: run {
            Log.e("Explore", "targetImageUrl 이 null이야!!")
            LogLS.e("targetImageUrl 이 null이야!!")
        }
    }

    companion object {
        fun newInstance(placeId: String, lat: Double, lng: Double, imageUrl: String, placeName: String): GoingWalkExploreFragment {
            return GoingWalkExploreFragment().apply {
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