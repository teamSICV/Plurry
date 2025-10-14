// 안전도 계산 모뎅
package com.SICV.plurry.safety.model

data class SafetyDetail(
    val score: Int,
    val level: Level,
    val convCount: Int,
    val publicCount: Int,
    val subwayCount: Int,
    val tourismCount: Int,
    val cctvCount: Int,
    val streetLightCount: Int,   // ← count로 통일
    val reasons: List<String> = emptyList()
) {
    enum class Level { SAFE, CAUTION, DANGER }
}

