package com.SICV.plurry.ranking

data class RankingRecord(
    val rank: Int,
    val userId: String? = null,
    val profileImageUrl: String?,
    val nickname: String,
    val record: String
)
