package com.SICV.plurry.crewstep

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import com.SICV.plurry.R
import com.SICV.plurry.ranking.RankingCrewTotal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class CrewExit(
    context: Context,
    private val isLeader: Boolean,
    private val crewId: String?,
    private val onConfirm: () -> Unit
) : Dialog(context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val crewTotalManager = RankingCrewTotal()
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "CrewExit"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isLeader) {
            setContentView(R.layout.activity_crew_leader_exit)
        } else {
            setContentView(R.layout.activity_crew_exit)
        }

        setCancelable(false)
        setCanceledOnTouchOutside(false)

        setupButtons()
    }

    private fun setupButtons() {
        if (isLeader) {
            val btnConfirm = findViewById<Button>(R.id.btnLeaderExitOk)
            val btnCancel = findViewById<Button>(R.id.btnLeaderExitBack)

            btnConfirm.setOnClickListener {
                handleLeaderExit()
            }

            btnCancel.setOnClickListener {
                dismiss()
            }
        } else {
            val btnConfirm = findViewById<Button>(R.id.btnExitOk)
            val btnCancel = findViewById<Button>(R.id.btnExitBack)

            btnConfirm.setOnClickListener {
                handleCrewExit()
            }

            btnCancel.setOnClickListener {
                dismiss()
            }
        }
    }

    private fun handleLeaderExit() {
        if (crewId == null) {
            Log.e(TAG, "Crew ID is null")
            onConfirm()
            dismiss()
            return
        }

        scope.launch {
            try {
                firestore.collection("Crew")
                    .document(crewId)
                    .delete()
                    .await()

                firestore.collection("Game")
                    .document("crew")
                    .collection("crewReward")
                    .document(crewId)
                    .delete()
                    .await()

                Log.d(TAG, "Leader deleted crew: $crewId")
                onConfirm()

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting crew", e)
                onConfirm()
            } finally {
                dismiss()
            }
        }
    }

    private fun handleCrewExit() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.e(TAG, "Current user ID is null")
            onConfirm()
            dismiss()
            return
        }

        scope.launch {
            try {
                val userDoc = firestore.collection("Users")
                    .document(currentUserId)
                    .get()
                    .await()

                val currentCrewId = userDoc.getString("crewAt")

                if (currentCrewId != null) {
                    Log.d(TAG, "User $currentUserId is leaving crew $currentCrewId")

                    onConfirm()

                    delay(500)
                    crewTotalManager.manualRecalculateCrewScore(currentCrewId)

                    Log.d(TAG, "Crew score recalculation triggered for crew: $currentCrewId")
                } else {
                    Log.w(TAG, "User is not in any crew")
                    onConfirm()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling crew exit", e)
                onConfirm()
            } finally {
                dismiss()
            }
        }
    }
}