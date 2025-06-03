package com.SICV.plurry.goingwalk

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.SICV.plurry.R
import com.google.android.gms.location.*

class ExploreTrackingFragment : Fragment() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var tvDistanceInfo: TextView

    private var targetLat = 0.0
    private var targetLng = 0.0
    private var lastNotifiedLevel = -1  // 0:100m, 1:50m, 2:10m

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.activity_goingwalk_exploremain, container, false)

        tvDistanceInfo = view.findViewById(R.id.tvDistanceInfo)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        arguments?.let {
            targetLat = it.getDouble("targetLat")
            targetLng = it.getDouble("targetLng")
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

                tvDistanceInfo.text = "목표까지 남은 거리: %.1f m".format(distance)

                when {
                    distance <= 10 && lastNotifiedLevel < 2 -> {
                        Toast.makeText(requireContext(), "🎯 목적지에 도착했습니다!", Toast.LENGTH_SHORT).show()
                        lastNotifiedLevel = 2
                    }
                    distance <= 50 && lastNotifiedLevel < 1 -> {
                        Toast.makeText(requireContext(), "📍 50m 이내에 접근했습니다", Toast.LENGTH_SHORT).show()
                        lastNotifiedLevel = 1
                    }
                    distance <= 100 && lastNotifiedLevel < 0 -> {
                        Toast.makeText(requireContext(), "📍 100m 이내에 접근했습니다", Toast.LENGTH_SHORT).show()
                        lastNotifiedLevel = 0
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

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    companion object {
        fun newInstance(lat: Double, lng: Double): ExploreTrackingFragment {
            return ExploreTrackingFragment().apply {
                arguments = Bundle().apply {
                    putDouble("targetLat", lat)
                    putDouble("targetLng", lng)
                }
            }
        }
    }
}

