package com.SICV.plurry.ranking

import com.SICV.plurry.ranking.RankingAdapter
import com.SICV.plurry.ranking.RankingRecord
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RankingMainActivity : AppCompatActivity() {

    private lateinit var rankingRecyclerview: RecyclerView
    private lateinit var rankingAdapter: RankingAdapter
    private val rankingList = mutableListOf<RankingRecord>()

    private lateinit var rankingMe: TextView
    private lateinit var rankingCrewMe: TextView
    private lateinit var rankingCrew: TextView
    private lateinit var partName: TextView

    private lateinit var rankingImg1: CircleImageView
    private lateinit var rankingImg2: CircleImageView
    private lateinit var rankingImg3: CircleImageView
    private lateinit var rankingTxt1: TextView
    private lateinit var rankingTxt2: TextView
    private lateinit var rankingTxt3: TextView

    private lateinit var myRankRanking: TextView
    private lateinit var myRankProfile: CircleImageView
    private lateinit var myRankName: TextView
    private lateinit var myRankRecord: TextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var currentUserCrewId: String? = null
    private var currentTabType: TabType = TabType.PERSONAL

    private lateinit var crewTotalManager: RankingCrewTotal
    private var crewMemberListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ranking_main)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        crewTotalManager = RankingCrewTotal()

        val rankingBackBtn = findViewById<ImageView>(R.id.rankingBackBtn)

        initViews()
        setupRecyclerView()
        setupTabClickListeners()
        getUserCrewInfo()
        selectTab(TabType.PERSONAL)

        rankingBackBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        startCrewMemberChangeListener()
    }

    private fun initViews() {
        rankingRecyclerview = findViewById(R.id.rankingRecyclerview)
        rankingMe = findViewById(R.id.rankingMe)
        rankingCrewMe = findViewById(R.id.rankingCrewMe)
        rankingCrew = findViewById(R.id.rankingCrew)
        partName = findViewById(R.id.partName)

        rankingImg1 = findViewById(R.id.rankingImg1)
        rankingImg2 = findViewById(R.id.rankingImg2)
        rankingImg3 = findViewById(R.id.rankingImg3)
        rankingTxt1 = findViewById(R.id.rankingTxt1)
        rankingTxt2 = findViewById(R.id.rankingTxt2)
        rankingTxt3 = findViewById(R.id.rankingTxt3)

        myRankRanking = findViewById(R.id.myRankRanking)
        myRankProfile = findViewById(R.id.myRankProfile)
        myRankName = findViewById(R.id.myRankName)
        myRankRecord = findViewById(R.id.myRankRecord)
    }

    private fun setupRecyclerView() {
        rankingAdapter = RankingAdapter(this, rankingList)
        val layoutManager = LinearLayoutManager(this)
        rankingRecyclerview.layoutManager = layoutManager
        rankingRecyclerview.adapter = rankingAdapter
    }

    private fun setupTabClickListeners() {
        rankingMe.setOnClickListener { selectTab(TabType.PERSONAL) }
        rankingCrewMe.setOnClickListener { selectTab(TabType.CREW_PERSONAL) }
        rankingCrew.setOnClickListener { selectTab(TabType.CREW) }
    }

    private fun getUserCrewInfo() {
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection("Users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val newCrewId = document.getString("crewAt")

                    if (currentUserCrewId != newCrewId) {
                        crewMemberListener?.remove()
                        crewTotalManager.stopAllListeners()
                    }

                    currentUserCrewId = newCrewId

                    currentUserCrewId?.let { crewId ->
                        crewTotalManager.startCrewScoreListener(crewId)
                        startCrewMemberChangeListener()
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("RankingActivity", "Error getting user crew info: ", exception)
            }
    }

    private fun selectTab(tabType: TabType) {
        currentTabType = tabType
        resetTabColors()

        when (tabType) {
            TabType.PERSONAL -> {
                rankingMe.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                partName.text = "개인"
                loadPersonalRankingData()
            }
            TabType.CREW_PERSONAL -> {
                rankingCrewMe.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                partName.text = "크루 기여도"
                loadCrewPersonalRankingData()
            }
            TabType.CREW -> {
                rankingCrew.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                partName.text = "크루"
                loadCrewRankingData()
            }
        }
    }

    private fun resetTabColors() {
        val grayColor = ContextCompat.getColor(this, R.color.gray)
        rankingMe.setTextColor(grayColor)
        rankingCrewMe.setTextColor(grayColor)
        rankingCrew.setTextColor(grayColor)
    }

    private fun loadPersonalRankingData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Game/users/userReward에서 모든 유저 데이터 가져오기
                val userRewardSnapshot = firestore.collection("Game")
                    .document("users")
                    .collection("userReward")
                    .get()
                    .await()

                val userScoreList = mutableListOf<Triple<String, Int, Map<String, Any?>>>()

                for (rewardDoc in userRewardSnapshot.documents) {
                    val userId = rewardDoc.id
                    val level = rewardDoc.getLong("level")?.toInt() ?: 0
                    val currentRaisingPoint = rewardDoc.getLong("currentRaisingPoint")?.toInt() ?: 0
                    val personalScore = level * 100 + currentRaisingPoint

                    // Users 컬렉션에서 닉네임과 프로필 이미지 가져오기
                    val userDoc = firestore.collection("Users").document(userId).get().await()
                    val userInfo = mapOf(
                        "name" to userDoc.getString("name"),
                        "profileImg" to userDoc.getString("profileImg")
                    )

                    userScoreList.add(Triple(userId, personalScore, userInfo))
                }

                userScoreList.sortByDescending { it.second }

                val rankingData = assignRanksWithTies(userScoreList)

                withContext(Dispatchers.Main) {
                    updateRankingList(rankingData)
                    updateTopThreeRanking(rankingData)
                    updateMyRankingInfo(rankingData)
                }
            } catch (e: Exception) {
                Log.e("RankingActivity", "Error loading personal ranking: ", e)
            }
        }
    }

    private fun loadCrewPersonalRankingData() {
        if (currentUserCrewId == null) {
            updateRankingList(emptyList())
            updateTopThreeRanking(emptyList())
            updateMyRankingInfoForNoCrew()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val crewMemberIds = getCrewMembers(currentUserCrewId!!)

                if (crewMemberIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        updateRankingList(emptyList())
                        updateTopThreeRanking(emptyList())
                        updateMyRankingInfoForNoCrew()
                    }
                    return@launch
                }

                val userScoreList = mutableListOf<Triple<String, Int, Map<String, Any?>>>()

                for (userId in crewMemberIds) {
                    // Game/users/userReward에서 레벨과 포인트 가져오기
                    val rewardDoc = firestore.collection("Game")
                        .document("users")
                        .collection("userReward")
                        .document(userId)
                        .get()
                        .await()

                    if (rewardDoc.exists()) {
                        val level = rewardDoc.getLong("level")?.toInt() ?: 0
                        val currentRaisingPoint = rewardDoc.getLong("currentRaisingPoint")?.toInt() ?: 0
                        val personalScore = level * 100 + currentRaisingPoint

                        // Users 컬렉션에서 닉네임과 프로필 이미지 가져오기
                        val userDoc = firestore.collection("Users").document(userId).get().await()
                        val userInfo = mapOf(
                            "name" to userDoc.getString("name"),
                            "profileImg" to userDoc.getString("profileImg")
                        )

                        userScoreList.add(Triple(userId, personalScore, userInfo))
                    }
                }

                userScoreList.sortByDescending { it.second }

                val rankingData = assignRanksWithTies(userScoreList)

                withContext(Dispatchers.Main) {
                    updateRankingList(rankingData)
                    updateTopThreeRanking(rankingData)
                    updateMyRankingInfo(rankingData)
                }
            } catch (e: Exception) {
                Log.e("RankingActivity", "Error loading crew personal ranking: ", e)
            }
        }
    }

    private fun loadCrewRankingData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val crewsSnapshot = firestore.collection("Crew").get().await()
                val crewScoreList = mutableListOf<Triple<String, Int, Map<String, Any?>>>()

                for (crewDoc in crewsSnapshot.documents) {
                    val crewId = crewDoc.id
                    val crewMemberIds = getCrewMembers(crewId)

                    var totalScore = 0
                    for (memberId in crewMemberIds) {
                        // Game/users/userReward에서 각 멤버의 점수 가져오기
                        val rewardDoc = firestore.collection("Game")
                            .document("users")
                            .collection("userReward")
                            .document(memberId)
                            .get()
                            .await()

                        if (rewardDoc.exists()) {
                            val level = rewardDoc.getLong("level")?.toInt() ?: 0
                            val currentRaisingPoint = rewardDoc.getLong("currentRaisingPoint")?.toInt() ?: 0
                            totalScore += (level * 100 + currentRaisingPoint)
                        }
                    }

                    val crewInfo = mapOf(
                        "name" to crewDoc.getString("name"),
                        "profileImg" to crewDoc.getString("CrewProfile")
                    )

                    crewScoreList.add(Triple(crewId, totalScore, crewInfo))
                }

                crewScoreList.sortByDescending { it.second }

                val rankingData = assignRanksWithTies(crewScoreList)

                withContext(Dispatchers.Main) {
                    updateRankingList(rankingData)
                    updateTopThreeRanking(rankingData)
                    updateMyCrewRankingInfo()
                }
            } catch (e: Exception) {
                Log.e("RankingActivity", "Error loading crew ranking: ", e)
            }
        }
    }

    private suspend fun getCrewMembers(crewId: String): List<String> {
        return try {
            val crewMemberDoc = firestore.collection("Crew")
                .document(crewId)
                .collection("member")
                .document("members")
                .get()
                .await()

            if (crewMemberDoc.exists()) {
                crewMemberDoc.data?.keys?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("RankingActivity", "Error getting crew members: ", e)
            emptyList()
        }
    }

    private fun formatScore(score: Int): String {
        return when {
            score >= 1_000_000_000 -> {
                val value = score / 1_000_000_000.0
                String.format("%.2fB", value)
            }
            score >= 1_000_000 -> {
                val value = score / 1_000_000.0
                String.format("%.2fM", value)
            }
            score >= 1_000 -> {
                val value = score / 1_000.0
                String.format("%.2fK", value)
            }
            else -> "${score}pt"
        }
    }

    private fun assignRanksWithTies(scoreList: List<Triple<String, Int, Map<String, Any?>>>): List<RankingRecord> {
        val rankingData = mutableListOf<RankingRecord>()
        var currentRank = 1
        var previousScore: Int? = null

        for ((index, item) in scoreList.withIndex()) {
            val (id, score, info) = item

            if (previousScore != null && previousScore != score) {
                currentRank = index + 1
            }

            val nickname = info["name"] as? String ?: "Unknown"
            val profileImage = info["profileImg"] as? String

            rankingData.add(RankingRecord(
                rank = currentRank,
                userId = id,
                profileImageUrl = profileImage,
                nickname = nickname,
                record = formatScore(score)
            ))

            previousScore = score
        }

        return rankingData
    }

    private fun updateMyRankingInfo(rankingData: List<RankingRecord>) {
        val currentUserId = auth.currentUser?.uid ?: return

        val myRankingInfo = rankingData.find { rankingRecord ->
            rankingRecord.userId == currentUserId
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDoc = firestore.collection("Users").document(currentUserId).get().await()

                withContext(Dispatchers.Main) {
                    if (userDoc.exists()) {
                        val nickname = userDoc.getString("name") ?: "Unknown"
                        val profileImage = userDoc.getString("profileImg")

                        if (profileImage != null) {
                            Glide.with(this@RankingMainActivity)
                                .load(profileImage)
                                .placeholder(R.drawable.basicprofile)
                                .into(myRankProfile)
                        } else {
                            myRankProfile.setImageResource(R.drawable.basicprofile)
                        }

                        myRankName.text = nickname

                        if (myRankingInfo != null) {
                            myRankRanking.text = "${myRankingInfo.rank}"
                            myRankRecord.text = myRankingInfo.record
                        } else {
                            myRankRanking.text = "-"

                            // Game/users/userReward에서 내 점수 가져오기
                            val rewardDoc = firestore.collection("Game")
                                .document("users")
                                .collection("userReward")
                                .document(currentUserId)
                                .get()
                                .await()

                            if (rewardDoc.exists()) {
                                val level = rewardDoc.getLong("level")?.toInt() ?: 0
                                val currentRaisingPoint = rewardDoc.getLong("currentRaisingPoint")?.toInt() ?: 0
                                val personalScore = level * 100 + currentRaisingPoint
                                myRankRecord.text = formatScore(personalScore)
                            } else {
                                myRankRecord.text = "0pt"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RankingActivity", "Error getting current user info: ", e)
                withContext(Dispatchers.Main) {
                    myRankProfile.setImageResource(R.drawable.basicprofile)
                    myRankName.text = "Unknown"
                    myRankRanking.text = "-"
                    myRankRecord.text = "0pt"
                }
            }
        }
    }

    private fun updateMyRankingInfoForNoCrew() {
        myRankProfile.setImageResource(R.drawable.basicprofile)
        myRankName.text = "No Crew"
        myRankRanking.text = "-"
        myRankRecord.text = "0pt"
    }

    private fun updateMyCrewRankingInfo() {
        if (currentUserCrewId == null) {
            myRankProfile.setImageResource(R.drawable.basiccrewprofile)
            myRankName.text = "No Crew"
            myRankRanking.text = "-"
            myRankRecord.text = "0pt"
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val crewDoc = firestore.collection("Crew").document(currentUserCrewId!!).get().await()

                withContext(Dispatchers.Main) {
                    if (crewDoc.exists()) {
                        val crewName = crewDoc.getString("name") ?: "Unknown Crew"
                        val crewImage = crewDoc.getString("CrewProfile")

                        if (crewImage != null) {
                            Glide.with(this@RankingMainActivity)
                                .load(crewImage)
                                .placeholder(R.drawable.basicprofile)
                                .into(myRankProfile)
                        } else {
                            myRankProfile.setImageResource(R.drawable.basicprofile)
                        }

                        myRankName.text = crewName
                        findMyCrewRanking(currentUserCrewId!!)
                        getMyCrewRecord(currentUserCrewId!!)
                    }
                }
            } catch (e: Exception) {
                Log.e("RankingActivity", "Error getting crew info: ", e)
                withContext(Dispatchers.Main) {
                    myRankProfile.setImageResource(R.drawable.basicprofile)
                    myRankName.text = "Unknown Crew"
                    myRankRanking.text = "-"
                    myRankRecord.text = "0pt"
                }
            }
        }
    }

    private fun findMyCrewRanking(crewId: String) {
        val myCrewRanking = rankingList.find { it.userId == crewId }
        if (myCrewRanking != null) {
            myRankRanking.text = "${myCrewRanking.rank}"
        } else {
            myRankRanking.text = "-"
        }
    }

    private fun getMyCrewRecord(crewId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val crewMemberIds = getCrewMembers(crewId)
                var totalScore = 0

                for (memberId in crewMemberIds) {
                    // Game/users/userReward에서 각 멤버의 점수 가져오기
                    val rewardDoc = firestore.collection("Game")
                        .document("users")
                        .collection("userReward")
                        .document(memberId)
                        .get()
                        .await()

                    if (rewardDoc.exists()) {
                        val level = rewardDoc.getLong("level")?.toInt() ?: 0
                        val currentRaisingPoint = rewardDoc.getLong("currentRaisingPoint")?.toInt() ?: 0
                        totalScore += (level * 100 + currentRaisingPoint)
                    }
                }

                withContext(Dispatchers.Main) {
                    myRankRecord.text = formatScore(totalScore)
                }
            } catch (e: Exception) {
                Log.e("RankingActivity", "Error getting my crew record: ", e)
                withContext(Dispatchers.Main) {
                    myRankRecord.text = "0pt"
                }
            }
        }
    }

    private fun updateTopThreeRanking(rankingData: List<RankingRecord>) {
        rankingImg1.setImageResource(R.drawable.basicprofile)
        rankingImg2.setImageResource(R.drawable.basicprofile)
        rankingImg3.setImageResource(R.drawable.basicprofile)
        rankingTxt1.text = ""
        rankingTxt2.text = ""
        rankingTxt3.text = ""

        rankingImg1.visibility = android.view.View.INVISIBLE
        rankingImg2.visibility = android.view.View.INVISIBLE
        rankingImg3.visibility = android.view.View.INVISIBLE
        rankingTxt1.visibility = android.view.View.INVISIBLE
        rankingTxt2.visibility = android.view.View.INVISIBLE
        rankingTxt3.visibility = android.view.View.INVISIBLE

        if (rankingData.isNotEmpty()) {
            val first = rankingData[0]
            rankingTxt1.text = first.nickname
            rankingTxt1.visibility = android.view.View.VISIBLE
            rankingImg1.visibility = android.view.View.VISIBLE
            first.profileImageUrl?.let { imageUrl ->
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.basicprofile)
                    .into(rankingImg1)
            }
        }

        if (rankingData.size > 1) {
            val second = rankingData[1]
            rankingTxt2.text = second.nickname
            rankingTxt2.visibility = android.view.View.VISIBLE
            rankingImg2.visibility = android.view.View.VISIBLE
            second.profileImageUrl?.let { imageUrl ->
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.basicprofile)
                    .into(rankingImg2)
            }
        }

        if (rankingData.size > 2) {
            val third = rankingData[2]
            rankingTxt3.text = third.nickname
            rankingTxt3.visibility = android.view.View.VISIBLE
            rankingImg3.visibility = android.view.View.VISIBLE
            third.profileImageUrl?.let { imageUrl ->
                Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.basicprofile)
                    .into(rankingImg3)
            }
        }
    }

    private fun updateRankingList(newData: List<RankingRecord>) {
        rankingList.clear()
        rankingList.addAll(newData)
        rankingAdapter.updateData(rankingList)
    }

    private fun startCrewMemberChangeListener() {
        currentUserCrewId?.let { crewId ->
            crewMemberListener = firestore.collection("Crew")
                .document(crewId)
                .collection("member")
                .document("members")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("RankingActivity", "Error listening to crew members", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        refreshCurrentTab()
                    }
                }
        }
    }

    private fun refreshCurrentTab() {
        when (currentTabType) {
            TabType.PERSONAL -> {
                loadPersonalRankingData()
            }
            TabType.CREW_PERSONAL -> {
                loadCrewPersonalRankingData()
            }
            TabType.CREW -> {
                loadCrewRankingData()
            }
        }
    }

    private fun manualRefresh() {
        getUserCrewInfo()
        refreshCurrentTab()
    }

    override fun onDestroy() {
        super.onDestroy()
        crewTotalManager.stopAllListeners()
        crewMemberListener?.remove()
    }

    enum class TabType {
        PERSONAL,
        CREW_PERSONAL,
        CREW
    }
}