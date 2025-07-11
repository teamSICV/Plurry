package com.SICV.plurry

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.crewstep.CrewLineChooseActivity
import com.SICV.plurry.crewstep.CrewLineMainActivity
import com.SICV.plurry.goingwalk.GoingWalkMainActivity
import com.SICV.plurry.pointrecord.PointRecordMainActivity
import com.SICV.plurry.raising.RaisingMainActivity
import com.SICV.plurry.ranking.RankingMainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settingButton()
    }

    private fun settingButton() {
        val buttonGoingWalk = findViewById<Button>(R.id.b_goingWalk)
        val buttonPointRecord = findViewById<Button>(R.id.b_pointRecord)
        val buttonCrewLine = findViewById<Button>(R.id.b_crewLine)
        val buttonRaising = findViewById<Button>(R.id.b_raising)
        val buttonRanking = findViewById<Button>(R.id.b_ranking)

        buttonGoingWalk.setOnClickListener{
            val intent = Intent(this, GoingWalkMainActivity::class.java)
            startActivity(intent)
        }

        buttonPointRecord.setOnClickListener{
            val intent = Intent(this, PointRecordMainActivity::class.java)
            startActivity(intent)
        }

        buttonCrewLine.setOnClickListener {
            checkUserCrewStatus()
        }

        buttonRaising.setOnClickListener{
            val intent = Intent(this, RaisingMainActivity::class.java)
            startActivity(intent)
        }

        buttonRanking.setOnClickListener{
            val intent = Intent(this, RankingMainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkUserCrewStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            val intent = Intent(this, CrewLineChooseActivity::class.java)
            startActivity(intent)
            return
        }

        val uid = currentUser.uid
        val db = FirebaseFirestore.getInstance()

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val crewAt = userDoc.getString("crewAt")

                    if (!crewAt.isNullOrEmpty()) {
                        val intent = Intent(this, CrewLineMainActivity::class.java)
                        intent.putExtra("crewId", crewAt)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, CrewLineChooseActivity::class.java)
                        startActivity(intent)
                    }
                } else {
                    val intent = Intent(this, CrewLineChooseActivity::class.java)
                    startActivity(intent)
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewCheck", "사용자 정보 확인 실패", e)
                val intent = Intent(this, CrewLineChooseActivity::class.java)
                startActivity(intent)
            }
    }
}