package com.SICV.plurry.safety.model

import com.google.gson.annotations.SerializedName

data class KakaoCategoryResponse(
    val documents: List<KakaoPlace>,
    val meta: KakaoMeta
)

data class KakaoPlace(
    @SerializedName("place_name") val placeName: String,
    @SerializedName("category_group_code") val categoryGroupCode: String,
    @SerializedName("x") val x: String,  // 경도
    @SerializedName("y") val y: String,  // 위도
    @SerializedName("distance") val distance: String? // m 단위, 반경 검색 시만 옴
)

data class KakaoMeta(
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("pageable_count") val pageableCount: Int,
    @SerializedName("is_end") val isEnd: Boolean
)


