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
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.SICV.plurry.R
import com.SICV.plurry.ranking.RankingMainActivity
import com.unity3d.player.UnityPlayerGameActivity
import com.unity3d.player.UnityPlayer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

enum class BoxId {
    NORMAL,
    CREW,
}

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
                        //Log.d("FirebaseDebug", "currentRaisingPoint: ${document.get("currentRaisingPoint")}")
                        //Log.d("FirebaseDebug", "필드 타입: ${document.get("currentRaisingPoint")?.javaClass?.simpleName}")

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

            // 컨테이너에 터치 리스너 추가 (버튼 외의 영역 터치 감지)
            androidUIContainer.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    // 버튼 영역인지 확인
                    val buttonBounds = IntArray(2)
                    btnGrowing.getLocationOnScreen(buttonBounds)
                    val buttonLeft = buttonBounds[0]
                    val buttonTop = buttonBounds[1]
                    val buttonRight = buttonLeft + btnGrowing.width
                    val buttonBottom = buttonTop + btnGrowing.height

                    // 터치 좌표가 버튼 외부인 경우
                    if (event.rawX < buttonLeft || event.rawX > buttonRight ||
                        event.rawY < buttonTop || event.rawY > buttonBottom) {
                        SetGrowingButtonGone()
                    }
                }
                false // 이벤트를 다른 뷰로 전달
            }
        }
    }

    private fun SetGrowingButtonGone() {
        runOnUiThread {
            val btnGrowing = findViewById<Button>(R.id.b_growing)
            btnGrowing.visibility = View.GONE
            btnGrowing.isEnabled = false

            val txtcurrentRaisingAmount = findViewById<TextView>(R.id.t_raisingAmount)
            txtcurrentRaisingAmount.visibility = View.GONE

            // 터치 리스너 제거
            androidUIContainer.setOnTouchListener(null)
        }
        //Call Unity
        SendMessageToUnity( "UnityProcessGrowing" )
    }

    private fun ProcessGrowing() {
        val txtcurrentRaisingPoint = findViewById<TextView>(R.id.t_raisingPoint)
        val txtcurrentRaisingAmount = findViewById<TextView>(R.id.t_raisingAmount)
        val txtcurrentLevel = findViewById<TextView>(R.id.t_level)
        val gageRaisingPoint = findViewById<View>(R.id.g_raise)

        if(currentRaisingAmount > 0) {
            currentRaisingPoint += 1
            currentRaisingAmount -= 1

            animateCoin()
        }
        txtcurrentRaisingPoint.text = (currentRaisingPoint % 100).toString()
        txtcurrentRaisingAmount.text = currentRaisingAmount.toString()
        currentStoryLevel = currentRaisingPoint / 100
        txtcurrentLevel.text = currentStoryLevel.toString()
        val density = resources.displayMetrics.density
        var gWidthPx = (((currentRaisingPoint % 100) / 100.0) * 250 * density).toInt()
        if(gWidthPx==0) {
            gWidthPx=1
        }
        gageRaisingPoint.layoutParams.width = gWidthPx
        gageRaisingPoint.requestLayout()
    }

    private fun GrowingRepeat() {
        if(isBtnGrowingpressed) {
            ProcessGrowing()
            handler.postDelayed({ GrowingRepeat() }, 100)
        }
    }

    private fun animateCoin() {
        val btnGrowing = findViewById<Button>(R.id.b_growing)
        val txtRaisingPoint = findViewById<TextView>(R.id.t_raisingPoint)

        val imgCoin = ImageView(this).apply {
            setImageResource(R.drawable.img_icon_coin)
            layoutParams = ViewGroup.LayoutParams(80, 80)
            x = btnGrowing.x
            y = btnGrowing.y
            visibility = View.VISIBLE
        }

        androidUIContainer.addView(imgCoin)

        imgCoin.animate()
            .translationX(txtRaisingPoint.x - btnGrowing.x)
            .translationY(txtRaisingPoint.y - btnGrowing.y)
            .setDuration(500)
            .withEndAction { androidUIContainer.removeView(imgCoin) }
            .start()
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
    private var isDialogShowing = false
    private var popupTotalItemGrowingAmount = 0

    fun onItemDialogResult(totalItemGrowingAmount: Int, currentNormalItemAmount: Int, currentCrewItemAmount: Int) {
        this.currentRaisingAmount += totalItemGrowingAmount
        this.currentNormalItemAmount = currentNormalItemAmount
        this.currentCrewItemAmount = currentCrewItemAmount
    }

    private fun ShowItemPopup() {
        if (isDialogShowing) return

        runOnUiThread {
            isDialogShowing = true
            popupTotalItemGrowingAmount = 0

            // DialogFragment 대신 직접 View를 overlay로 추가
            val inflater = LayoutInflater.from(this)
            val popupView = inflater.inflate(R.layout.activity_raising_item, androidUIContainer, false)

            // 팝업 이벤트 처리
            setupPopupElements(popupView)

            // ConstraintLayout에서 하단 정렬을 위한 LayoutParams
            val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID

            androidUIContainer.addView(popupView, layoutParams)

        }
    }

    private fun setupPopupElements(popupView: View) {
        val btnQuit = popupView.findViewById<Button>(R.id.b_quit)
        btnQuit.setOnClickListener {
            currentRaisingAmount += popupTotalItemGrowingAmount
            androidUIContainer.removeView(popupView)
            isDialogShowing = false
            SendMessageToUnity( "UnityProcessItem" )
        }

        val btnNormalItem = popupView.findViewById<Button>(R.id.b_normal)
        btnNormalItem.setOnClickListener {
            openItemBoxInPopup(BoxId.NORMAL, popupView)
        }

        val btnCrewItem = popupView.findViewById<Button>(R.id.b_crew)
        btnCrewItem.setOnClickListener {
            openItemBoxInPopup(BoxId.CREW, popupView)
        }

        updatePopupTextViews(popupView)
    }

    private fun updatePopupTextViews(popupView: View) {
        val tvNormalItem = popupView.findViewById<TextView>(R.id.tv_normal)
        tvNormalItem.text = currentNormalItemAmount.toString()

        val tvCrewlItem = popupView.findViewById<TextView>(R.id.tv_crew)
        tvCrewlItem.text = currentCrewItemAmount.toString()

        val tvGetGrowingAmount = popupView.findViewById<TextView>(R.id.tv_openamount)
        tvGetGrowingAmount.text = popupTotalItemGrowingAmount.toString()
    }

    private fun openItemBoxInPopup(boxId: BoxId, popupView: View) {
        val randomMin: Int
        val randomMax: Int

        if (boxId == BoxId.NORMAL) {
            if (currentNormalItemAmount > 0) {
                randomMin = 0
                randomMax = 100
                currentNormalItemAmount--
            } else return
        } else {
            if (currentCrewItemAmount > 0) {
                randomMin = 50
                randomMax = 200
                currentCrewItemAmount--
            } else return
        }

        popupTotalItemGrowingAmount += (randomMin..randomMax).random()
        updatePopupTextViews(popupView)
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
        //SendMessageToUnity( "UnityProcessStory" )
    }

    private fun UnityRankingTriggerEnter() {
        Log.d("UnityToAndroid", "UnityRankingTriggerEnter call")

        ShowRankingPopup()
        //SendMessageToUnity( "UnityProcessRanking" )
    }

    private fun UnityItemTriggerEnter() {
        Log.d("UnityToAndroid", "UnityItemTriggerEnter call")

        ShowItemPopup()
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
            //Log.d("AndroidToUnity", "Message sent successfully")
        } catch (e: Exception) {
            Log.e("AndroidToUnity", "Failed to send message: ${e.message}")
        }
    }
}