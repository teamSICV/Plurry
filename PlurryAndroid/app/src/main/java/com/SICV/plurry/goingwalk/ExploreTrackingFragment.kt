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

    private var googleMap: com.google.android.gms.maps.GoogleMap? = null
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var lastVibrationLevel = Int.MAX_VALUE
    private var lastLoggedDistanceLevel = -1
    private var arrivalDialogShown = false
    private var targetImageUrl: String? = null
    private var placeId: String? = null

    private lateinit var fitnessOptions: FitnessOptions
    private var exploreStartTime: Long = 0L

    // Firebase Firestore 인스턴스 (더 이상 여기서 직접 저장하지 않으므로, 이 인스턴스는 필요 없을 수 있습니다.
    // 하지만 다른 용도로 사용될 가능성이 있어 일단 유지합니다.)
    private lateinit var db: FirebaseFirestore
    // Firebase Auth 인스턴스 (사용자별 데이터 저장 시 필요)
    private lateinit var auth: FirebaseAuth

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        arguments?.let {
            placeId = it.getString("placeId")
            targetLat = it.getDouble("targetLat")
            targetLng = it.getDouble("targetLng")
            targetImageUrl = it.getString("targetImageUrl")
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
            interval = 5000
            fastestInterval = 3000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val current = result.lastLocation ?: return
                val distance = calculateDistance(current.latitude, current.longitude)
                tvDistanceInfo.text = "남은 거리: %.1f m".format(distance)

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

                if (distance < 50 && !arrivalDialogShown) {
                    arrivalDialogShown = true
                    onArriveAtPlace()
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

                // Firebase에 저장하는 로직을 ExploreResultDialogFragment로 이동했으므로 여기서는 제거합니다.
                // saveExploreDataToFirebase(totalSteps, totalDistance, totalCalories, exploreStartTime, endTime)

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

    // saveExploreDataToFirebase 함수는 더 이상 ExploreTrackingFragment에서 사용되지 않으므로 제거합니다.
    /*
    private fun saveExploreDataToFirebase(
        steps: Int,
        distance: Double,
        calories: Double,
        startTime: Long,
        endTime: Long
    ) {
        val userId = auth.currentUser?.uid // 현재 로그인된 사용자의 UID 가져오기
        if (userId == null) {
            Log.e("Firebase", "사용자가 로그인되어 있지 않습니다. 탐색 데이터를 저장할 수 없습니다.")
            Toast.makeText(requireContext(), "사용자 로그인 정보가 없습니다. 데이터 저장 불가.", Toast.LENGTH_SHORT).show()
            return
        }

        if (placeId == null) {
            Log.e("Firebase", "Place ID가 null입니다. 탐색 데이터를 저장할 수 없습니다.")
            Toast.makeText(requireContext(), "대상 장소 정보가 없습니다. 데이터 저장 불가.", Toast.LENGTH_SHORT).show()
            return
        }

        val visitedPlaceData = hashMapOf(
            "calo" to calories,
            "geo" to mapOf("latitude" to targetLat, "longitude" to targetLng),
            "placeId" to placeId,
            "stepNum" to steps,
            "walkDistance" to distance,
            "walkEndTime" to endTime,
            "walkStartTime" to startTime,
            "userId" to userId
        )

        // 🚀 수정: 데이터 저장 경로를 Users/{userId}/visitedPlaces/{placeId}로 간소화합니다.
        db.collection("Users") // 'Users' 컬렉션
            .document(userId) // 사용자 UID 문서
            .collection("visitedPlaces") // 새로운 컬렉션: 'visitedPlaces'
            .document(placeId!!) // placeId를 문서 ID로 사용
            .set(visitedPlaceData) // 여기에 직접 탐색 데이터를 저장
            .addOnSuccessListener {
                Log.d("Firebase", "탐색 데이터 Firebase 저장 성공 (새 경로)!")
                Toast.makeText(requireContext(), "탐색 기록이 성공적으로 저장되었습니다!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "탐색 데이터 Firebase 저장 실패 (새 경로)", e)
                Toast.makeText(requireContext(), "탐색 기록 저장 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }
    */

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
        fun newInstance(placeId: String, lat: Double, lng: Double, imageUrl: String): ExploreTrackingFragment {
            return ExploreTrackingFragment().apply {
                arguments = Bundle().apply {
                    putString("placeId", placeId)
                    putDouble("targetLat", lat)
                    putDouble("targetLng", lng)
                    putString("targetImageUrl", imageUrl)
                }
            }
        }
    }
}
