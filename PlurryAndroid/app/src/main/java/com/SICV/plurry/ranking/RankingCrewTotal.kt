package com.SICV.plurry.ranking

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class RankingCrewTotal {
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeListeners = mutableMapOf<String, ListenerRegistration>()

    companion object {
        private const val TAG = "RankingCrewTotal"
        private const val LOCK_TIMEOUT_MS = 10000L
        private const val MAX_RETRY_ATTEMPTS = 5
    }

    fun startCrewScoreListener(crewId: String) {
        scope.launch {
            try {
                val crewMembers = getCrewMembers(crewId)
                if (crewMembers.isNotEmpty()) {
                    setupCrewMembersListener(crewId, crewMembers)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting crew score listener for crew: $crewId", e)
            }
        }
    }

    private fun setupCrewMembersListener(crewId: String, memberIds: List<String>) {
        stopCrewScoreListener(crewId)

        memberIds.forEach { memberId ->
            val listenerKey = "${crewId}_${memberId}"

            val listener = firestore.collection("Game")
                .document("users")
                .collection("userReward")
                .document(memberId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to member $memberId changes", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        scope.launch {
                            updateCrewTotalScoreWithRetry(crewId, 0)
                        }
                    }
                }

            activeListeners[listenerKey] = listener
        }
    }

    private suspend fun updateCrewTotalScoreWithRetry(crewId: String, retryCount: Int) {
        val lockKey = "crew_update_lock_$crewId"
        val lockValue = System.currentTimeMillis().toString() + "_" + Random.nextInt(1000)

        if (retryCount < MAX_RETRY_ATTEMPTS) {
            try {
                val lockAcquired = acquireDistributedLock(lockKey, lockValue)
                if (!lockAcquired) {
                    android.util.Log.d(TAG, "updateLock 활성화로 재시도: ${retryCount + 1}/5")
                    updateCrewTotalScoreWithRetry(crewId, retryCount + 1)
                    return
                }

                try {
                    val crewMembers = getCrewMembers(crewId)
                    val totalScore = calculateCrewTotalScore(crewMembers)
                    updateCrewRewardInTransaction(crewId, totalScore)

                } finally {
                    releaseDistributedLock(lockKey, lockValue)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error updating crew total score for $crewId, attempt ${retryCount + 1}/$MAX_RETRY_ATTEMPTS", e)
                updateCrewTotalScoreWithRetry(crewId, retryCount + 1)
            }
        } else {
            android.util.Log.e(TAG, "updateLock으로 인한 최대 재시도 초과")
        }
    }

    private suspend fun acquireDistributedLock(lockKey: String, lockValue: String): Boolean {
        return try {
            val lockRef = firestore.collection("Game")
                .document("crew")
                .collection("updateLock")
                .document(lockKey)

            val result = firestore.runTransaction { transaction ->
                val lockDoc = transaction.get(lockRef)

                if (!lockDoc.exists()) {
                    android.util.Log.d(TAG, "updateLock 생성 중")
                    transaction.set(lockRef, mapOf(
                        "value" to lockValue,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "expiresAt" to System.currentTimeMillis() + LOCK_TIMEOUT_MS
                    ))
                    true
                } else {
                    val existingValue = lockDoc.getString("value")
                    val expiresAt = lockDoc.getLong("expiresAt") ?: 0
                    val currentTime = System.currentTimeMillis()

                    if (existingValue == lockValue) {
                        true
                    } else if (currentTime > expiresAt) {
                        transaction.set(lockRef, mapOf(
                            "value" to lockValue,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "expiresAt" to System.currentTimeMillis() + LOCK_TIMEOUT_MS
                        ))
                        true
                    } else {
                        false
                    }
                }
            }.await()

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring distributed lock: $lockKey", e)
            false
        }
    }

    private suspend fun releaseDistributedLock(lockKey: String, lockValue: String) {
        try {
            val lockRef = firestore.collection("Game")
                .document("crew")
                .collection("updateLock")
                .document(lockKey)

            firestore.runTransaction { transaction ->
                val lockDoc = transaction.get(lockRef)

                if (lockDoc.exists()) {
                    val existingValue = lockDoc.getString("value")

                    if (existingValue == lockValue) {
                        transaction.delete(lockRef)
                        android.util.Log.d(TAG, "updateLock 삭제 중")
                    }
                }
            }.await()

        } catch (e: Exception) {
            Log.e(TAG, "Error releasing distributed lock: $lockKey", e)
        }
    }

    private suspend fun getCrewMembers(crewId: String): List<String> {
        return try {
            if (crewId.isBlank()) {
                return emptyList()
            }

            val crewDoc = firestore.collection("Crew")
                .document(crewId)
                .collection("member")
                .document("members")
                .get()
                .await()

            if (crewDoc.exists()) {
                val members = crewDoc.data?.keys?.toList() ?: emptyList()
                members
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting crew members for crewId: '$crewId'", e)
            emptyList()
        }
    }

    private suspend fun calculateCrewTotalScore(memberIds: List<String>): Int {
        var totalScore = 0

        try {
            for (memberId in memberIds) {
                val userRewardDoc = firestore.collection("Game")
                    .document("users")
                    .collection("userReward")
                    .document(memberId)
                    .get()
                    .await()

                if (userRewardDoc.exists()) {
                    val memberScore = userRewardDoc.getLong("crewRewardItem")?.toInt() ?: 0
                    totalScore += memberScore
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating crew total score", e)
        }

        return totalScore
    }

    private suspend fun updateCrewRewardInTransaction(crewId: String, totalScore: Int) {
        try {
            val crewRewardRef = firestore.collection("Game")
                .document("crew")
                .collection("crewReward")
                .document(crewId)

            firestore.runTransaction { transaction ->
                val crewRewardDoc = transaction.get(crewRewardRef)

                val updateData = if (crewRewardDoc.exists()) {
                    mapOf(
                        "crewRewardItem" to totalScore,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )
                } else {
                    mapOf(
                        "crewRewardItem" to totalScore,
                        "lastUpdated" to FieldValue.serverTimestamp(),
                        "crewId" to crewId
                    )
                }

                transaction.set(crewRewardRef, updateData, com.google.firebase.firestore.SetOptions.merge())
                android.util.Log.d(TAG, "크루 리워드 업데이트 성공!")
            }.await()

        } catch (e: Exception) {
            android.util.Log.e(TAG, "크루 리워드 업데이트 실패: ${e.message}")
            throw e
        }
    }

    fun stopCrewScoreListener(crewId: String) {
        val listenersToRemove = activeListeners.filter { it.key.startsWith("${crewId}_") }

        listenersToRemove.forEach { (key, listener) ->
            listener.remove()
            activeListeners.remove(key)
        }

        Log.d(TAG, "Stopped ${listenersToRemove.size} listeners for crew $crewId")
    }

    fun stopAllListeners() {
        activeListeners.values.forEach { it.remove() }
        activeListeners.clear()
        Log.d(TAG, "Stopped all crew score listeners")
    }

    fun manualRecalculateCrewScore(crewId: String) {
        scope.launch {
            updateCrewTotalScoreWithRetry(crewId, 0)
        }
    }

    fun recalculateAllCrewScores() {
        scope.launch {
            try {
                val crewsSnapshot = firestore.collection("Crew").get().await()

                for (crewDoc in crewsSnapshot.documents) {
                    val crewId = crewDoc.id
                    updateCrewTotalScoreWithRetry(crewId, 0)
                }

                Log.d(TAG, "Recalculated scores for ${crewsSnapshot.documents.size} crews")
            } catch (e: Exception) {
                Log.e(TAG, "Error recalculating all crew scores", e)
            }
        }
    }
}