
package com.SICV.plurry.safety.model
data class KakaoCategoryResponse(
    val documents: List<Document>?
) {
    data class Document(
        val id: String? = null,
        val place_name: String? = null,
        val category_name: String? = null,
        val address_name: String? = null,
        val road_address_name: String? = null,
        val phone: String? = null,
        val place_url: String? = null,
        val distance: String? = null,
        val x: String? = null, // longitude
        val y: String? = null  // latitude
    )

}