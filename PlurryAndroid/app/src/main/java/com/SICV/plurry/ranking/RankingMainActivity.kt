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
        firestore.collection("Game")
            .document("users")
            .collection("userReward")
            .get()
            .addOnSuccessListener { documents ->
                val userRewardList = mutableListOf<Pair<String, Int>>()

                for (document in documents) {
                    val userId = document.id
                    val userRewardItem = document.getLong("userRewardItem")?.toInt() ?: 0
                    userRewardList.add(Pair(userId, userRewardItem))
                }

                userRewardList.sortByDescending { it.second }

                getUserInfoForRanking(userRewardList) { rankingData ->
                    updateRankingList(rankingData)
                    updateTopThreeRanking(rankingData)
                    updateMyRankingInfo(rankingData)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("RankingActivity", "Error loading personal ranking: ", exception)
            }
    }

    private fun loadCrewPersonalRankingData() {
        // 크루에 속해있지 않으면 빈 데이터 처리
        if (currentUserCrewId == null) {
            updateRankingList(emptyList())
            updateTopThreeRanking(emptyList())
            updateMyRankingInfoForNoCrew()
            return
        }

        firestore.collection("Users")
            .whereEqualTo("crewAt", currentUserCrewId)
            .get()
            .addOnSuccessListener { crewMembers ->
                val crewMemberIds = crewMembers.documents.map { it.id }

                firestore.collection("Game")
                    .document("users")
                    .collection("userReward")
                    .get()
                    .addOnSuccessListener { documents ->
                        val crewRewardList = mutableListOf<Pair<String, Int>>()

                        for (document in documents) {
                            val userId = document.id
                            if (userId in crewMemberIds) {
                                val crewRewardItem = document.getLong("crewRewardItem")?.toInt() ?: 0
                                crewRewardList.add(Pair(userId, crewRewardItem))
                            }
                        }

                        crewRewardList.sortByDescending { it.second }

                        getUserInfoForRanking(crewRewardList) { rankingData ->
                            updateRankingList(rankingData)
                            updateTopThreeRanking(rankingData)
                            updateMyRankingInfo(rankingData)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("RankingActivity", "Error loading crew personal ranking: ", exception)
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("RankingActivity", "Error getting crew members: ", exception)
            }
    }

    private fun loadCrewRankingData() {
        firestore.collection("Game")
            .document("crew")
            .collection("crewReward")
            .get()
            .addOnSuccessListener { documents ->
                val crewRewardList = mutableListOf<Pair<String, Int>>()

                if (documents.isEmpty()) {
                    updateRankingList(emptyList())
                    updateTopThreeRanking(emptyList())
                    updateMyCrewRankingInfo()
                    return@addOnSuccessListener
                }

                for (document in documents) {
                    val crewId = document.id
                    val crewRewardItem = document.getLong("crewRewardItem")?.toInt() ?: 0
                    crewRewardList.add(Pair(crewId, crewRewardItem))
                }

                crewRewardList.sortByDescending { it.second }

                getCrewInfoForRanking(crewRewardList) { rankingData ->
                    updateRankingList(rankingData)
                    updateTopThreeRanking(rankingData)
                    updateMyCrewRankingInfo()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("RankingActivity", "Error loading crew ranking: ", exception)
            }
    }

    private fun getUserInfoForRanking(
        userRewardList: List<Pair<String, Int>>,
        callback: (List<RankingRecord>) -> Unit
    ) {
        val userInfoList = mutableListOf<RankingRecord>()
        var processedCount = 0

        if (userRewardList.isEmpty()) {
            callback(userInfoList)
            return
        }

        for ((index, userRewardPair) in userRewardList.withIndex()) {
            val (userId, reward) = userRewardPair
            firestore.collection("Users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val nickname = document.getString("name") ?: "Unknown"
                        val profileImage = document.getString("profileImg")
                        userInfoList.add(RankingRecord(
                            rank = index + 1,
                            userId = userId,
                            profileImageUrl = profileImage,
                            nickname = nickname,
                            record = "${reward}pt"
                        ))
                    }

                    processedCount++
                    if (processedCount == userRewardList.size) {
                        callback(userInfoList.sortedBy { it.rank })
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("RankingActivity", "Error getting user info for $userId: ", exception)
                    processedCount++
                    if (processedCount == userRewardList.size) {
                        callback(userInfoList.sortedBy { it.rank })
                    }
                }
        }
    }

    private fun getCrewInfoForRanking(
        crewRewardList: List<Pair<String, Int>>,
        callback: (List<RankingRecord>) -> Unit
    ) {
        val crewInfoList = mutableListOf<RankingRecord>()
        var processedCount = 0

        if (crewRewardList.isEmpty()) {
            callback(crewInfoList)
            return
        }

        for ((index, crewRewardPair) in crewRewardList.withIndex()) {
            val (crewId, reward) = crewRewardPair

            firestore.collection("Crew")
                .document(crewId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val crewName = document.getString("name") ?: "Unknown Crew"
                        val crewImage = document.getString("CrewProfile")

                        crewInfoList.add(RankingRecord(
                            rank = index + 1,
                            userId = crewId,
                            profileImageUrl = crewImage,
                            nickname = crewName,
                            record = "${reward}pt"
                        ))
                    }

                    processedCount++
                    if (processedCount == crewRewardList.size) {
                        callback(crewInfoList.sortedBy { it.rank })
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("RankingActivity", "Error getting crew info for $crewId: ", exception)
                    processedCount++
                    if (processedCount == crewRewardList.size) {
                        callback(crewInfoList.sortedBy { it.rank })
                    }
                }
        }
    }

    private fun updateMyRankingInfo(rankingData: List<RankingRecord>) {
        val currentUserId = auth.currentUser?.uid ?: return

        val myRankingInfo = rankingData.find { rankingRecord ->
            rankingRecord.userId == currentUserId
        }

        firestore.collection("Users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val nickname = document.getString("name") ?: "Unknown"
                    val profileImage = document.getString("profileImg")

                    if (profileImage != null) {
                        Glide.with(this)
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
                        getMyRecord(currentUserId)
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("RankingActivity", "Error getting current user info: ", exception)
                myRankProfile.setImageResource(R.drawable.basicprofile)
                myRankName.text = "Unknown"
                myRankRanking.text = "-"
                myRankRecord.text = "0pt"
            }
    }

    // 크루가 없을 때 크루 내 개인 탭에서 사용할 내 랭킹 정보 업데이트
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

        firestore.collection("Crew")
            .document(currentUserCrewId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val crewName = document.getString("name") ?: "Unknown Crew"
                    val crewImage = document.getString("CrewProfile")

                    if (crewImage != null) {
                        Glide.with(this)
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
            .addOnFailureListener { exception ->
                Log.e("RankingActivity", "Error getting crew info: ", exception)
                myRankProfile.setImageResource(R.drawable.basicprofile)
                myRankName.text = "Unknown Crew"
                myRankRanking.text = "-"
                myRankRecord.text = "0pt"
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

    private fun getMyRecord(userId: String) {
        val rewardField = when (currentTabType) {
            TabType.PERSONAL -> "userRewardItem"
            TabType.CREW_PERSONAL -> "crewRewardItem"
            TabType.CREW -> return
        }

        firestore.collection("Game")
            .document("users")
            .collection("userReward")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val record = document.getLong(rewardField)?.toInt() ?: 0
                    myRankRecord.text = "${record}pt"
                } else {
                    myRankRecord.text = "0pt"
                }
            }
            .addOnFailureListener { exception ->
                Log.e("RankingActivity", "Error getting my record: ", exception)
                myRankRecord.text = "0pt"
            }
    }

    private fun getMyCrewRecord(crewId: String) {
        firestore.collection("Game")
            .document("crew")
            .collection("crewReward")
            .document(crewId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val record = document.getLong("crewRewardItem")?.toInt() ?: 0
                    myRankRecord.text = "${record}pt"
                } else {
                    myRankRecord.text = "0pt"
                }
            }
            .addOnFailureListener { exception ->
                Log.e("RankingActivity", "Error getting my crew record: ", exception)
                myRankRecord.text = "0pt"
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

        // 랭킹 데이터가 있을 때만 표시
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
            }
            TabType.CREW_PERSONAL -> {
                loadCrewPersonalRankingData()
            }
            TabType.CREW -> {
                currentUserCrewId?.let { crewId ->
                    crewTotalManager.manualRecalculateCrewScore(crewId)
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loadCrewRankingData()
                }, 1000)
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