package com.SICV.plurry.raising

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import com.SICV.plurry.R
import com.SICV.plurry.ranking.RankingMainActivity
import com.unity3d.player.UnityPlayerGameActivity
import com.unity3d.player.UnityPlayer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.log

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
        }, 7000)

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

                        //Toast.makeText(this, "데이터 로드 성공", Toast.LENGTH_SHORT).show()
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

    private fun setupPopupOutsideTouchClose(popupView: View, callUnityFunction: String) {
        Log.d("LogLS", "setupPopupOutsideTouchClose called")

        // parent 영역에 터치 리스너 설정
        val parentLayout = popupView.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.parent)
        val mainLayout = popupView.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.main)

        parentLayout.setOnTouchListener { _, event ->
            Log.d("LogLS", "Touch event received on parent: ${event.action}")

            if (event.action == MotionEvent.ACTION_DOWN) {
                // main 영역인지 확인
                val mainBounds = IntArray(2)
                mainLayout.getLocationOnScreen(mainBounds)
                val mainLeft = mainBounds[0]
                val mainTop = mainBounds[1]
                val mainRight = mainLeft + mainLayout.width
                val mainBottom = mainTop + mainLayout.height

                Log.d("LogLS", "Touch coordinates: (${event.rawX}, ${event.rawY})")
                Log.d("LogLS", "Main bounds: left=$mainLeft, top=$mainTop, right=$mainRight, bottom=$mainBottom")

                // 터치 좌표가 main 영역 외부인 경우 팝업 닫기
                if (event.rawX < mainLeft || event.rawX > mainRight ||
                    event.rawY < mainTop || event.rawY > mainBottom) {
                    Log.d("LogLS", "Touched Outside Main Layout")
                    androidUIContainer.removeView(popupView)
                    isStoryDialogShowing = false
                    isDialogShowing = false // Item 팝업도 고려
                    SendMessageToUnity(callUnityFunction)
                    return@setOnTouchListener true
                } else {
                    Log.d("LogLS", "Touched Inside Main Layout")
                }
            }
            false
        }
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
    private var isStoryDialogShowing = false
    private val storyCount: Int = 5

    private fun ShowStoryPopup() {
        if (isStoryDialogShowing) return

        runOnUiThread {
            isStoryDialogShowing = true

            // 스토리 팝업 레이아웃 inflate
            val inflater = LayoutInflater.from(this)
            val popupView = inflater.inflate(R.layout.activity_raising_story, androidUIContainer, false)

            // 팝업 태그 설정
            popupView.tag = "storyPopup"

            // 스토리 팝업 요소들 설정
            setupStoryPopupElements(popupView)

            // 풀스크린으로 팝업 표시
            val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            androidUIContainer.addView(popupView, layoutParams)

            // 외부 터치로 팝업 닫기
            setupPopupOutsideTouchClose(popupView, "UnityProcessStory")
        }
    }

    private fun setupStoryPopupElements(popupView: View) {
        // 스크롤뷰와 컨테이너 찾기
        val scrollView = popupView.findViewById<ScrollView>(R.id.scrollView)
        val storyContainer = popupView.findViewById<LinearLayout>(R.id.storyContainer)

        if (storyContainer == null) {
            Log.e("Story", "storyContainer is null!")
            return
        }

        // 기존 버튼들 모두 제거 (혹시 있다면)
        storyContainer.removeAllViews()

        // 스토리 버튼들 동적 생성
        for (i in 0 until storyCount) {
            val storyButton = Button(this).apply {
                text = "스토리 ${i + 1}"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 8, 16, 8)
                }

                if (i < currentStoryLevel) {
                    setBackgroundColor(Color.BLUE)
                    setOnClickListener {
                        // 스토리 플레이 팝업 표시
                        showStoryPlayPopup(i + 1)
                    }
                } else {
                    setBackgroundColor(Color.GRAY)
                    setOnClickListener {
                        Toast.makeText(this@RaisingMainActivity, "레벨이 낮습니다!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            storyContainer.addView(storyButton)
        }
    }

    private fun showStoryPlayPopup(storyNumber: Int) {
        // 여기서 RaisingStoryPlayActivity 대신 다른 처리를 하거나
        // 필요하다면 RaisingStoryPlayActivity도 팝업으로 변환할 수 있음

        // 일단 간단하게 Toast로 처리
        Toast.makeText(this, "스토리 $storyNumber 선택됨", Toast.LENGTH_SHORT).show()

        // 또는 실제 RaisingStoryPlayActivity 실행
         val intent = Intent(this, RaisingStoryPlayActivity::class.java)
         intent.putExtra("currentStory", storyNumber)
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
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            androidUIContainer.addView(popupView, layoutParams)

            // 외부 터치로 팝업 닫기
            setupPopupOutsideTouchClose(popupView, "UnityProcessItem")
        }
    }

    private fun setupPopupElements(popupView: View) {
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

        popupTotalItemGrowingAmount = (randomMin..randomMax).random()
        currentRaisingAmount += popupTotalItemGrowingAmount
        updatePopupTextViews(popupView)
    }


/* *********
*
* PopupScript
*
* *********/

    private fun ShowScriptPopup(paramX: Float, paramY: Float) {
        runOnUiThread {
            // 기존 팝업이 있다면 제거
            val existingPopup = androidUIContainer.findViewWithTag<View>("scriptPopup")
            if (existingPopup != null) {
                androidUIContainer.removeView(existingPopup)
            }

            // 레이아웃 inflate
            val inflater = LayoutInflater.from(this)
            val popupView = inflater.inflate(R.layout.popup_raising_character_script, androidUIContainer, false)

            // 태그 설정
            popupView.tag = "scriptPopup"

            // 픽셀을 dp로 변환
            val density = resources.displayMetrics.density
            var dpX = 530
            var dpY = 980
            
            val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 좌측 상단을 기준으로 마진 설정
            layoutParams.leftMargin = dpX
            layoutParams.topMargin = dpY

            // 제약 조건 설정 (부모의 왼쪽 상단에 고정)
            layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID

            // 컨테이너에 추가
            androidUIContainer.addView(popupView, layoutParams)
            //Log.d("PopupScript", "Popup shown at margins(1): (${dpX}, ${dpY})")

            // 뷰가 그려진 후 크기를 측정해서 중앙 정렬
            popupView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    popupView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    dpX -= (popupView.width / 2).toInt()
                    dpY -= popupView.height.toInt()

                    layoutParams.leftMargin = dpX
                    layoutParams.topMargin = dpY
                    popupView.layoutParams = layoutParams

                    //Log.d("PopupScript", "Popup shown at margins(2): (${dpX}, ${dpY})")
                }
            })

            // 3초 후 자동 제거
            Handler(Looper.getMainLooper()).postDelayed({
                androidUIContainer.removeView(popupView)
            }, 3000)

        }
    }



/* ***********************************************
*
*    Unity Call
*
* *************************************************/
    //Unity Call
    private fun UnityGrowingTriggerEnter() {
        //Log.d("LogLS", "UnityGrowingTriggerEnter call")

        SetGrowingButtonVisible()
    }

    private fun UnityStoryTriggerEnter() {
        //Log.d("LogLS", "UnityStoryTriggerEnter call")

        ShowStoryPopup()
        //SendMessageToUnity( "UnityProcessStory" )
    }

    private fun UnityRankingTriggerEnter() {
        Log.d("LogLS", "UnityRankingTriggerEnter call")

        ShowRankingPopup()
        //SendMessageToUnity( "UnityProcessRanking" )
    }

    private fun UnityItemTriggerEnter() {
        //Log.d("LogLS", "UnityItemTriggerEnter call")

        ShowItemPopup()
    }

    private fun UnityGrowingTriggerExit()
    {
        Log.d("LogLS", "UnityGrowingTriggerExit call")

        SetGrowingButtonGone()
    }

    private fun UnityStoryTriggerExit()
    {
        Log.d("LogLS", "UnityStoryTriggerExit call")
    }

    private fun UnityRankingTriggerExit()
    {
        Log.d("LogLS", "UnityRankingTriggerExit call")
    }

    private fun UnityItemTriggerExit()
    {
        Log.d("LogLS", "UnityItemTriggerExit call")
    }

    private fun UnityPopUpScript(paramX : Float, paramY : Float)
    {
        Log.d("LogLS", "UnityPopUpScript call ${paramX}, ${paramY}")
        ShowScriptPopup(paramX, paramY)
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
            Log.e("LogLS", "Failed to send message: ${e.message}")
        }
    }
}