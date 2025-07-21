package com.SICV.plurry.crewstep

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class CrewGameManager {
    companion object {
        fun initializeCrewGameData(crewId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
            val db = FirebaseFirestore.getInstance()

            val crewGameData = hashMapOf(
                "crewRewardItem" to null,
                "lastUpdated" to Timestamp.now(),
                "updateLock" to false,
                "characterName" to "",
                "level" to 0,
                "storyLevel" to 0
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