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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var mapFragment: SupportMapFragment

    private var googleMap: com.google.android.gms.maps.GoogleMap? = null
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var lastVibrationLevel = Int.MAX_VALUE
    private var arrivalDialogShown = false
    private var targetImageUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.activity_goingwalk_exploremain, container, false)

        tvDistanceInfo = view.findViewById(R.id.tvDistanceInfo)
        arrowImageView = view.findViewById(R.id.arrowImageView)
        imgTargetPreview = view.findViewById(R.id.imgTargetPreview)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        arguments?.let {
            targetLat = it.getDouble("targetLat")
            targetLng = it.getDouble("targetLng")
            targetImageUrl = it.getString("targetImageUrl")
        }

        // 목표 장소 이미지 로딩
        targetImageUrl?.let { url ->
            Glide.with(this)
                .load(url)
                .into(imgTargetPreview)
        }

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

                if (distance < 50 && !arrivalDialogShown) {
                    arrivalDialogShown = true
                    targetImageUrl?.let { url ->
                        ExploreResultDialogFragment
                            .newInstance("confirm", url)
                            .show(parentFragmentManager, "explore_confirm")
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

            ExploreResultDialogFragment
                .newInstance("fail", url)
                .show(parentFragmentManager, "explore_result")

            Log.d("Explore", "팝업 show() 호출 완료!")
        } ?: run {
            Log.e("Explore", "targetImageUrl 가 null이야!!")
        }
    }

    companion object {
        fun newInstance(lat: Double, lng: Double, imageUrl: String): ExploreTrackingFragment {
            return ExploreTrackingFragment().apply {
                arguments = Bundle().apply {
                    putDouble("targetLat", lat)
                    putDouble("targetLng", lng)
                    putString("targetImageUrl", imageUrl)
                }
            }
        }
    }
}
