// SafetyOverlayManager.kt
package com.SICV.plurry.safety

import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.SICV.plurry.safety.model.SafetyDetail
import android.util.Log

class SafetyOverlayManager(private val googleMap: GoogleMap) {

    private val dangerAreas = mutableListOf<DangerArea>()
    private val overlays = mutableListOf<Circle>()

    data class DangerArea(
        val id: String,
        val center: LatLng,
        val radius: Double = 100.0, // 기본 100m 반경
        val safetyDetail: SafetyDetail,
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        private const val DANGER_RADIUS = 100.0 // 위험 영역 반경 (미터)
        private const val OVERLAY_DURATION = 30 * 60 * 1000L // 30분간 유지
        private const val MIN_DISTANCE_BETWEEN_AREAS = 150.0 // 영역 간 최소 거리
    }

    /**
     * 안전도 평가 결과를 바탕으로 위험 지역 오버레이 추가
     */
    fun addSafetyEvaluation(lat: Double, lng: Double, safetyDetail: SafetyDetail) {
        val location = LatLng(lat, lng)
        val areaId = "${lat}_${lng}_${System.currentTimeMillis()}"

        when (safetyDetail.level) {
            SafetyDetail.Level.DANGER -> {
                // 기존 근처 위험 지역이 있는지 확인
                val nearbyArea = findNearbyDangerArea(location)
                if (nearbyArea == null) {
                    addDangerOverlay(areaId, location, safetyDetail)
                    Log.d("SafetyOverlay", "새로운 위험 지역 추가: $areaId")
                } else {
                    Log.d("SafetyOverlay", "근처에 이미 위험 지역 존재: ${nearbyArea.id}")
                }
            }
            SafetyDetail.Level.CAUTION -> {
                // 주의 지역은 노란색으로 표시 (선택사항)
                addCautionOverlay(areaId, location, safetyDetail)
            }
            SafetyDetail.Level.SAFE -> {
                // 안전 지역은 표시하지 않음 또는 초록색으로 표시
                Log.d("SafetyOverlay", "안전 지역: $areaId")
            }
        }

        // 오래된 오버레이 정리
        cleanupExpiredOverlays()
    }

    /**
     * 위험 지역 오버레이 추가
     */
    private fun addDangerOverlay(id: String, center: LatLng, safetyDetail: SafetyDetail) {
        val circle = googleMap.addCircle(
            CircleOptions()
                .center(center)
                .radius(DANGER_RADIUS)
                .fillColor(Color.argb(100, 255, 0, 0)) // 반투명 빨간색
                .strokeColor(Color.RED)
                .strokeWidth(3f)
                .clickable(true)
        )

        // 클릭 리스너 추가
        googleMap.setOnCircleClickListener { clickedCircle ->
            if (clickedCircle == circle) {
                showDangerAreaInfo(safetyDetail, center)
            }
        }

        val dangerArea = DangerArea(id, center, DANGER_RADIUS, safetyDetail)
        dangerAreas.add(dangerArea)
        overlays.add(circle)

        Log.i("SafetyOverlay", "위험 지역 오버레이 추가 완료 - 점수: ${safetyDetail.score}")
    }

    /**
     * 주의 지역 오버레이 추가 (옵션)
     */
    private fun addCautionOverlay(id: String, center: LatLng, safetyDetail: SafetyDetail) {
        val circle = googleMap.addCircle(
            CircleOptions()
                .center(center)
                .radius(DANGER_RADIUS * 0.8) // 위험보다 조금 작게
                .fillColor(Color.argb(80, 255, 165, 0)) // 반투명 주황색
                .strokeColor(Color.parseColor("#FFA500"))
                .strokeWidth(2f)
                .clickable(true)
        )

        overlays.add(circle)
        Log.i("SafetyOverlay", "주의 지역 오버레이 추가 완료")
    }

    /**
     * 근처 위험 지역 찾기
     */
    private fun findNearbyDangerArea(location: LatLng): DangerArea? {
        return dangerAreas.find { area ->
            val distance = calculateDistance(location, area.center)
            distance < MIN_DISTANCE_BETWEEN_AREAS
        }
    }

    /**
     * 두 좌표 간 거리 계산 (미터)
     */
    private fun calculateDistance(loc1: LatLng, loc2: LatLng): Double {
        val earthRadius = 6371000.0 // 지구 반지름 (미터)
        val lat1Rad = Math.toRadians(loc1.latitude)
        val lat2Rad = Math.toRadians(loc2.latitude)
        val deltaLat = Math.toRadians(loc2.latitude - loc1.latitude)
        val deltaLng = Math.toRadians(loc2.longitude - loc1.longitude)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * 위험 지역 정보 표시
     */
    private fun showDangerAreaInfo(safetyDetail: SafetyDetail, center: LatLng) {
        val infoWindow = googleMap.addMarker(
            MarkerOptions()
                .position(center)
                .title("⚠️ 위험 지역")
                .snippet("안전도: ${safetyDetail.score}\n${safetyDetail.reasons.joinToString(", ")}")
        )
        infoWindow?.showInfoWindow()

        // 3초 후 마커 제거
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            infoWindow?.remove()
        }, 3000)
    }

    /**
     * 오래된 오버레이 정리
     */
    private fun cleanupExpiredOverlays() {
        val currentTime = System.currentTimeMillis()
        val expiredAreas = dangerAreas.filter {
            currentTime - it.timestamp > OVERLAY_DURATION
        }

        expiredAreas.forEach { area ->
            val index = dangerAreas.indexOf(area)
            if (index >= 0 && index < overlays.size) {
                overlays[index].remove() // 지도에서 제거
                overlays.removeAt(index)
                dangerAreas.removeAt(index)
                Log.d("SafetyOverlay", "만료된 위험 지역 제거: ${area.id}")
            }
        }
    }

    /**
     * 특정 좌표가 위험 지역에 포함되는지 확인
     */
    fun isLocationInDangerArea(lat: Double, lng: Double): Boolean {
        val location = LatLng(lat, lng)
        return dangerAreas.any { area ->
            calculateDistance(location, area.center) <= area.radius
        }
    }

    /**
     * 모든 오버레이 제거
     */
    fun clearAllOverlays() {
        overlays.forEach { it.remove() }
        overlays.clear()
        dangerAreas.clear()
        Log.i("SafetyOverlay", "모든 안전도 오버레이 제거 완료")
    }

    /**
     * 현재 위험 지역 개수 반환
     */
    fun getDangerAreaCount(): Int = dangerAreas.size

    /**
     * 위험 지역 목록 반환
     */
    fun getDangerAreas(): List<DangerArea> = dangerAreas.toList()
}