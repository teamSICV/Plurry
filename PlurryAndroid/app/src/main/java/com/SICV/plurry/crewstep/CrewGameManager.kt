package com.SICV.plurry.crewstep

import com.google.firebase.Timestamp
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
    }
}