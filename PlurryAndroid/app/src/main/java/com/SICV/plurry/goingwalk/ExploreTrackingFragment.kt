// ExploreTrackingFragment.kt
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
import com.SICV.plurry.R
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import kotlin.math.*

class ExploreTrackingFragment : Fragment() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var tvDistanceInfo: TextView
    private lateinit var arrowImageView: ImageView
    private lateinit var imgTargetPreview: ImageView
    private lateinit var btnExitExplore: Button
    private lateinit var mapFragment: SupportMapFragment
    // 🚀 NEW: 속도 경고 메시지 TextView
    private lateinit var tvSpeedWarning: TextView

    private var googleMap: com.google.android.gms.maps.GoogleMap? = null
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var lastVibrationLevel = Int.MAX_VALUE
    private var lastLoggedDistanceLevel = -1
    private var arrivalDialogShown = false
    private var targetImageUrl: String? = null
    private var placeId: String? = null
    private var targetPlaceName: String? = null // 🚀 NEW: 장소 이름 변수 추가

    private lateinit var fitnessOptions: FitnessOptions
    private var exploreStartTime: Long = 0L

    // Firebase Firestore 인스턴스 (더 이상 여기서 직접 저장하지 않으므로, 이 인스턴스는 필요 없을 수 있습니다.
    // 하지만 다른 용도로 사용될 가능성이 있어 일단 유지합니다.)
    private lateinit var db: FirebaseFirestore
    // Firebase Auth 인스턴스 (사용자별 데이터 저장 시 필요)
    private lateinit var auth: FirebaseAuth

    // 🚀 NEW: 탐색 기능 활성화 여부
    private var isExploringActive = true
    // 🚀 NEW: 이전 위치 업데이트 시간 (속도 계산용)
    private var lastLocationTime: Long = 0L
    // 🚀 NEW: 이전 위치 (속도 계산용)
    private var lastLocation: Location? = null
    // 🚀 NEW: 경로 관리를 위한 PolylineManager 인스턴스
    private var polylineManager: PolylineManager? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.activity_goingwalk_exploremain, container, false)

        tvDistanceInfo = view.findViewById(R.id.tvDistanceInfo)
        arrowImageView = view.findViewById(R.id.arrowImageView)
        imgTargetPreview = view.findViewById(R.id.imgTargetPreview)
        btnExitExplore = view.findViewById(R.id.btnExitExplore)
        // 🚀 NEW: tvSpeedWarning 초기화
        tvSpeedWarning = view.findViewById(R.id.tvSpeedWarning)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        arguments?.let {
            placeId = it.getString("placeId")
            targetLat = it.getDouble("targetLat")
            targetLng = it.getDouble("targetLng")
            targetImageUrl = it.getString("targetImageUrl")
            targetPlaceName = it.getString("targetPlaceName") // 🚀 NEW: 장소 이름 가져오기
        }

        targetImageUrl?.let { url ->
            Glide.with(this)
                .load(url)
                .into(imgTargetPreview)
        }

        btnExitExplore.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        mapFragment = parentFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            // 🚀 NEW: GoogleMap 객체가 준비되면 PolylineManager를 초기화합니다.
            polylineManager = PolylineManager(map)
        }

        exploreStartTime = System.currentTimeMillis()
        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .build()

        val account = GoogleSignIn.getAccountForExtension(requireContext(), fitnessOptions)
        Fitness.getRecordingClient(requireContext(), account).subscribe(DataType.TYPE_STEP_COUNT_DELTA)
        Fitness.getRecordingClient(requireContext(), account).subscribe(DataType.TYPE_DISTANCE_DELTA)
        Fitness.getRecordingClient(requireContext(), account).subscribe(DataType.TYPE_CALORIES_EXPENDED)

        // Firebase 초기화 (다른 용도로 사용될 가능성 있어 유지)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        startLocationTracking()
        return view
    }

    private fun startLocationTracking() {
        val request = LocationRequest.create().apply {
            interval = 2000 // 🚀 수정: 속도 감지를 위해 interval을 더 짧게 설정
            fastestInterval = 1000 // 🚀 수정: 속도 감지를 위해 fastestInterval을 더 짧게 설정
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val current = result.lastLocation ?: return

                // 🚀 NEW: 속도 감지 및 탐색 기능 제어 로직
                if (lastLocation != null && lastLocationTime != 0L) {
                    val timeDeltaSeconds = (current.elapsedRealtimeNanos - lastLocationTime) / 1_000_000_000.0
                    val distanceDeltaMeters = current.distanceTo(lastLocation!!)

                    // 초당 미터 (m/s)
                    val speedMs = if (timeDeltaSeconds > 0) (distanceDeltaMeters / timeDeltaSeconds) else 0.0
                    // 시속 킬로미터 (km/h)
                    val speedKmh = speedMs * 3.6

                    Log.d("ExploreTrackingFragment", "현재 속도: %.2f km/h".format(speedKmh))

                    // 시속 30km 이상일 경우
                    if (speedKmh >= 30.0) {
                        if (isExploringActive) {
                            isExploringActive = false // 탐색 기능 비활성화
                            tvSpeedWarning.visibility = View.VISIBLE // 경고 메시지 표시
                            tvDistanceInfo.visibility = View.GONE // 거리 정보 숨김
                            arrowImageView.visibility = View.GONE // 화살표 숨김
                            Toast.makeText(requireContext(), "이동수단에서 내린 후 진행해주세요.", Toast.LENGTH_LONG).show()
                            Log.d("ExploreTrackingFragment", "속도 제한 초과: 탐색 기능 중지.")
                        }
                    } else {
                        if (!isExploringActive) {
                            isExploringActive = true // 탐색 기능 재활성화
                            tvSpeedWarning.visibility = View.GONE // 경고 메시지 숨김
                            tvDistanceInfo.visibility = View.VISIBLE // 거리 정보 다시 표시
                            arrowImageView.visibility = View.VISIBLE // 화살표 다시 표시
                            Toast.makeText(requireContext(), "탐색을 재개합니다.", Toast.LENGTH_SHORT).show()
                            Log.d("ExploreTrackingFragment", "속도 정상: 탐색 기능 재개.")
                        }
                    }
                }

                lastLocation = current
                lastLocationTime = current.elapsedRealtimeNanos

                // 🚀 MODIFIED: isExploringActive가 true일 때만 탐색 관련 UI 및 경로 업데이트
                if (isExploringActive) {
                    // 🚀 NEW: PolylineManager를 사용하여 현재 위치를 경로에 추가합니다.
                    polylineManager?.addPointToPath(LatLng(current.latitude, current.longitude))

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
            Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun onArriveAtPlace() {
        // 🚀 MODIFIED: 탐색이 비활성화 상태일 때는 도착 처리하지 않음
        if (!isExploringActive) {
            Log.d("ExploreTrackingFragment", "탐색 기능이 비활성화되어 도착 처리를 건너뜁니다.")
            return
        }

        val endTime = System.currentTimeMillis()
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

                targetImageUrl?.let { imageUrl ->
                    // 탐색 결과 다이얼로그를 띄울 때 운동 데이터를 함께 전달합니다.
                    // 이 데이터는 사진 비교 성공 시 Firebase에 저장될 것입니다.
                    ExploreResultDialogFragment
                        .newInstance("confirm", imageUrl, placeId ?: "", totalSteps, totalDistance, totalCalories)
                        .show(parentFragmentManager, "explore_confirm")
                }
            }
            .addOnFailureListener {
                Log.e("GoogleFit", "탐색 GoogleFit 데이터 로딩 실패", it)
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
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        // 🚀 NEW: 프래그먼트가 소멸될 때 PolylineManager를 초기화합니다.
        polylineManager?.clearPath()
    }

    fun onPhotoTaken(photoUri: android.net.Uri) {
        Log.d("Explore", "onPhotoTaken 호출됨! URI: $photoUri")

        targetImageUrl?.let { url ->
            Log.d("Explore", "imageUrl 전달됨: $url")

            ExploreResultDialogFragment
                .newInstance("fail", url, placeId ?: "")
                .show(parentFragmentManager, "explore_result")

            Log.d("Explore", "팝업 show() 호출 완료!")
        } ?: run {
            Log.e("Explore", "targetImageUrl 이 null이야!!")
        }
    }

    companion object {
        fun newInstance(placeId: String, lat: Double, lng: Double, imageUrl: String, placeName: String): ExploreTrackingFragment { // 🚀 MODIFIED: placeName 인자 추가
            return ExploreTrackingFragment().apply {
                arguments = Bundle().apply {
                    putString("placeId", placeId)
                    putDouble("targetLat", lat)
                    putDouble("targetLng", lng)
                    putString("targetImageUrl", imageUrl)
                    putString("targetPlaceName", placeName) // 🚀 NEW: placeName 전달
                }
            }
        }
    }
}
