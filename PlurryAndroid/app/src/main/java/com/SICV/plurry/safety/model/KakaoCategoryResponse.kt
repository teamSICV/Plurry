package com.SICV.plurry.safety.model

import com.google.gson.annotations.SerializedName

data class KakaoCategoryResponse(
    @SerializedName("meta")
    val meta: Meta?,
    @SerializedName("documents")
    val documents: List<Document>?
)

data class Meta(
    @SerializedName("total_count")
    val totalCount: Int?,
    @SerializedName("pageable_count")
    val pageableCount: Int?,
    @SerializedName("is_end")
    val isEnd: Boolean?
)

data class Document(
    @SerializedName("id")
    val id: String?,
    @SerializedName("place_name")
    val placeName: String?,
    @SerializedName("category_name")
    val categoryName: String?,
    @SerializedName("category_group_code")
    val categoryGroupCode: String?,
    @SerializedName("category_group_name")
    val categoryGroupName: String?,
    @SerializedName("phone")
    val phone: String?,
    @SerializedName("address_name")
    val addressName: String?,
    @SerializedName("road_address_name")
    val roadAddressName: String?,
    @SerializedName("x")
    val x: String?,   // 위도/경도 문자열로 옴
    @SerializedName("y")
    val y: String?,
    @SerializedName("place_url")
    val placeUrl: String?,
    @SerializedName("distance")
    val distance: String?  // 중심 좌표와의 거리(m)
)
data class PoiCount(val total: Int, val withinRadius: Int)

fun KakaoCategoryResponse.toPoiCount(): PoiCount {
    val total = this.meta?.totalCount ?: this.documents?.size ?: 0
    val within = this.documents?.size ?: 0
    return PoiCount(total = total, withinRadius = within)
}


