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
import kotlinx.coroutines.delay
import kotlin.random.Random

class RankingCrewTotal {
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeListeners = mutableMapOf<String, ListenerRegistration>()

    companion object {
        private const val TAG = "RankingCrewTotal"
        private const val LOCK_TIMEOUT_MS = 10000L
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val RETRY_DELAY_MS = 1000L
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
                            updateCrewTotalScore(crewId)
                        }
                    }
                }

            activeListeners[listenerKey] = listener
        }
    }

    private suspend fun updateCrewTotalScore(crewId: String) {
        val lockKey = "crew_update_lock_$crewId"
        val lockValue = System.currentTimeMillis().toString() + "_" + Random.nextInt(1000)

        Log.d(TAG, "Starting crew score update for: $crewId")

        var retryCount = 0
        while (retryCount < MAX_RETRY_ATTEMPTS) {
            try {
                val lockAcquired = acquireDistributedLock(lockKey, lockValue)
                if (!lockAcquired) {
                    Log.d(TAG, "Failed to acquire lock for crew $crewId, attempt ${retryCount + 1}/$MAX_RETRY_ATTEMPTS")
                    delay(RETRY_DELAY_MS + Random.nextLong(500)) // 지터 추가
                    retryCount++
                    continue
                }

                Log.d(TAG, "Lock acquired! Processing crew $crewId score update...")

                try {
                    val crewMembers = getCrewMembers(crewId)
                    val totalScore = calculateCrewTotalScore(crewMembers)

                    updateCrewRewardInTransaction(crewId, totalScore)

                    Log.d(TAG, "Successfully updated crew $crewId total score: $totalScore")
                    break

                } finally {
                    releaseDistributedLock(lockKey, lockValue)
                    Log.d(TAG, "Completed processing for crew: $crewId")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error updating crew total score for $crewId, attempt ${retryCount + 1}/$MAX_RETRY_ATTEMPTS", e)
                retryCount++
                if (retryCount < MAX_RETRY_ATTEMPTS) {
                    delay(RETRY_DELAY_MS * retryCount)
                }
            }
        }

        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "Failed to update crew $crewId after $MAX_RETRY_ATTEMPTS attempts")
        }
    }

    private suspend fun acquireDistributedLock(lockKey: String, lockValue: String): Boolean {
        return try {
            Log.d(TAG, "Attempting to acquire lock: $lockKey with value: $lockValue")

            val lockRef = firestore.collection("Game")
                .document("crew")
                .collection("updateLock")
                .document(lockKey)

            val result = firestore.runTransaction { transaction ->
                val lockDoc = transaction.get(lockRef)

                if (!lockDoc.exists()) {
                    Log.d(TAG, "Lock document doesn't exist, creating new lock: $lockKey")
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

                    Log.d(TAG, "Existing lock found - Key: $lockKey, ExistingValue: $existingValue, ExpiresAt: $expiresAt, CurrentTime: $currentTime")

                    if (existingValue == lockValue) {
                        Log.d(TAG, "Same process already owns the lock: $lockKey")
                        true
                    } else if (currentTime > expiresAt) {
                        Log.d(TAG, "Lock expired, taking over: $lockKey")
                        transaction.set(lockRef, mapOf(
                            "value" to lockValue,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "expiresAt" to System.currentTimeMillis() + LOCK_TIMEOUT_MS
                        ))
                        true
                    } else {
                        Log.d(TAG, "Lock is held by another process: $lockKey (remaining: ${expiresAt - currentTime}ms)")
                        false
                    }
                }
            }.await()

            if (result) {
                Log.d(TAG, "Successfully acquired lock: $lockKey")
            } else {
                Log.d(TAG, "Failed to acquire lock: $lockKey")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring distributed lock: $lockKey", e)
            false
        }
    }

    private suspend fun releaseDistributedLock(lockKey: String, lockValue: String) {
        try {
            Log.d(TAG, "Attempting to release lock: $lockKey with value: $lockValue")

            val lockRef = firestore.collection("Game")
                .document("crew")
                .collection("updateLock")
                .document(lockKey)

            firestore.runTransaction { transaction ->
                val lockDoc = transaction.get(lockRef)

                if (lockDoc.exists()) {
                    val existingValue = lockDoc.getString("value")
                    Log.d(TAG, "Lock release check - Key: $lockKey, ExistingValue: $existingValue, MyValue: $lockValue")

                    if (existingValue == lockValue) {
                        transaction.delete(lockRef)
                        Log.d(TAG, "Successfully released and deleted lock: $lockKey")
                    } else {
                        Log.w(TAG, "Cannot release lock - not owned by this process: $lockKey")
                    }
                } else {
                    Log.w(TAG, "Lock document not found during release: $lockKey")
                }
            }.await()

        } catch (e: Exception) {
            Log.e(TAG, "Error releasing distributed lock: $lockKey", e)
        }
    }

    private suspend fun getCrewMembers(crewId: String): List<String> {
        return try {
            val crewDoc = firestore.collection("Crew")
                .document(crewId)
                .collection("member")
                .document("members")
                .get()
                .await()

            if (crewDoc.exists()) {
                val members = crewDoc.data?.keys?.toList() ?: emptyList()
                Log.d(TAG, "Found ${members.size} members for crew $crewId")
                members
            } else {
                Log.w(TAG, "No members found for crew $crewId")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting crew members for $crewId", e)
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
                    Log.d(TAG, "Member $memberId score: $memberScore")
                } else {
                    Log.d(TAG, "No userReward document found for member $memberId")
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
            }.await()

            Log.d(TAG, "Successfully updated crew $crewId reward to $totalScore")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating crew reward for $crewId", e)
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
            updateCrewTotalScore(crewId)
        }
    }

    fun recalculateAllCrewScores() {
        scope.launch {
            try {
                val crewsSnapshot = firestore.collection("Crew").get().await()

                for (crewDoc in crewsSnapshot.documents) {
                    val crewId = crewDoc.id
                    updateCrewTotalScore(crewId)
                    delay(100)
                }

                Log.d(TAG, "Recalculated scores for ${crewsSnapshot.documents.size} crews")
            } catch (e: Exception) {
                Log.e(TAG, "Error recalculating all crew scores", e)
            }
        }
    }
}