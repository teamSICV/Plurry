// RouteAvoidanceManager.kt
package com.SICV.plurry.safety

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

class RouteAvoidanceManager {

    private var currentDetour: DetourInfo? = null
    private val detourHistory = mutableListOf<LatLng>()

    data class DetourInfo(
        val detourTarget: LatLng,           // 우회할 임시 목적지
        val originalTarget: LatLng,         // 원래 목적지
        val dangerArea: LatLng,            // 위험 구역 중심
        val dangerRadius: Double,          // 위험 구역 반지름
        val detourReason: String,          // 우회 이유
        val createdTime: Long = System.currentTimeMillis()
    )

    data class NavigationResult(
        val targetLocation: LatLng,        // 화살표가 가리킬 목적지
        val isDetourActive: Boolean,       // 우회 중인가?
        val detourMessage: String?,        // 우회 안내 메시지
        val distanceToTarget: Float,       // 현재 타겟까지 거리
        val progressPercentage: Int        // 전체 진행률
    )

    companion object {
        private const val DETOUR_MARGIN = 50.0        // 위험구역에서 50m 더 멀리 우회
        private const val DETOUR_ARRIVAL_RADIUS = 30.0 // 우회점 도달 판정 반지름
        private const val MAX_DETOUR_DISTANCE = 500.0   // 최대 우회 거리
        private const val DETOUR_TIMEOUT = 10 * 60 * 1000L // 우회 타임아웃 (10분)
    }

    /**
     * 현재 위치에서 목적지로 가는 경로에 위험구역이 있는지 체크하고 우회 경로 계산
     */
    fun calculateNavigation(
        currentLocation: LatLng,
        finalDestination: LatLng,
        dangerAreas: List<SafetyOverlayManager.DangerArea>
    ): NavigationResult {

        // 1. 기존 우회가 완료되었는지 체크
        currentDetour?.let { detour ->
            if (isDetourCompleted(currentLocation, detour)) {
                Log.i("RouteAvoidance", "우회 완료 - 원래 목적지로 복귀")
                currentDetour = null
                detourHistory.clear()
                return NavigationResult(
                    targetLocation = finalDestination,
                    isDetourActive = false,
                    detourMessage = "우회 완료! 목적지로 직진하세요",
                    distanceToTarget = calculateDistance(currentLocation, finalDestination),
                    progressPercentage = calculateProgress(currentLocation, finalDestination, finalDestination)
                )
            }

            // 우회가 타임아웃되었는지 체크
            if (System.currentTimeMillis() - detour.createdTime > DETOUR_TIMEOUT) {
                Log.w("RouteAvoidance", "우회 타임아웃 - 강제 복귀")
                currentDetour = null
                detourHistory.clear()
            }
        }

        // 2. 현재 우회 중이면 우회점으로 안내
        currentDetour?.let { detour ->
            return NavigationResult(
                targetLocation = detour.detourTarget,
                isDetourActive = true,
                detourMessage = "위험구역 우회 중... (${detour.detourReason})",
                distanceToTarget = calculateDistance(currentLocation, detour.detourTarget),
                progressPercentage = calculateDetourProgress(currentLocation, detour)
            )
        }

        // 3. 직선 경로에 위험구역이 있는지 체크
        val blockingArea = findBlockingDangerArea(currentLocation, finalDestination, dangerAreas)

        if (blockingArea != null) {
            // 4. 우회 경로 계산
            val detourPoint = calculateDetourPoint(currentLocation, finalDestination, blockingArea)

            if (detourPoint != null && isValidDetourPoint(currentLocation, detourPoint, blockingArea)) {
                currentDetour = DetourInfo(
                    detourTarget = detourPoint,
                    originalTarget = finalDestination,
                    dangerArea = blockingArea.center,
                    dangerRadius = blockingArea.radius,
                    detourReason = "위험구역 회피"
                )

                Log.i("RouteAvoidance", "새로운 우회 경로 생성: ${blockingArea.safetyDetail.level}")

                return NavigationResult(
                    targetLocation = detourPoint,
                    isDetourActive = true,
                    detourMessage = "⚠️ 위험구역 발견! 우회 경로로 안내합니다",
                    distanceToTarget = calculateDistance(currentLocation, detourPoint),
                    progressPercentage = calculateProgress(currentLocation, finalDestination, detourPoint)
                )
            }
        }

        // 5. 위험구역이 없으면 직진
        return NavigationResult(
            targetLocation = finalDestination,
            isDetourActive = false,
            detourMessage = null,
            distanceToTarget = calculateDistance(currentLocation, finalDestination),
            progressPercentage = calculateProgress(currentLocation, finalDestination, finalDestination)
        )
    }

    /**
     * 직선 경로를 차단하는 위험구역 찾기
     */
    private fun findBlockingDangerArea(
        start: LatLng,
        end: LatLng,
        dangerAreas: List<SafetyOverlayManager.DangerArea>
    ): SafetyOverlayManager.DangerArea? {

        return dangerAreas
            .filter { it.safetyDetail.level == com.SICV.plurry.safety.model.SafetyDetail.Level.DANGER }
            .find { area ->
                // 직선과 원의 교차 판정
                val distanceToLine = distanceFromPointToLine(area.center, start, end)
                val isOnPath = distanceToLine <= (area.radius + DETOUR_MARGIN)

                if (isOnPath) {
                    Log.d("RouteAvoidance", "위험구역 차단 발견: 거리=${distanceToLine.toInt()}m, 반지름=${area.radius.toInt()}m")
                }

                isOnPath
            }
    }

    /**
     * 우회점 계산 (위험구역을 돌아가는 지점)
     */
    private fun calculateDetourPoint(
        start: LatLng,
        end: LatLng,
        dangerArea: SafetyOverlayManager.DangerArea
    ): LatLng? {

        val dangerCenter = dangerArea.center
        val safeRadius = dangerArea.radius + DETOUR_MARGIN

        // 시작점에서 위험구역 중심으로의 벡터
        val toDangerX = dangerCenter.longitude - start.longitude
        val toDangerY = dangerCenter.latitude - start.latitude

        // 시작점에서 목적지로의 벡터
        val toEndX = end.longitude - start.longitude
        val toEndY = end.latitude - start.latitude

        // 외적을 이용해 위험구역이 경로의 왼쪽인지 오른쪽인지 판단
        val crossProduct = toDangerX * toEndY - toDangerY * toEndX
        val turnDirection = if (crossProduct > 0) 1 else -1 // 1: 좌회전, -1: 우회전

        // 위험구역 중심에서 수직인 방향으로 우회점 계산
        val angle = atan2(toEndY, toEndX) + (turnDirection * PI / 2)

        val detourX = dangerCenter.longitude + cos(angle) * safeRadius / 111320.0 // 경도 보정
        val detourY = dangerCenter.latitude + sin(angle) * safeRadius / 110540.0  // 위도 보정

        return LatLng(detourY, detourX)
    }

    /**
     * 점과 직선 사이의 최단거리 계산
     */
    private fun distanceFromPointToLine(point: LatLng, lineStart: LatLng, lineEnd: LatLng): Double {
        val A = point.latitude - lineStart.latitude
        val B = point.longitude - lineStart.longitude
        val C = lineEnd.latitude - lineStart.latitude
        val D = lineEnd.longitude - lineStart.longitude

        val dot = A * C + B * D
        val lenSq = C * C + D * D

        if (lenSq == 0.0) return calculateDistance(point, lineStart).toDouble()

        val param = dot / lenSq
        val closestPoint = when {
            param < 0 -> lineStart
            param > 1 -> lineEnd
            else -> LatLng(
                lineStart.latitude + param * C,
                lineStart.longitude + param * D
            )
        }

        return calculateDistance(point, closestPoint).toDouble()
    }

    /**
     * 우회점이 유효한지 검증
     */
    private fun isValidDetourPoint(
        currentLocation: LatLng,
        detourPoint: LatLng,
        dangerArea: SafetyOverlayManager.DangerArea
    ): Boolean {
        val distanceToDetour = calculateDistance(currentLocation, detourPoint)
        val distanceToDanger = calculateDistance(detourPoint, dangerArea.center)

        return distanceToDetour <= MAX_DETOUR_DISTANCE &&
                distanceToDanger > (dangerArea.radius + DETOUR_MARGIN / 2)
    }

    /**
     * 우회가 완료되었는지 체크
     */
    private fun isDetourCompleted(currentLocation: LatLng, detour: DetourInfo): Boolean {
        val distanceToDetourPoint = calculateDistance(currentLocation, detour.detourTarget)
        return distanceToDetourPoint <= DETOUR_ARRIVAL_RADIUS
    }

    /**
     * 전체 진행률 계산
     */
    private fun calculateProgress(
        currentLocation: LatLng,
        finalDestination: LatLng,
        immediateTarget: LatLng
    ): Int {
        val totalDistance = calculateDistance(currentLocation, finalDestination)
        val remainingDistance = calculateDistance(currentLocation, immediateTarget)

        return if (totalDistance > 0) {
            maxOf(0, minOf(100, (100 - (remainingDistance / totalDistance * 100)).toInt()))
        } else 100
    }

    /**
     * 우회 진행률 계산
     */
    private fun calculateDetourProgress(currentLocation: LatLng, detour: DetourInfo): Int {
        val detourDistance = calculateDistance(detour.originalTarget, detour.detourTarget)
        val currentToDetour = calculateDistance(currentLocation, detour.detourTarget)

        return if (detourDistance > 0) {
            maxOf(0, minOf(100, ((detourDistance - currentToDetour) / detourDistance * 100).toInt()))
        } else 50
    }

    /**
     * 두 좌표 간 거리 계산 (미터)
     */
    private fun calculateDistance(from: LatLng, to: LatLng): Float {
        val fromLoc = Location("from").apply {
            latitude = from.latitude
            longitude = from.longitude
        }
        val toLoc = Location("to").apply {
            latitude = to.latitude
            longitude = to.longitude
        }
        return fromLoc.distanceTo(toLoc)
    }

    /**
     * 현재 우회 상태 반환
     */
    fun getCurrentDetour(): DetourInfo? = currentDetour

    /**
     * 강제로 우회 취소
     */
    fun cancelDetour() {
        currentDetour = null
        detourHistory.clear()
        Log.i("RouteAvoidance", "우회 강제 취소")
    }

    /**
     * 우회 기록 반환 (디버깅용)
     */
    fun getDetourHistory(): List<LatLng> = detourHistory.toList()
}