package com.SICV.plurry

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.crewstep.CrewLineChooseActivity
import com.SICV.plurry.crewstep.CrewLineMainActivity
import com.SICV.plurry.goingwalk.GoingWalkMainActivity
import com.SICV.plurry.login.LoginMainActivity
import com.SICV.plurry.pointrecord.PointRecordMainActivity
import com.SICV.plurry.raising.RaisingMainActivity
import com.SICV.plurry.ranking.MainCrewRankingManager
import com.SICV.plurry.ranking.MainRankingManager
import com.SICV.plurry.ranking.RankingMainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var rankingManager: MainRankingManager
    private lateinit var crewRankingManager: MainCrewRankingManager
    private lateinit var myWalkRecord: MainMyWalkRecord

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        settingButton()

        //메인-랭킹
        setupRankingManager()

        //메인-크루데이터
        val caloTextView = findViewById<TextView>(R.id.mainCrewCalo)
        val countTextView = findViewById<TextView>(R.id.mainCrewCount)
        val distanceTextView = findViewById<TextView>(R.id.mainCrewDistance)

        crewRankingManager = MainCrewRankingManager(caloTextView, countTextView, distanceTextView)
        crewRankingManager.startUpdating()

        //메인-내 항해기록
        setupMyWalkRecord()

    }

    private fun settingButton() {
        val buttonGoingWalk = findViewById<Button>(R.id.b_goingWalk)
        val buttonPointRecord = findViewById<Button>(R.id.b_pointRecord)
        val buttonCrewLine = findViewById<Button>(R.id.b_crewLine)
        val buttonRaising = findViewById<Button>(R.id.b_raising)
        val buttonLogout = findViewById<Button>(R.id.btnLogout)

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

        buttonLogout.setOnClickListener{
            signOut()
        }
    }

    //메인-로그인, 로그아웃
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

    private fun signOut(){
        auth.signOut()

        googleSignInClient.signOut().addOnCompleteListener(this) {task ->
            if(task.isSuccessful){
                Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
                goToLogin()
            }else{
                Log.e("Logout", "Google 로그아웃 실패", task.exception)
                goToLogin()
            }
        }
    }

    private fun goToLogin(){
        val intent = Intent(this, LoginMainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    //메인-랭킹
    private fun setupRankingManager() {
        val rankingLinearLayout = findViewById<LinearLayout>(R.id.mainRankLinearLayout)
        val titleTextView = rankingLinearLayout.getChildAt(0) as TextView
        val valueTextView = rankingLinearLayout.getChildAt(1) as TextView
        val unitTextView = rankingLinearLayout.getChildAt(2) as TextView

        val leftArrow = findViewById<ImageView>(R.id.btnPre)
        val rightArrow = findViewById<ImageView>(R.id.btnNext)

        rankingManager = MainRankingManager(
            titleTextView, valueTextView, unitTextView, leftArrow, rightArrow
        )

        rankingManager.initialize()
    }

    //메인-내 항해기록
    private fun setupMyWalkRecord() {
        val caloTextView = findViewById<TextView>(R.id.mainWalkCalo)
        val distanceTextView = findViewById<TextView>(R.id.mainWalkDistance)
        val countTextView = findViewById<TextView>(R.id.mainWalkCount)

        myWalkRecord = MainMyWalkRecord(caloTextView, distanceTextView, countTextView)
        myWalkRecord.startUpdating()
    }

    override fun onResume() {
        super.onResume()
        crewRankingManager.startUpdating()
        myWalkRecord.startUpdating()
    }

    override fun onPause() {
        super.onPause()
        crewRankingManager.stopUpdating()
        myWalkRecord.stopUpdating()
    }

    override fun onDestroy() {
        super.onDestroy()
        rankingManager.cleanup()
        crewRankingManager.cleanup()
        myWalkRecord.cleanup()
    }
}