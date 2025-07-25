package com.SICV.plurry.raising

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.SICV.plurry.R
import com.SICV.plurry.ranking.RankingMainActivity
import com.unity3d.player.UnityPlayerGameActivity
import com.unity3d.player.UnityPlayer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class RaisingMainActivity : UnityPlayerGameActivity() {

    private var currentRaisingPoint : Int = -1;
    private var currentRaisingAmount : Int = -1;
    private var currentStoryLevel : Int = -1;
    private var currentNormalItemAmount : Int = -1;
    private var currentCrewItemAmount : Int = -1;

    private lateinit var androidUIContainer: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val rootLayout = findViewById<android.widget.FrameLayout>(contentViewId)

            val inflater = LayoutInflater.from(this)
            androidUIContainer = inflater.inflate(R.layout.activity_raising_main, rootLayout, false) as ViewGroup

            val layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            rootLayout.addView(androidUIContainer, layoutParams)

            Log.d("MainUnityGameActivity", "Android UI overlay added successfully")
        } catch (e: Exception) {
            Log.e("MainUnityGameActivity", "Error adding Android UI overlay", e)
        }

        loadUserDataFromFirebase()

        Handler(Looper.getMainLooper()).postDelayed({
            findViewById<ImageView>(R.id.img_loading).visibility = View.GONE
        }, 5000)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.extras == null) return

        if (intent.extras!!.containsKey("doQuit")) {
            finish()
        }
    }

    override fun onUnityPlayerUnloaded() {
        super.onUnityPlayerUnloaded()
        finish()
    }


/* ******************
*
* setData
*
* ******************/

    private fun loadUserDataFromFirebase() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val db = FirebaseFirestore.getInstance()
            db.collection("Game").document("users")
                .collection("userReward").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        Log.d("FirebaseDebug", "currentRaisingPoint: ${document.get("currentRaisingPoint")}")
                        Log.d("FirebaseDebug", "필드 타입: ${document.get("currentRaisingPoint")?.javaClass?.simpleName}")

                        currentRaisingPoint = document.getLong("currentRaisingPoint")?.toInt() ?: -1
                        currentRaisingAmount = document.getLong("currentRaisingAmount")?.toInt() ?: -1
                        currentStoryLevel = document.getLong("level")?.toInt() ?: -1
                        currentNormalItemAmount = document.getLong("userRewardItem")?.toInt() ?: -1
                        //currentCrewItemAmount = document.getLong("crewRewardItem")?.toInt() ?: -1

                        Toast.makeText(this, "데이터 로드 성공", Toast.LENGTH_SHORT).show()
                        setupUIElements()
                    } else {
                        Toast.makeText(this, "사용자 데이터를 찾을 수 없습니다: $userId", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "데이터 로드 실패: $userId", Toast.LENGTH_SHORT).show()
                }

/*            if(currentRaisingPoint==-1||currentRaisingAmount==-1||currentStoryLevel==-1||currentNormalItemAmount==-1||currentCrewItemAmount==-1)
            {
                Toast.makeText(this, "데이터 로드 실패" + userId.toString(), Toast.LENGTH_SHORT).show()
            }*/
        } else {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }



/* ******************
*
* setOnClickListener
*
* ******************/
    private val handler = Handler(Looper.getMainLooper())
    private var isBtnGrowingpressed = false

    private fun setupUIElements() {

        //QuitButton
        val btnQuit = findViewById<Button>(R.id.b_quit)
        btnQuit.setOnClickListener { onUnityPlayerUnloaded() }

        //Raising
        val btnGrowing = findViewById<Button>(R.id.b_growing)
        btnGrowing.setOnTouchListener {_, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isBtnGrowingpressed = true
                    GrowingRepeat()
                }
                MotionEvent.ACTION_UP -> { isBtnGrowingpressed = false }
            }
            true
        }
        btnGrowing.visibility = View.GONE
        btnGrowing.isEnabled = false

        val txtcurrentLevel = findViewById<TextView>(R.id.t_level)
        txtcurrentLevel.text = currentStoryLevel.toString()

        val txtcurrentRaisingPoint = findViewById<TextView>(R.id.t_raisingPoint)
        txtcurrentRaisingPoint.text = currentRaisingPoint.toString()

        val txtcurrentRaisingAmount = findViewById<TextView>(R.id.t_raisingAmount)
        txtcurrentRaisingAmount.text = currentRaisingAmount.toString()
        txtcurrentRaisingAmount.visibility = View.GONE
    }


/* *********
*
* Raising
*
* *********/
    private fun SetGrowingButtonVisible() {
        runOnUiThread {
            val btnGrowing = findViewById<Button>(R.id.b_growing)
            btnGrowing.visibility = View.VISIBLE
            btnGrowing.isEnabled = true

            val txtcurrentRaisingAmount = findViewById<TextView>(R.id.t_raisingAmount)
            txtcurrentRaisingAmount.text = currentRaisingAmount.toString()
            txtcurrentRaisingAmount.visibility = View.VISIBLE
        }
    }

    private fun SetGrowingButtonGone() {
        runOnUiThread {
            val btnGrowing = findViewById<Button>(R.id.b_growing)
            btnGrowing.visibility = View.GONE
            btnGrowing.isEnabled = false

            val txtcurrentRaisingAmount = findViewById<TextView>(R.id.t_raisingAmount)
            txtcurrentRaisingAmount.visibility = View.GONE
        }
    }

    private fun ProcessGrowing() {
        val txtcurrentRaisingPoint = findViewById<TextView>(R.id.t_raisingPoint)
        val txtcurrentRaisingAmount = findViewById<TextView>(R.id.t_raisingAmount)
        val txtcurrentLevel = findViewById<TextView>(R.id.t_level)

        if(currentRaisingAmount > 0) {
            currentRaisingPoint += 1
            currentRaisingAmount -= 1
        }
        txtcurrentRaisingPoint.text = currentRaisingPoint.toString()
        txtcurrentRaisingAmount.text = currentRaisingAmount.toString()
        currentStoryLevel = currentRaisingPoint / 100
        txtcurrentLevel.text = currentStoryLevel.toString()

        //Call Unity
        SendMessageToUnity( "UnityProcessGrowing" )
    }

    private fun GrowingRepeat() {
        if(isBtnGrowingpressed) {
            ProcessGrowing()
            handler.postDelayed({ GrowingRepeat() }, 100)
        }
    }

/* *********
*
* Story
*
* *********/
    private fun ShowStoryPopup() {
        val intent = Intent(this, RaisingStoryActivity::class.java)
        intent.putExtra("currentLevel", currentStoryLevel)
        startActivity(intent)
    }

/* *********
*
* Ranking
*
* *********/
    private fun ShowRankingPopup() {
        val intent = Intent(this, RankingMainActivity::class.java)
        startActivity(intent)
    }

/* *********
*
* Item
*
* *********/
    private val itemActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            currentRaisingAmount += result.data?.getIntExtra("totalItemGrowingAmount", 0) ?: 0
            currentNormalItemAmount = result.data?.getIntExtra("currentNormalItemAmount", 0) ?: 0
            currentCrewItemAmount = result.data?.getIntExtra("currentCrewItemAmount", 0) ?: 0
        }
    }

    private fun ShowItemPopup() {
        val intent = Intent(this, RaisingItemActivity::class.java)
        intent.putExtra("currentNormalItemAmount", currentNormalItemAmount)  // Int 값 1
        intent.putExtra("currentCrewItemAmount", currentCrewItemAmount)
        itemActivityLauncher.launch(intent)
    }

/* ***********************************************
*
*    Unity Call
*
* *************************************************/
    //Unity Call
    private fun UnityGrowingTriggerEnter() {
        Log.d("UnityToAndroid", "UnityGrowingTriggerEnter call")

        SetGrowingButtonVisible()
    }

    private fun UnityStoryTriggerEnter() {
        Log.d("UnityToAndroid", "UnityStoryTriggerEnter call")

        ShowStoryPopup()
        SendMessageToUnity( "UnityProcessStory" )
    }

    private fun UnityRankingTriggerEnter() {
        Log.d("UnityToAndroid", "UnityRankingTriggerEnter call")

        ShowRankingPopup()
        SendMessageToUnity( "UnityProcessRanking" )
    }

    private fun UnityItemTriggerEnter() {
        Log.d("UnityToAndroid", "UnityItemTriggerEnter call")

        ShowItemPopup()
        SendMessageToUnity( "UnityProcessItem" )
    }

    private fun UnityGrowingTriggerExit()
    {
        Log.d("UnityToAndroid", "UnityGrowingTriggerExit call")

        SetGrowingButtonGone()
    }

    private fun UnityStoryTriggerExit()
    {
        Log.d("UnityToAndroid", "UnityStoryTriggerExit call")
    }

    private fun UnityRankingTriggerExit()
    {
        Log.d("UnityToAndroid", "UnityRankingTriggerExit call")
    }

    private fun UnityItemTriggerExit()
    {
        Log.d("UnityToAndroid", "UnityItemTriggerExit call")
    }


/* ***********************************************
*
*    Android Call
*
* *************************************************/

    private fun SendMessageToUnity( inFunctionName : String )
    {
        try {
            UnityPlayer.UnitySendMessage("GameController", inFunctionName, "")
            Log.d("AndroidToUnity", "Message sent successfully")
        } catch (e: Exception) {
            Log.e("AndroidToUnity", "Failed to send message: ${e.message}")
        }
    }
}