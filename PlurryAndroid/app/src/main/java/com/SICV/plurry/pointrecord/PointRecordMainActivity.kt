package com.SICV.plurry.pointrecord

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.SICV.plurry.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlin.math.*
import android.media.MediaPlayer // MediaPlayer 임포트

class PointRecordMainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private lateinit var showBtn: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var visitedPlacesLoader: VisitedPlacesLoader
    private var currentUserLocation: LatLng? = null

    // 배경 음악을 위한 미디어 플레이어
    private var mediaPlayer: MediaPlayer? = null // MediaPlayer 인스턴스 선언

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_point_record_main)

        // --- 음악 시작: 배경 음악 재생을 초기화하고 연속 재생 시작 ---
        try {
            // 참고: 'R.raw.background_music'을 'res/raw' 폴더에 넣은 실제 파일 이름으로 바꿔주세요.
            mediaPlayer = MediaPlayer.create(this, R.raw.signrecord)
            mediaPlayer?.isLooping = true // 음악을 계속 반복하도록 설정
            mediaPlayer?.setVolume(1.0f, 1.0f)

            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("PointRecord", "미디어 플레이어 초기화 또는 시작 오류: ${e.message}")
        }
        // --- 음악 끝 ---

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.myPlaceMap) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        showBtn = findViewById<Button>(R.id.showCrewBottomBtn)
        showBtn.visibility = View.GONE

        showBtn.setOnClickListener {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val uid = currentUser.uid
                Log.d("PointRecord", "버튼 클릭 - 사용자 UID: $uid")

                db.collection("Users").document(uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val crewAt = document.getString("crewAt")
                            Log.d("PointRecord", "사용자의 crewAt: $crewAt")

                            if (crewAt != null) {
                                val fragment = CrewPointBottomFragment.newInstance(crewAt)
                                fragment.show(supportFragmentManager, fragment.tag)
                            } else {
                                Toast.makeText(this, "크루 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.d("PointRecord", "사용자 문서가 존재하지 않음")
                            Toast.makeText(this, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("PointRecord", "크루 정보 로딩 실패", exception)
                        Toast.makeText(this, "크루 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Log.d("PointRecord", "사용자가 로그인하지 않음")
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

        checkCrewMembership()
    }

    // --- Activity 라이프사이클 메서드에 음악 재생/정지 로직 추가 ---
    override fun onResume() {
        super.onResume()
        // 화면으로 돌아왔을 때 음악을 다시 재생합니다.
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        // 화면을 벗어나 다른 페이지로 이동할 때 음악을 일시 정지합니다.
        mediaPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 메모리 누수 방지를 위해 MediaPlayer 리소스 해제
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
    // --- 음악 로직 끝 ---

    private fun checkCrewMembership() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("PointRecord", "사용자가 로그인하지 않음")
            hideCrewFeatures()
            return
        }

        val uid = currentUser.uid
        Log.d("PointRecord", "크루 멤버십 확인 - UID: $uid")

        db.collection("Users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val crewAt = document.get("crewAt")
                    Log.d("PointRecord", "크루 멤버십 확인 결과 - crewAt: $crewAt")

                    if (crewAt != null) {
                        showCrewFeatures()
                    } else {
                        hideCrewFeatures()
                    }
                } else {
                    Log.d("PointRecord", "사용자 문서가 존재하지 않음")
                    hideCrewFeatures()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PointRecord", "크루 멤버십 확인 실패", exception)
                hideCrewFeatures()
                Toast.makeText(this, "크루 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCrewFeatures() {
        Log.d("PointRecord", "크루 기능 표시")
        showBtn.visibility = View.VISIBLE

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            db.collection("Users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val crewAt = document.getString("crewAt")
                        if (crewAt != null) {
                            Log.d("PointRecord", "자동으로 크루 장소포인트 표시 - crewAt: $crewAt")
                            val fragment = CrewPointBottomFragment.newInstance(crewAt)
                            fragment.show(supportFragmentManager, fragment.tag)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("PointRecord", "자동 크루 장소포인트 표시 실패", exception)
                }
        }
    }

    private fun hideCrewFeatures() {
        Log.d("PointRecord", "크루 기능 숨김")
        showBtn.visibility = View.GONE
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        visitedPlacesLoader = VisitedPlacesLoader(googleMap, auth, db, currentUserLocation)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            getLastKnownLocation()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        googleMap.setOnMarkerClickListener { marker ->
            val tag = marker.tag as? PlaceData
            if (tag != null) {
                showPointRecordDialog(tag.imageUrl, tag.name, tag.description, tag.placeId, tag.lat, tag.lng, tag.isVisited)
            }
            true
        }
    }

    private fun getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener(this, OnSuccessListener<Location> { location ->
                    if (location != null) {
                        val userLocation = LatLng(location.latitude, location.longitude)
                        currentUserLocation = userLocation
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f))

                        visitedPlacesLoader = VisitedPlacesLoader(googleMap, auth, db, currentUserLocation)

                        loadUserPlaces()
                        loadVisitedPlaces()
                    }
                })
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    private fun loadUserPlaces() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("PointRecord", "사용자가 로그인하지 않음")
            return
        }

        val uid = currentUser.uid
        Log.d("PointRecord", "사용자 장소 로드 시작 - UID: $uid")

        db.collection("Places")
            .whereEqualTo("addedBy", uid)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("PointRecord", "사용자 장소 ${documents.size()}개 로드 완료")

                for (document in documents) {
                    val name = document.getString("name") ?: "이름 없음"
                    val myImgUrl = document.getString("myImgUrl") ?: ""
                    val geoPoint = document.getGeoPoint("geo")
                    val placeId = document.id

                    if (geoPoint != null) {
                        val latitude = geoPoint.latitude
                        val longitude = geoPoint.longitude
                        val position = LatLng(latitude, longitude)

                        val distance = calculateDistance(currentUserLocation, position)

                        val description = buildString {
                            append("추가한 유저: $uid\n")
                            append("거리: ${String.format("%.2f", distance)}km")
                        }

                        val marker = googleMap.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title(name)
                                .snippet(description)
                        )

                        marker?.tag = PlaceData(myImgUrl, name, description, placeId, latitude, longitude)

                        Log.d("PointRecord", "마커 추가: $name at ($latitude, $longitude), 거리: ${distance}km")
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PointRecord", "사용자 장소 로드 실패", exception)
                Toast.makeText(this, "장소 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadVisitedPlaces() {
        visitedPlacesLoader.loadVisitedPlaces()
    }

    private fun calculateDistance(from: LatLng?, to: LatLng): Double {
        if (from == null) return 0.0

        val earthRadius = 6371.0

        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) *
                sin(dLng / 2) * sin(dLng / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private fun showPointRecordDialog(imageUrl: String, name: String, description: String, placeId: String, lat: Double, lng: Double, isVisited: Boolean = false) {
        val dialog = PointRecordDialog.newInstance(imageUrl, name, description, placeId, lat, lng, "", isVisited)
        dialog.show(supportFragmentManager, "PointRecordDialog")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastKnownLocation()
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class PlaceData(
        val imageUrl: String,
        val name: String,
        val description: String,
        val placeId: String = "",
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val isVisited: Boolean = false
    )
}