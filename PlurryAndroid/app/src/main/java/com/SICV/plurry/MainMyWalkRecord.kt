package com.SICV.plurry

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.floor

class MainMyWalkRecord(
    private val caloriesTextView: TextView,
    private val distanceTextView: TextView,
    private val stepCountTextView: TextView
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var isUpdating = false

    private var lastCalories: Double? = null
    private var lastDistance: Double? = null
    private var lastStepCount: Long? = null

    fun startUpdating() {
        if (isUpdating) return
        isUpdating = true
        loadLatestWalkData()
        scheduleNextUpdate()
    }

    fun stopUpdating() {
        isUpdating = false
        updateRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun scheduleNextUpdate() {
        if (!isUpdating) return

        updateRunnable = Runnable {
            if (isUpdating) {
                loadLatestWalkData()
                scheduleNextUpdate()
            }
        }
        handler.postDelayed(updateRunnable!!, 5000)
    }

    private fun loadLatestWalkData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showDefaultValues()
            return
        }

        val uid = currentUser.uid

        db.collection("Users").document(uid).collection("goWalk")
            .orderBy("endTime", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val latestWalk = documents.first()

                    val calories = latestWalk.getDouble("calories") ?: 0.0
                    val distance = latestWalk.getDouble("distance") ?: 0.0
                    val stepCount = latestWalk.getLong("stepCount") ?: 0L

                    if (hasDataChanged(calories, distance, stepCount)) {
                        updateUI(calories, distance, stepCount)
                        lastCalories = calories
                        lastDistance = distance
                        lastStepCount = stepCount
                    }
                } else {
                    if (lastCalories != null || lastDistance != null || lastStepCount != null) {
                        showDefaultValues()
                        lastCalories = null
                        lastDistance = null
                        lastStepCount = null
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MainMyWalkRecord", "최신 산책 데이터 로드 실패", exception)
                if (lastCalories != null || lastDistance != null || lastStepCount != null) {
                    showDefaultValues()
                    lastCalories = null
                    lastDistance = null
                    lastStepCount = null
                }
            }
    }

    private fun hasDataChanged(calories: Double, distance: Double, stepCount: Long): Boolean {
        return lastCalories != calories || lastDistance != distance || lastStepCount != stepCount
    }

    private fun updateUI(calories: Double, distance: Double, stepCount: Long) {
        caloriesTextView.text = formatWithUnits(calories)

        distanceTextView.text = formatDistance(distance)

        stepCountTextView.text = formatWithUnits(stepCount.toDouble())
    }

    private fun formatWithUnits(value: Double): String {
        return when {
            value >= 10000000 -> {
                val millions = (value / 10000).toInt() / 100.0
                if (millions == millions.toInt().toDouble()) {
                    "${millions.toInt()}M"
                } else {
                    String.format("%.2f", millions).trimEnd('0').trimEnd('.') + "M"
                }
            }
            value >= 10000 -> {
                val thousands = (value / 10).toInt() / 100.0
                if (thousands == thousands.toInt().toDouble()) {
                    "${thousands.toInt()}K"
                } else {
                    String.format("%.2f", thousands).trimEnd('0').trimEnd('.') + "K"
                }
            }
            else -> {
                val truncated = (value * 100).toInt() / 100.0
                if (truncated == truncated.toInt().toDouble()) {
                    truncated.toInt().toString()
                } else {
                    String.format("%.2f", truncated).trimEnd('0').trimEnd('.')
                }
            }
        }
    }

    private fun formatDistance(distance: Double): String {
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
                String.format("%.2f", truncated)
            }
        }
    }

    private fun showDefaultValues() {
        caloriesTextView.text = "0"
        distanceTextView.text = "0.00"
        stepCountTextView.text = "0"
    }

    fun cleanup() {
        stopUpdating()
        lastCalories = null
        lastDistance = null
        lastStepCount = null
    }
}