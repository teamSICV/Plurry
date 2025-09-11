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
        tvSpeedWarning = view.findViewById(R.id.tvSpeedWarning)

        // 안전도 배너 (nullable 처리)
        safetyBanner = view.findViewById(R.id.safetyBanner)
        safetyBannerText = view.findViewById(R.id.safetyBannerText)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        arguments?.let {
            placeId = it.getString("placeId")
            targetLat = it.getDouble("targetLat")
            targetLng = it.getDouble("targetLng")
            targetImageUrl = it.getString("targetImageUrl")
            targetPlaceName = it.getString("targetPlaceName")
        }

        targetImageUrl?.let { url ->
            Glide.with(this).load(url).into(imgTargetPreview)
        }

        btnExitExplore.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        // 맵 프래그먼트 (없어도 앱 안 죽게 처리)
        val mapFrag = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFrag?.getMapAsync { map ->
            googleMap = map
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

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // 안전도 ViewModel 생성
        val safetyRepo = SafetyRepo(
            kakao = RetrofitModule.kakaoApi,
            safetyService = RetrofitModule.safetyService,
            kakaoApiKey = "KakaoAK a670ee4833315dafcd56da98e48b2a26" // ← 실제 키로 교체
        )
        safetyViewModel = ViewModelProvider(this, SafetyVMFactory(safetyRepo))[SafetyViewModel::class.java]

        safetyViewModel.safety.observe(viewLifecycleOwner) { detail ->
            if (detail == null) return@observe
            val levelName = detail.level?.name ?: "UNKNOWN"
            latestSafetyLine = " · 안전도 ${detail.score} ($levelName)"

            if (levelName == "DANGER") {
                safetyBannerText?.text = "안전도 ${detail.score} (낮음). 밝은 길로 우회하세요."
                safetyBanner?.visibility = View.VISIBLE

                val fm = parentFragmentManager
                if (isAdded && !isDetached && fm.findFragmentByTag("safety_detour") == null) {
                    BottomSheet().show(fm, "safety_detour")
                }
            } else {
                safetyBanner?.visibility = View.GONE
            }
        }

        startLocationTracking()
        return view
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

                // 속도 감지
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
                            Toast.makeText(requireContext(), "이동수단에서 내린 후 진행해주세요.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        if (!isExploringActive) {
                            isExploringActive = true
                            tvSpeedWarning.visibility = View.GONE
                            tvDistanceInfo.visibility = View.VISIBLE
                            arrowImageView.visibility = View.VISIBLE
                            Toast.makeText(requireContext(), "탐색을 재개합니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                lastLocation = current
                lastLocationTime = current.elapsedRealtimeNanos

                if (isExploringActive) {
                    // 안전도 평가 (200m 이동 or 30초마다)
                    val now = System.currentTimeMillis()
                    val needEval = when {
                        lastSafetyEvalLoc == null -> true
                        current.distanceTo(lastSafetyEvalLoc!!) >= 200f -> true
                        (now - lastSafetyEvalTime) >= 30_000L -> true
                        else -> false
                    }
                    if (needEval) {
                        safetyViewModel.evaluate(current.latitude, current.longitude)
                        lastSafetyEvalLoc = current
                        lastSafetyEvalTime = now
                    }

                    polylineManager?.addPointToPath(LatLng(current.latitude, current.longitude))

                    val distance = calculateDistance(current.latitude, current.longitude)
                    tvDistanceInfo.text =
                        "${targetPlaceName ?: "목표 장소"} 남은 거리: %.1f m".format(distance) + latestSafetyLine

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

                    val currentLevel50m = (distance / 50).toInt()
                    if (currentLevel50m != lastLoggedDistanceLevel) {
                        lastLoggedDistanceLevel = currentLevel50m
                    }

                    if (distance < 50 && !arrivalDialogShown) {
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
        if (!isExploringActive) return

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

                targetImageUrl?.let { imageUrl ->
                    ExploreResultDialogFragment
                        .newInstance("confirm", imageUrl, placeId ?: "", totalSteps, totalDistance, totalCalories)
                        .show(parentFragmentManager, "explore_confirm")
                }
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
        polylineManager?.clearPath()
    }

    fun onPhotoTaken(photoUri: android.net.Uri) {
        targetImageUrl?.let { url ->
            ExploreResultDialogFragment
                .newInstance("fail", url, placeId ?: "")
                .show(parentFragmentManager, "explore_result")
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
