package com.SICV.plurry.goingwalk

import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

/**
 * PolylineManager는 GoogleMap에 사용자의 이동 경로를 실시간으로 그리는 역할
 *
 * @param googleMap 경로를 그릴 GoogleMap 인스턴스
 */
class PolylineManager(private val googleMap: GoogleMap) {

    // 사용자의 이동 경로를 저장할 LatLng 리스트
    private val pathPoints = mutableListOf<LatLng>()
    // 현재 지도에 그려져 있는 Polyline 객체
    private var polyline: Polyline? = null

    /**
     * 경로에 새로운 위치 지점을 추가하고, 지도에 경로를 다시 그림
     *
     * @param newPoint 새로 추가할 위치 (LatLng)
     */
    fun addPointToPath(newPoint: LatLng) {
        // 새로운 지점을 리스트에 추가합니다.
        pathPoints.add(newPoint)
        // 지도를 업데이트하여 경로를 다시 그립니다.
        redrawPolyline()
    }

    /**
     * 지도에 그려진 경로와 내부 위치 데이터를 모두 초기화
     * 주로 탐색이 끝났을 때 호출
     */
    fun clearPath() {
        // 위치 리스트를 비웁니다.
        pathPoints.clear()
        // 지도에 있는 Polyline을 제거합니다.
        polyline?.remove()
        // polyline 참조를 null로 설정합니다.
        polyline = null
    }

    /**
     * 현재 저장된 pathPoints 리스트를 기반으로 지도에 Polyline을 다시 그림
     * 이전에 그려진 Polyline이 있다면 제거하고 새로 그림
     */
    private fun redrawPolyline() {
        // 기존 Polyline이 있다면 제거합니다.
        polyline?.remove()

        // Polyline을 그리기 위한 옵션을 설정
        val polylineOptions = PolylineOptions()
            .addAll(pathPoints)
            .color(Color.BLUE) // 파란색
            .width(15f) // 두께 15

        // 설정된 옵션으로 지도에 Polyline을 추가하고, 생성된 객체를 저장
        polyline = googleMap.addPolyline(polylineOptions)
    }
}
