package com.SICV.plurry.ranking

import com.SICV.plurry.ranking.RankingAdapter
import com.SICV.plurry.ranking.RankingRecord
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R

class RankingMainActivity : AppCompatActivity() {

    private lateinit var rankingRecyclerview: RecyclerView
    private lateinit var rankingAdapter: RankingAdapter
    private val rankingList = mutableListOf<RankingRecord>()

    private lateinit var rankingMe: TextView
    private lateinit var rankingCrewMe: TextView
    private lateinit var rankingCrew: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ranking_main)

        val rankingBackBtn = findViewById<ImageView>(R.id.rankingBackBtn)

        initViews()
        setupRecyclerView()
        setupTabClickListeners()

        selectTab(TabType.PERSONAL)

        rankingBackBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun initViews() {
        rankingRecyclerview = findViewById(R.id.rankingRecyclerview)
        rankingMe = findViewById(R.id.rankingMe)
        rankingCrewMe = findViewById(R.id.rankingCrewMe)
        rankingCrew = findViewById(R.id.rankingCrew)
    }

    private fun setupRecyclerView() {
        rankingAdapter = RankingAdapter(this, rankingList)

        val layoutManager = LinearLayoutManager(this)
        rankingRecyclerview.layoutManager = layoutManager
        rankingRecyclerview.adapter = rankingAdapter
    }

    private fun setupTabClickListeners() {
        rankingMe.setOnClickListener {
            selectTab(TabType.PERSONAL)
        }

        rankingCrewMe.setOnClickListener {
            selectTab(TabType.CREW_PERSONAL)
        }

        rankingCrew.setOnClickListener {
            selectTab(TabType.CREW)
        }
    }

    private fun selectTab(tabType: TabType) {
        resetTabColors()

        when (tabType) {
            TabType.PERSONAL -> {
                rankingMe.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                loadPersonalRankingData()
            }
            TabType.CREW_PERSONAL -> {
                rankingCrewMe.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                loadCrewPersonalRankingData()
            }
            TabType.CREW -> {
                rankingCrew.setTextColor(ContextCompat.getColor(this, android.R.color.black))
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
        val personalData = listOf(
            RankingRecord(4, null, "사용자1", "Lv. 1"),
            RankingRecord(5, null, "사용자2", "Lv. 1"),
            RankingRecord(6, null, "사용자3", "Lv. 1"),
            RankingRecord(7, null, "사용자4", "Lv. 1"),
            RankingRecord(8, null, "사용자5", "Lv. 0")
        )

        updateRankingList(personalData)
    }

    private fun loadCrewPersonalRankingData() {
        val crewPersonalData = listOf(
            RankingRecord(4, null, "크루원A", "Lv. 3"),
            RankingRecord(5, null, "크루원B", "Lv. 2"),
            RankingRecord(6, null, "크루원C", "Lv. 2"),
            RankingRecord(7, null, "크루원D", "Lv. 1"),
            RankingRecord(8, null, "크루원E", "Lv. 1")
        )

        updateRankingList(crewPersonalData)
    }

    private fun loadCrewRankingData() {
        val crewData = listOf(
            RankingRecord(4, null, "크루A", "100pt"),
            RankingRecord(5, null, "크루B", "85pt"),
            RankingRecord(6, null, "크루C", "72pt"),
            RankingRecord(7, null, "크루D", "58pt"),
            RankingRecord(8, null, "크루E", "45pt")
        )

        updateRankingList(crewData)
    }

    private fun updateRankingList(newData: List<RankingRecord>) {
        rankingList.clear()
        rankingList.addAll(newData)
        rankingAdapter.updateData(rankingList)
    }

    enum class TabType {
        PERSONAL,
        CREW_PERSONAL,
        CREW
    }
}