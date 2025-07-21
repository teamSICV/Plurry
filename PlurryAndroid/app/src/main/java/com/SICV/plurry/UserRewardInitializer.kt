package com.SICV.plurry

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.unity3d.player.a.e

class UserRewardInitializer {
    companion object{
        private const val TAG = "UserRewardInitializer"

        fun intializeUserReward(onSucces: ()-> Unit, onFailure: (Exception)-> Unit){
            val currentUser = FirebaseAuth.getInstance().currentUser
            if(currentUser == null){
                onFailure(Exception("User not logged in"))
                return
            }

            val uid = currentUser.uid
            val db = FirebaseFirestore.getInstance()

            val userRewardData = hashMapOf(
                "characterName" to "",
                "crewRewardItem" to null,
                "level" to 0,
                "storyLevel" to 0,
                "userRewardItem" to 0,
                "lastUpdated" to Timestamp.now()
            )

            db.collection("Game")
                .document("users")
                .collection("userReward")
                .document(uid)
                .set(userRewardData)
                .addOnSuccessListener {
                    onSucces()
                }
                .addOnFailureListener { e->
                    onFailure(e)
                }
        }
    }
}