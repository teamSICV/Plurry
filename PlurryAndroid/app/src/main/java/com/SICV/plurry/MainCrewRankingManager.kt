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
        distanceTextView.text = "0"

        previousCalo = null
        previousCount = null
        previousDistance = null
    }

    private fun formatNumber(number: Int): String {
        return when {
            number >= 10000000 -> {
                val millions = (number / 10000).toInt() / 100.0
                if (millions == millions.toInt().toDouble()) {
                    "${millions.toInt()}M"
                } else {
                    String.format("%.2f", millions).trimEnd('0').trimEnd('.') + "M"
                }
            }
            number >= 10000 -> {
                val thousands = (number / 10).toInt() / 100.0
                if (thousands == thousands.toInt().toDouble()) {
                    "${thousands.toInt()}K"
                } else {
                    String.format("%.2f", thousands).trimEnd('0').trimEnd('.') + "K"
                }
            }
            else -> number.toString()
        }
    }

    private fun formatDistance(distance: Float): String {
        return when {
            distance >= 10000 -> {
                val thousands = (distance * 100).toInt() / 100000.0
                if (thousands == thousands.toInt().toDouble()) {
                    "${thousands.toInt()}K"
                } else {
                    String.format("%.2f", thousands).trimEnd('0').trimEnd('.') + "K"
                }
            }
            else -> {
                val truncated = (distance * 100).toInt() / 100.0
                if (truncated == truncated.toInt().toDouble()) {
                    truncated.toInt().toString()
                } else {
                    String.format("%.2f", truncated).trimEnd('0').trimEnd('.')
                }
            }
        }
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