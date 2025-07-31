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
import com.google.firebase.storage.FirebaseStorage
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

        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            firestore.collection("Users")
                .document(currentUserId)
                .update("crewAt", "")
            Log.d(TAG, "Started clearing current leader's crewAt: $currentUserId")
        }

        onConfirm()
        dismiss()

        scope.launch {
            try {
                val currentUserId = auth.currentUser?.uid
                if (currentUserId != null) {
                    firestore.collection("Users")
                        .document(currentUserId)
                        .update("crewAt", "")
                        .await()
                }

                firestore.collection("Crew")
                    .document(crewId)
                    .delete()
                    .await()

                clearMembersCrewAt(crewId)
                deleteCrewPlaceDocuments(crewId)
                deleteCrewRewardDocument(crewId)
                deleteCrewStorage(crewId)

                Log.d(TAG, "Successfully deleted crew: $crewId")

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting crew: $crewId", e)
            }
        }
    }

    private suspend fun clearMembersCrewAt(crewId: String) {
        try {
            val membersDoc = firestore.collection("Crew")
                .document(crewId)
                .collection("member")
                .document("members")
                .get()
                .await()

            if (membersDoc.exists()) {
                val membersData = membersDoc.data
                membersData?.keys?.forEach { memberUid ->
                    try {
                        firestore.collection("Users")
                            .document(memberUid)
                            .update("crewAt", "")
                            .await()
                        Log.d(TAG, "Cleared crewAt for member: $memberUid")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear crewAt for member $memberUid: ${e.message}")
                    }
                }

                membersDoc.reference.delete().await()
                Log.d(TAG, "Deleted members document")
            }

            val leaderDoc = firestore.collection("Crew")
                .document(crewId)
                .collection("member")
                .document("leader")
                .get()
                .await()

            if (leaderDoc.exists()) {
                val leaderData = leaderDoc.data
                val leaderField = leaderData?.get("leader") as? String

                if (leaderField != null) {
                    try {
                        firestore.collection("Users")
                            .document(leaderField)
                            .update("crewAt", "")
                            .await()
                        Log.d(TAG, "Cleared crewAt for leader: $leaderField")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear crewAt for leader $leaderField: ${e.message}")
                    }
                }

                leaderDoc.reference.delete().await()
                Log.d(TAG, "Deleted leader document")
            }

            Log.d(TAG, "Successfully cleared crewAt for all members and leader")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing members crewAt", e)
        }
    }

    private suspend fun deleteCrewPlaceDocuments(crewId: String) {
        try {
            val crewPlaceRef = firestore.collection("Crew")
                .document(crewId)
                .collection("crewPlace")

            val documents = crewPlaceRef.get().await()

            documents.documents.forEach { document ->
                document.reference.delete().await()
                Log.d(TAG, "Deleted crewPlace document: ${document.id}")
            }

            Log.d(TAG, "Successfully deleted all crewPlace documents for crew: $crewId")
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting crewPlace documents for crew $crewId: ${e.message}")
        }
    }

    private suspend fun deleteCrewRewardDocument(crewId: String) {
        try {
            firestore.collection("Game")
                .document("crew")
                .collection("crewReward")
                .document(crewId)
                .delete()
                .await()

            Log.d(TAG, "Successfully deleted crewReward document for crew: $crewId")
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting crewReward document for crew $crewId: ${e.message}")
        }
    }

    private suspend fun deleteCrewStorage(crewId: String) {
        try {
            val storage = FirebaseStorage.getInstance("gs://plurry-855a9.firebasestorage.app")
            val crewRef = storage.reference.child("Crew").child(crewId)

            val result = crewRef.listAll().await()

            result.items.forEach { file ->
                file.delete().await()
            }

            result.prefixes.forEach { folder ->
                deleteStorageFolder(folder)
            }

            Log.d(TAG, "Deleted storage folder: Crew/$crewId")
        } catch (e: Exception) {
            Log.w(TAG, "Storage deletion failed for Crew/$crewId: ${e.message}")
        }
    }

    private suspend fun deleteStorageFolder(folderRef: com.google.firebase.storage.StorageReference) {
        val result = folderRef.listAll().await()

        result.items.forEach { file ->
            file.delete().await()
        }

        result.prefixes.forEach { subfolder ->
            deleteStorageFolder(subfolder)
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