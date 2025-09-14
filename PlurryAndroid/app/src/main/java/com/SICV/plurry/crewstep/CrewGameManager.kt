package com.SICV.plurry.crewstep

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class CrewGameManager {
    companion object {
        fun initializeCrewGameData(crewId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
            val db = FirebaseFirestore.getInstance()

            val crewGameData = hashMapOf(
                "crewRewardItem" to 0,
                "lastUpdated" to Timestamp.now(),
                "updateLock" to false,
                "level" to 0,
                "storyLevel" to 0,
                "crewWalkCount" to 0,
                "crewCaloCount" to 0,
                "crewId" to crewId,
                "crewDisCount" to 0
            )

            db.collection("Game")
                .document("crew")
                .collection("crewReward")
                .document(crewId)
                .set(crewGameData)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    onFailure(e)
                }
        }

        //게임-크루-락
        fun updateCrewWalkData(
            crewId: String,
            distance: Double,
            steps: Int,
            calories: Double,
            onComplete: ((Boolean) -> Unit)? = null
        ){
            android.util.Log.d("CrewGameManager", "updateCrewWalkData 호출 - 크루ID: $crewId")
            updateCrewWalkDataWithRetry(crewId, distance, steps, calories, 0, onComplete)
        }

        private fun updateCrewWalkDataWithRetry(
            crewId: String,
            distance: Double,
            steps: Int,
            calories: Double,
            retryCount: Int,
            onComplete: ((Boolean) -> Unit)? = null
        ) {
            android.util.Log.d("CrewGameManager", "재시도 함수 호출 - 시도횟수: $retryCount")

            val db = FirebaseFirestore.getInstance()
            val lockRef = db.collection("Game")
                .document("crew")
                .collection("countLock")
                .document(crewId)

            val crewRewardRef = db.collection("Game")
                .document("crew")
                .collection("crewReward")
                .document(crewId)

            android.util.Log.d("CrewGameManager", "countLock 경로: ${lockRef.path}")
            android.util.Log.d("CrewGameManager", "crewReward 경로: ${crewRewardRef.path}")

            lockRef.get()
                .addOnSuccessListener { lockDoc ->
                    android.util.Log.d("CrewGameManager", "lockRef.get() 성공 - 문서존재: ${lockDoc.exists()}")

                    val isLocked = lockDoc.getBoolean("locked") ?: false
                    android.util.Log.d("CrewGameManager", "락 상태: $isLocked")

                    if(isLocked) {
                        if(retryCount < 5) {
                            android.util.Log.d("CrewGameManager", "countLock 활성화로 재시도: ${retryCount + 1}/5")
                            updateCrewWalkDataWithRetry(crewId, distance, steps, calories, retryCount + 1, onComplete)
                        } else {
                            android.util.Log.e("CrewGameManager", "countLock으로 인한 최대 재시도 초과")
                            onComplete?.invoke(false)
                        }
                        return@addOnSuccessListener
                    }

                    android.util.Log.d("CrewGameManager", "락이 없음 - 트랜잭션 실행")
                    executeTransaction(lockRef, crewRewardRef, distance, steps, calories, retryCount, crewId, onComplete)
                }
                .addOnFailureListener { e ->
                    android.util.Log.d("CrewGameManager", "lockRef.get() 실패: ${e.message}")
                    android.util.Log.d("CrewGameManager", "countLock 문서 없음 - 트랜잭션 진행")
                    executeTransaction(lockRef, crewRewardRef, distance, steps, calories, retryCount, crewId, onComplete)
                }
        }

        private fun executeTransaction(
            lockRef: com.google.firebase.firestore.DocumentReference,
            crewRewardRef: com.google.firebase.firestore.DocumentReference,
            distance: Double,
            steps: Int,
            calories: Double,
            retryCount: Int,
            crewId: String,
            onComplete: ((Boolean) -> Unit)?
        ) {
            android.util.Log.d("CrewGameManager", "executeTransaction 함수 호출됨")

            val db = FirebaseFirestore.getInstance()

            db.runTransaction { transaction ->
                android.util.Log.d("CrewGameManager", "트랜잭션 블록 내부 실행")

                val lockSnapshot = transaction.get(lockRef)
                val isLocked = lockSnapshot.getBoolean("locked") ?: false
                android.util.Log.d("CrewGameManager", "트랜잭션 내 락 상태 확인: $isLocked")

                if(isLocked) {
                    android.util.Log.d("CrewGameManager", "트랜잭션 내에서 락 감지 - 예외 발생")
                    throw Exception("CountLock is currently held")
                }

                android.util.Log.d("CrewGameManager", "countLock 생성 중")
                transaction.set(lockRef, mapOf("locked" to true))

                android.util.Log.d("CrewGameManager", "crewReward 업데이트 중")
                val truncatedDistance = kotlin.math.floor(distance * 10000) / 10000.0
                val truncatedCalories = kotlin.math.floor(calories * 10000) / 10000.0

                transaction.update(crewRewardRef, mapOf(
                    "crewCaloCount" to FieldValue.increment(truncatedCalories),
                    "crewDisCount" to FieldValue.increment(truncatedDistance),
                    "crewWalkCount" to FieldValue.increment(steps.toLong()),
                    "lastUpdated" to Timestamp.now()
                ))

                android.util.Log.d("CrewGameManager", "countLock 삭제 중")
                transaction.delete(lockRef)

                android.util.Log.d("CrewGameManager", "트랜잭션 완료")
            }.addOnSuccessListener {
                android.util.Log.d("CrewGameManager", "트랜잭션 성공!")
                onComplete?.invoke(true)
            }.addOnFailureListener { e ->
                android.util.Log.e("CrewGameManager", "트랜잭션 실패: ${e.message}")
                android.util.Log.e("CrewGameManager", "실패 상세: ${e.javaClass.simpleName}")

                if(retryCount < 5) {
                    android.util.Log.d("CrewGameManager", "트랜잭션 실패로 재시도: ${retryCount + 1}/5")
                    updateCrewWalkDataWithRetry(crewId, distance, steps, calories, retryCount + 1, onComplete)
                } else {
                    android.util.Log.e("CrewGameManager", "최대 재시도 초과")
                    onComplete?.invoke(false)
                }
            }
        }
    }
}