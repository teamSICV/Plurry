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
import com.google.android.gms.location.*
import com.google.android.gms.maps.SupportMapFragment
import kotlin.math.*
import android.net.Uri
import android.util.Log
import com.bumptech.glide.Glide

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
    private var placeId: String? = null  // 🔥 placeId 변수 추가

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

        // 🔥 arguments 처리 부분 수정
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
            parentFragmentManager.popBackStack() // 탐색 종료 → 산책 모드로 복귀
        }

        // ✅ 뒤로가기 버튼 비활성화 처리
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 뒤로가기 무시
            }
        })

        mapFragment = parentFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
        }

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

                // 🔥 도착 시 다이얼로그 호출 부분 수정
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

    // 🔥 장소 도착 시 호출되는 메서드 추가
    private fun onArriveAtPlace() {
        targetImageUrl?.let { imageUrl ->
            ExploreResultDialogFragment
                .newInstance("confirm", imageUrl, placeId ?: "")
                .show(parentFragmentManager, "explore_confirm")
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
            vibrator.vibrate(500)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun onPhotoTaken(photoUri: Uri) {
        Log.d("Explore", "onPhotoTaken 호출됨! URI: $photoUri")

        targetImageUrl?.let { url ->
            Log.d("Explore", "imageUrl 전달됨: $url")

            // 🔥 placeId도 함께 전달
            ExploreResultDialogFragment
                .newInstance("fail", url, placeId ?: "")
                .show(parentFragmentManager, "explore_result")

            Log.d("Explore", "팝업 show() 호출 완료!")
        } ?: run {
            Log.e("Explore", "targetImageUrl 가 null이야!!")
        }
    }

    companion object {
        // 🔥 placeId 매개변수 추가
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