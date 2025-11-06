package com.SICV.plurry.ranking

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainRankingManager(
    private val titleTextView: TextView,
    private val valueTextView: TextView,
    private val unitTextView: TextView,
    private val leftArrow: ImageView,
    private val rightArrow: ImageView
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val crewTotalManager = RankingCrewTotal()

    private var currentUserId: String? = null
    private var currentCrewId: String? = null
    private var currentRankingType = RankingType.PERSONAL_RANKING

    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private val refreshInterval = 5000L

    enum class RankingType {
        PERSONAL_RANKING,
        CREW_CONTRIBUTION,
        CREW_RANKING
    }

    fun initialize() {
        setupClickListeners()
        loadUserInfo()
        valueTextView.gravity = android.view.Gravity.END
        startAutoRefresh()
    }

    private fun setupClickListeners() {
        leftArrow.setOnClickListener {
            currentRankingType = getPreviousType()
            updateDisplay()
            restartAutoRefresh()
        }

        rightArrow.setOnClickListener {
            currentRankingType = getNextType()
            updateDisplay()
            restartAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        stopAutoRefresh()

        refreshRunnable = object : Runnable {
            override fun run() {
                refreshUserCrewInfoAndData()
                refreshHandler.postDelayed(this, refreshInterval)
            }
        }
        refreshHandler.postDelayed(refreshRunnable!!, refreshInterval)
    }

    private fun stopAutoRefresh() {
        refreshRunnable?.let {
            refreshHandler.removeCallbacks(it)
        }
        refreshRunnable = null
    }

    private fun restartAutoRefresh() {
        stopAutoRefresh()
        startAutoRefresh()
    }

    private fun refreshUserCrewInfoAndData() {
        currentUserId?.let { userId ->
            firestore.collection("Users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    try {
                        val previousCrewId = currentCrewId

                        if (document.exists()) {
                            val newCrewId = document.getString("crewAt")
                            currentCrewId = if (newCrewId.isNullOrBlank()) null else newCrewId
                        } else {
                            currentCrewId = null
                        }

                        if (previousCrewId != currentCrewId) {
                            if (currentCrewId != null && currentCrewId!!.isNotBlank()) {
                                crewTotalManager.startCrewScoreListener(currentCrewId!!)
                            }

                            if (currentRankingType == RankingType.CREW_CONTRIBUTION ||
                                currentRankingType == RankingType.CREW_RANKING) {
                                if (currentCrewId == null || currentCrewId!!.isBlank()) {
                                    valueTextView.text = "--"
                                }
                            }
                        }
                        loadCurrentRankingData()
                    } catch (e: Exception) {
                        loadCurrentRankingData()
                    }
                }
                .addOnFailureListener {
                    loadCurrentRankingData()
                }
        }
    }

    private fun loadCurrentRankingData() {
        when (currentRankingType) {
            RankingType.PERSONAL_RANKING -> loadPersonalRanking()
            RankingType.CREW_CONTRIBUTION -> loadCrewContribution()
            RankingType.CREW_RANKING -> loadCrewRanking()
        }
    }

    private fun loadUserInfo() {
        currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            updateDisplay()
            return
        }

        currentUserId?.let { userId ->
            firestore.collection("Users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    try {
                        if (document.exists()) {
                            currentCrewId = document.getString("crewAt")

                            currentCrewId?.let { crewId ->
                                if (crewId.isNotBlank()) {
                                    crewTotalManager.startCrewScoreListener(crewId)
                                }
                            }

                            updateDisplay()
                        } else {
                            updateDisplay()
                        }
                    } catch (e: Exception) {
                        updateDisplay()
                    }
                }
                .addOnFailureListener {
                    updateDisplay()
                }
        }
    }

    private fun updateDisplay() {
        when (currentRankingType) {
            RankingType.PERSONAL_RANKING -> {
                titleTextView.text = "개인\n랭킹"
                unitTextView.text = "위"
                loadPersonalRanking()
            }
            RankingType.CREW_CONTRIBUTION -> {
                titleTextView.text = "크루\n기여도"
                unitTextView.text = "위"
                loadCrewContribution()
            }
            RankingType.CREW_RANKING -> {
                titleTextView.text = "크루\n순위"
                unitTextView.text = "위"
                loadCrewRanking()
            }
        }
    }

    private fun loadPersonalRanking() {
        currentUserId?.let { userId ->
            firestore.collection("Game")
                .document("users")
                .collection("userReward")
                .get()
                .addOnSuccessListener { documents ->
                    val userScoreList = mutableListOf<Pair<String, Int>>()

                    for (document in documents) {
                        val docUserId = document.id
                        val level = document.getLong("level")?.toInt() ?: 0
                        val currentRaisingPoint = document.getLong("currentRaisingPoint")?.toInt() ?: 0
                        val score = level * 100 + currentRaisingPoint
                        userScoreList.add(Pair(docUserId, score))
                    }

                    userScoreList.sortByDescending { it.second }

                    // 동점자 처리
                    var rank = 1
                    var previousScore: Int? = null
                    val rankMap = mutableMapOf<String, Int>()

                    userScoreList.forEachIndexed { index, (id, score) ->
                        if (previousScore != null && previousScore != score) {
                            rank = index + 1
                        }
                        rankMap[id] = rank
                        previousScore = score
                    }

                    val myRank = rankMap[userId] ?: 0
                    valueTextView.text = if (myRank > 0) myRank.toString() else "0"
                }
        }
    }

    private fun loadCrewContribution() {
        if (currentCrewId == null || currentCrewId.isNullOrBlank() || currentUserId == null) {
            valueTextView.text = "--"
            return
        }

        firestore.collection("Users")
            .whereEqualTo("crewAt", currentCrewId)
            .get()
            .addOnSuccessListener { crewMembers ->
                val crewMemberIds = crewMembers.documents.map { it.id }

                firestore.collection("Game")
                    .document("users")
                    .collection("userReward")
                    .get()
                    .addOnSuccessListener { documents ->
                        val crewContributionList = mutableListOf<Pair<String, Int>>()

                        for (document in documents) {
                            val userId = document.id
                            if (userId in crewMemberIds) {
                                val level = document.getLong("level")?.toInt() ?: 0
                                val currentRaisingPoint = document.getLong("currentRaisingPoint")?.toInt() ?: 0
                                val score = level * 100 + currentRaisingPoint
                                crewContributionList.add(Pair(userId, score))
                            }
                        }

                        crewContributionList.sortByDescending { it.second }

                        // 동점자 처리
                        var rank = 1
                        var previousScore: Int? = null
                        val rankMap = mutableMapOf<String, Int>()

                        crewContributionList.forEachIndexed { index, (id, score) ->
                            if (previousScore != null && previousScore != score) {
                                rank = index + 1
                            }
                            rankMap[id] = rank
                            previousScore = score
                        }

                        val myContributionRank = rankMap[currentUserId] ?: 0
                        valueTextView.text = if (myContributionRank > 0) myContributionRank.toString() else "--"
                    }
                    .addOnFailureListener {
                        valueTextView.text = "--"
                    }
            }
            .addOnFailureListener {
                valueTextView.text = "--"
            }
    }

    private fun loadCrewRanking() {
        if (currentCrewId == null || currentCrewId.isNullOrBlank()) {
            valueTextView.text = "--"
            return
        }

        firestore.collection("Crew")
            .get()
            .addOnSuccessListener { crewDocs ->
                val crewScoreList = mutableListOf<Pair<String, Int>>()

                val tasksCompleted = java.util.concurrent.atomic.AtomicInteger(0)
                val totalCrews = crewDocs.size()

                if (totalCrews == 0) {
                    valueTextView.text = "--"
                    return@addOnSuccessListener
                }

                for (crewDoc in crewDocs) {
                    val crewId = crewDoc.id

                    firestore.collection("Users")
                        .whereEqualTo("crewAt", crewId)
                        .get()
                        .addOnSuccessListener { members ->
                            val memberIds = members.documents.map { it.id }

                            if (memberIds.isEmpty()) {
                                if (tasksCompleted.incrementAndGet() == totalCrews) {
                                    calculateCrewRanking(crewScoreList)
                                }
                                return@addOnSuccessListener
                            }

                            firestore.collection("Game")
                                .document("users")
                                .collection("userReward")
                                .get()
                                .addOnSuccessListener { userRewards ->
                                    var totalScore = 0

                                    for (userReward in userRewards) {
                                        if (userReward.id in memberIds) {
                                            val level = userReward.getLong("level")?.toInt() ?: 0
                                            val currentRaisingPoint = userReward.getLong("currentRaisingPoint")?.toInt() ?: 0
                                            totalScore += (level * 100 + currentRaisingPoint)
                                        }
                                    }

                                    crewScoreList.add(Pair(crewId, totalScore))

                                    if (tasksCompleted.incrementAndGet() == totalCrews) {
                                        calculateCrewRanking(crewScoreList)
                                    }
                                }
                                .addOnFailureListener {
                                    if (tasksCompleted.incrementAndGet() == totalCrews) {
                                        calculateCrewRanking(crewScoreList)
                                    }
                                }
                        }
                        .addOnFailureListener {
                            if (tasksCompleted.incrementAndGet() == totalCrews) {
                                calculateCrewRanking(crewScoreList)
                            }
                        }
                }
            }
            .addOnFailureListener {
                valueTextView.text = "--"
            }
    }

    private fun calculateCrewRanking(crewScoreList: List<Pair<String, Int>>) {
        crewScoreList.sortedByDescending { it.second }.let { sortedList ->
            // 동점자 처리
            var rank = 1
            var previousScore: Int? = null
            val rankMap = mutableMapOf<String, Int>()

            sortedList.forEachIndexed { index, (id, score) ->
                if (previousScore != null && previousScore != score) {
                    rank = index + 1
                }
                rankMap[id] = rank
                previousScore = score
            }

            val myCrewRank = rankMap[currentCrewId] ?: 0
            valueTextView.text = if (myCrewRank > 0) myCrewRank.toString() else "--"
        }
    }

    private fun getNextType(): RankingType {
        return when (currentRankingType) {
            RankingType.PERSONAL_RANKING -> RankingType.CREW_CONTRIBUTION
            RankingType.CREW_CONTRIBUTION -> RankingType.CREW_RANKING
            RankingType.CREW_RANKING -> RankingType.PERSONAL_RANKING
        }
    }

    private fun getPreviousType(): RankingType {
        return when (currentRankingType) {
            RankingType.PERSONAL_RANKING -> RankingType.CREW_RANKING
            RankingType.CREW_CONTRIBUTION -> RankingType.PERSONAL_RANKING
            RankingType.CREW_RANKING -> RankingType.CREW_CONTRIBUTION
        }
    }

    fun cleanup() {
        stopAutoRefresh()
        crewTotalManager.stopAllListeners()
    }
}