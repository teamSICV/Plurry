package com.SICV.plurry.crewstep

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.google.firebase.firestore.FirebaseFirestore

class CrewLineChooseActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val firestore = FirebaseFirestore.getInstance()

    private val crewList = mutableListOf<Crew>()
    private lateinit var adapter: CrewAdapter
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_line_choose)

        val makeCrewButton = findViewById<TextView>(R.id.makeCrew)
        recyclerView = findViewById(R.id.chooseCrewRecyclerView)

        val crewChooseBackBtn = findViewById<ImageView>(R.id.crewChooseBackBtn)

        adapter = CrewAdapter(crewList) { crew ->
            val intent = Intent(this, CrewLineMainActivity::class.java)
            intent.putExtra("crewId", crew.crewId)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        makeCrewButton.setOnClickListener {
            val intent = Intent(this, CrewLineMakeCrewActivity::class.java)
            startActivity(intent)
        }

        crewChooseBackBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        fetchCrewList()
    }

    override fun onResume() {
        super.onResume()
        if (!isLoading) {
            fetchCrewList()
        }
    }

    private fun fetchCrewList() {
        if (isLoading) return
        isLoading = true

        firestore.collection("Crew")
            .get()
            .addOnSuccessListener { documents ->
                crewList.clear()

                if (documents.isEmpty) {
                    adapter.notifyDataSetChanged()
                    isLoading = false
                    return@addOnSuccessListener
                }

                var pendingTasks = documents.size()

                for (doc in documents) {
                    val crewId = doc.id
                    val name = doc.getString("name") ?: ""
                    val crewProfileUrl = doc.getString("CrewProfile") ?: ""
                    val mainField = doc.getString("mainField") ?: ""

                    firestore.collection("Crew").document(crewId)
                        .collection("member")
                        .document("members")
                        .get()
                        .addOnSuccessListener { memberDoc ->
                            val memberCount = memberDoc?.data?.size ?: 0
                            val crew = Crew(
                                crewId = crewId,
                                name = name,
                                crewProfileUrl = crewProfileUrl,
                                mainField = mainField,
                                memberCount = memberCount
                            )
                            crewList.add(crew)

                            pendingTasks--
                            if (pendingTasks == 0) {
                                adapter.notifyDataSetChanged()
                                isLoading = false
                            }
                        }
                        .addOnFailureListener {
                            pendingTasks--
                            if (pendingTasks == 0) {
                                adapter.notifyDataSetChanged()
                                isLoading = false
                            }
                        }
                }
            }
            .addOnFailureListener {
                isLoading = false
            }
    }
}