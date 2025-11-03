package com.SICV.plurry.safety

import com.SICV.plurry.safety.model.SafetyDetail

/**
 * 수동 위험지역 설정
 */
object CustomDanger {

    data class Zone(
        val name: String,
        val lat: Double,
        val lng: Double,
        val radius: Float = 40f
    )

    // 여기 수정하면 좌표 추가/변경 가능
    val zones = mutableListOf(
        Zone("하나누리관 위험지역", 37.650162, 127.019515, 10f)
    )

    fun applyToOverlay(manager: SafetyOverlayManager?) {
        manager?.setMinDistanceBetweenAreas(50.0)
        zones.forEach { z ->
            val detail = SafetyDetail(
                score = 0,
                level = SafetyDetail.Level.DANGER,
                convCount = 0, publicCount = 0, subwayCount = 0, tourismCount = 0,
                cctvCount = 0, streetLightCount = 0,
                reasons = listOf("수동 위험지역 - ${z.name}")
            )
            manager?.addManualDanger(
                z.lat, z.lng, detail,
                z.radius.toDouble(),
                detourAllowed = false   // 수동 존: 우회 경로 제외
            )
        }
        manager?.setMinDistanceBetweenAreas(150.0)
    }

    fun addZone(name: String, lat: Double, lng: Double, radius: Float = 50f) {
        zones.add(Zone(name, lat, lng, radius))
    }
    fun clearZones() { zones.clear() }
}

