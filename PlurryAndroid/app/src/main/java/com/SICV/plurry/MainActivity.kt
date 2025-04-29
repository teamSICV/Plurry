package com.SICV.plurry

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.crewstep.CrewLineMainActivity
import com.SICV.plurry.goingwalk.GoingWalkMainActivity
import com.SICV.plurry.pointrecord.PointRecordMainActivity
import com.SICV.plurry.raising.RaisingMainActivity
import com.SICV.plurry.ranking.RankingMainActivity
import com.SICV.plurry.unity.MainUnityActivity


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
        val buttonUnity = findViewById<Button>(R.id.b_unity)

        buttonGoingWalk.setOnClickListener{
            val intent = Intent(this, GoingWalkMainActivity::class.java)
            startActivity(intent)
        }

        buttonPointRecord.setOnClickListener{
            val intent = Intent(this, PointRecordMainActivity::class.java)
            startActivity(intent)
        }

        buttonCrewLine.setOnClickListener{
            val intent = Intent(this, CrewLineMainActivity::class.java)
            startActivity(intent)
        }

        buttonRaising.setOnClickListener{
            val intent = Intent(this, RaisingMainActivity::class.java)
            startActivity(intent)
        }

        buttonRanking.setOnClickListener{
            val intent = Intent(this, RankingMainActivity::class.java)
            startActivity(intent)
        }

        buttonUnity.setOnClickListener{
            val intent = Intent(this, MainUnityActivity::class.java)
            startActivity(intent)
        }
    }
}