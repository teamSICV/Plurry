package com.SICV.plurry.crewstep

import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CrewWalkManager(
    private val recyclerView: RecyclerView,
    private val context: android.content.Context
) {
    private var crewWalkData = mutableListOf<WalkRecord>()
    private lateinit var walkRecordAdapter: WalkRecordAdapter
    private val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
    private val koreaLocale = Locale.KOREA

    interface WalkDataUpdateListener {
        fun onWalkDataUpdated(walkRecords: List<WalkRecord>)
    }

    private var dataUpdateListener: WalkDataUpdateListener? = null

    fun setDataUpdateListener(listener: WalkDataUpdateListener) {
        dataUpdateListener = listener
    }

    init {
        recyclerView.layoutManager = LinearLayoutManager(context)

        val loadingData = listOf(
            WalkRecord("로딩 중...", getKoreaTimeString(), "0.0km", "0분", "0kcal")
        )
        walkRecordAdapter = WalkRecordAdapter(loadingData)
        recyclerView.adapter = walkRecordAdapter
    }

    fun getCrewWalkData(): List<WalkRecord> {
        return crewWalkData.toList()
    }

    fun loadCrewWalkRecords(crewId: String, db: FirebaseFirestore) {
        if (crewId.isEmpty()) {
            updateRecyclerView(emptyList())
            return
        }

        db.collection("Crew").document(crewId).collection("member").document("members").get()
            .addOnSuccessListener { memberDoc ->
                if (memberDoc.exists()) {
                    val memberData = memberDoc.data
                    if (memberData != null && memberData.isNotEmpty()) {
                        val memberUids = memberData.keys.toList()
                        fetchWalkRecordsForMembers(memberUids, db)
                    } else {
                        updateRecyclerView(emptyList())
                    }
                } else {
                    updateRecyclerView(emptyList())
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewWalkManager", "멤버 정보 로드 실패", e)
                updateRecyclerView(emptyList())
            }
    }

    private fun fetchWalkRecordsForMembers(memberUids: List<String>, db: FirebaseFirestore) {
        val walkRecords = mutableListOf<WalkRecord>()
        var completedRequests = 0

        if (memberUids.isEmpty()) {
            updateRecyclerView(emptyList())
            return
        }

        for (uid in memberUids) {
            db.collection("Users").document(uid).get()
                .addOnSuccessListener { userDoc ->
                    val userName = if (userDoc.exists()) {
                        val name = userDoc.getString("name") ?: uid
                        name
                    } else {
                        uid
                    }
                    val crewAtTime = if (userDoc.exists()) {
                        userDoc.getTimestamp("crewAtTime")?.toDate()?.time
                    } else {
                        null
                    }

                    // limit 추가
                    db.collection("Users").document(uid).collection("goWalk")
                        .orderBy("endTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(50)
                        .get()
                        .addOnSuccessListener { walkDocs ->
                            for (walkDoc in walkDocs.documents) {
                                try {
                                    val calories = walkDoc.getLong("calories") ?: 0L
                                    val distance = walkDoc.getDouble("distance") ?: 0.0
                                    val startTime = try {
                                        walkDoc.getTimestamp("startTime")?.toDate()?.time ?: 0L
                                    } catch (e: Exception) {
                                        0L
                                    }

                                    val endTime = try {
                                        walkDoc.getTimestamp("endTime")?.toDate()?.time ?: 0L
                                    } catch (e: Exception) {
                                        0L
                                    }

                                    if (endTime > 0 && distance > 0) {
                                        val shouldInclude = if (crewAtTime != null) {
                                            endTime >= crewAtTime
                                        } else {
                                            true
                                        }

                                        if (shouldInclude) {
                                            val distanceFormatted = String.format("%.1fkm", distance)
                                            val walkDuration = if (endTime > startTime && startTime > 0) {
                                                val durationMinutes = (endTime - startTime) / (1000 * 60)
                                                "${durationMinutes}분"
                                            } else {
                                                "0분"
                                            }

                                            val endTimeFormatted = SimpleDateFormat("yy-MM-dd HH:mm", koreaLocale).apply {
                                                timeZone = koreaTimeZone
                                            }.format(Date(endTime))

                                            val walkRecord = WalkRecord(
                                                userName,
                                                endTimeFormatted,
                                                distanceFormatted,
                                                walkDuration,
                                                "${calories}kcal"
                                            )

                                            walkRecords.add(walkRecord)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("CrewWalkManager", "문서 파싱 오류: ${walkDoc.id}", e)
                                }
                            }

                            completedRequests++
                            if (completedRequests == memberUids.size) {
                                updateRecyclerView(walkRecords)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("CrewWalkManager", "산책 기록 로드 실패: $uid", e)
                            completedRequests++
                            if (completedRequests == memberUids.size) {
                                updateRecyclerView(walkRecords)
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("CrewWalkManager", "사용자 정보 로드 실패: $uid", e)
                    completedRequests++
                    if (completedRequests == memberUids.size) {
                        updateRecyclerView(walkRecords)
                    }
                }
        }
    }

    private fun updateRecyclerView(walkRecords: List<WalkRecord>) {
        crewWalkData.clear()
        crewWalkData.addAll(walkRecords)

        try {
            val sortedRecords = if (walkRecords.isNotEmpty()) {
                walkRecords.sortedByDescending {
                    try {
                        it.getParsedTime()?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }.take(30)
            } else {
                listOf(WalkRecord("데이터 없음", getKoreaTimeString(), "0.0km", "0분", "0kcal"))
            }

            (context as? android.app.Activity)?.runOnUiThread {
                walkRecordAdapter = WalkRecordAdapter(sortedRecords)
                recyclerView.adapter = walkRecordAdapter

                dataUpdateListener?.onWalkDataUpdated(sortedRecords)
            }
        } catch (e: Exception) {
            Log.e("CrewWalkManager", "RecyclerView 업데이트 오류", e)
            (context as? android.app.Activity)?.runOnUiThread {
                val errorRecord = listOf(WalkRecord("오류 발생", getKoreaTimeString(), "0.0km", "0분", "0kcal"))
                walkRecordAdapter = WalkRecordAdapter(errorRecord)
                recyclerView.adapter = walkRecordAdapter

                dataUpdateListener?.onWalkDataUpdated(errorRecord)
            }
        }
    }

    private fun getKoreaTimeString(): String {
        val calendar = Calendar.getInstance(koreaTimeZone, koreaLocale)
        return SimpleDateFormat("yy-MM-dd HH:mm", koreaLocale).apply {
            timeZone = koreaTimeZone
        }.format(calendar.time)
    }

    fun clearData() {
        crewWalkData.clear()
    }
}