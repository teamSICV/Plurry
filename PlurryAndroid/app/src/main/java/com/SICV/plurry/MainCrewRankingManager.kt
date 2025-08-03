package com.SICV.plurry.ranking

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainCrewRankingManager(
    private val caloTextView: TextView,
    private val countTextView: TextView,
    private val distanceTextView: TextView
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    private var updateRunnable: Runnable? = null
    private var isRunning = false
    private var currentCrewId: String? = null

    private var previousCalo: Long? = null
    private var previousCount: Long? = null
    private var previousDistance: Float? = null

    fun startUpdating() {
        if (isRunning) {
            return
        }
        isRunning = true
        loadUserCrewInfo()
    }

    fun stopUpdating() {
        isRunning = false
        updateRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    private fun loadUserCrewInfo() {
        val currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            setDefaultValues()
            scheduleNextUpdate()
            return
        }

        firestore.collection("Users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val crewId = document.getString("crewAt")

                    if (crewId != null && crewId.isNotBlank()) {
                        currentCrewId = crewId
                        loadCrewData(crewId)
                    } else {
                        setDefaultValues()
                        scheduleNextUpdate()
                    }
                } else {
                    setDefaultValues()
                    scheduleNextUpdate()
                }
            }
            .addOnFailureListener {
                setDefaultValues()
                scheduleNextUpdate()
            }
    }

    private fun loadCrewData(crewId: String) {
        firestore.collection("Game")
            .document("crew")
            .collection("crewReward")
            .document(crewId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val crewCaloCount = document.getLong("crewCaloCount") ?: 0L
                    val crewWalkCount = document.getLong("crewWalkCount") ?: 0L
                    val crewDisCount = document.getDouble("crewDisCount")?.toFloat() ?: 0f

                    updateUI(crewCaloCount, crewWalkCount, crewDisCount)
                } else {
                    setDefaultValues()
                }
                scheduleNextUpdate()
            }
            .addOnFailureListener {
                setDefaultValues()
                scheduleNextUpdate()
            }
    }

    private fun updateUI(caloCount: Long, walkCount: Long, distance: Float) {
        if (previousCalo != caloCount) {
            val formattedCalo = formatNumber(caloCount.toInt())
            caloTextView.text = formattedCalo
            previousCalo = caloCount
        }

        if (previousCount != walkCount) {
            val formattedCount = formatNumber(walkCount.toInt())
            countTextView.text = formattedCount
            previousCount = walkCount
        }

        if (previousDistance != distance) {
            val formattedDistance = formatDistance(distance)
            distanceTextView.text = formattedDistance
            previousDistance = distance
        }
    }

    private fun setDefaultValues() {
        caloTextView.text = "0"
        countTextView.text = "0"
        distanceTextView.text = "0.0"

        previousCalo = null
        previousCount = null
        previousDistance = null
    }

    private fun formatNumber(number: Int): String {
        return when {
            number >= 1000000 -> String.format("%.1fM", number / 1000000.0)
            number >= 1000 -> String.format("%.1fK", number / 1000.0)
            else -> number.toString()
        }
    }

    private fun formatDistance(distance: Float): String {
        return String.format("%.1f", distance)
    }

    private fun scheduleNextUpdate() {
        if (!isRunning) {
            return
        }

        updateRunnable = Runnable {
            if (isRunning) {
                loadUserCrewInfo()
            }
        }
        handler.postDelayed(updateRunnable!!, 5000)
    }

    fun cleanup() {
        stopUpdating()
    }
}