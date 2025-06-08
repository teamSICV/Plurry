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
import androidx.activity.result.contract.ActivityResultContracts
import com.SICV.plurry.R
import com.SICV.plurry.ranking.RankingMainActivity
import com.unity3d.player.UnityPlayerGameActivity
import com.unity3d.player.UnityPlayer

class RaisingMainActivity : UnityPlayerGameActivity() {

    private var currentRaisingPoint : Int = 0;
    public var currentRaisingAmount : Int = 100;
    private var currentStoryLevel : Int = 0;
    private var currentNormalItemAmount : Int = 3;
    private var currentCrewItemAmount : Int = 3;

    private lateinit var androidUIContainer: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // 루트 레이아웃 가져오기 (이것은 FrameLayout일 것입니다)
            val rootLayout = findViewById<android.widget.FrameLayout>(contentViewId)

            // 투명 레이아웃 준비
            val inflater = LayoutInflater.from(this)
            androidUIContainer = inflater.inflate(R.layout.activity_raising_main, rootLayout, false) as ViewGroup

            // 레이아웃 파라미터 설정
            val layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // 투명 레이아웃을 Unity 뷰 위에 추가
            rootLayout.addView(androidUIContainer, layoutParams)

            // UI 요소들의 이벤트 리스너 설정
            setupUIElements()

            Log.d("MainUnityGameActivity", "Android UI overlay added successfully")
        } catch (e: Exception) {
            Log.e("MainUnityGameActivity", "Error adding Android UI overlay", e)
        }

        // Intent에서 전달된 데이터 처리
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.extras == null) return

        // 종료 요청 처리
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

        if(currentRaisingAmount > 0) {
            currentRaisingPoint += 1
            currentRaisingAmount -= 1
        }
        txtcurrentRaisingPoint.text = currentRaisingPoint.toString()
        txtcurrentRaisingAmount.text = currentRaisingAmount.toString()

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