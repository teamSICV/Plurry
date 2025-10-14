package com.SICV.plurry.raising

import LogLS
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
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.SICV.plurry.MainActivity
import com.SICV.plurry.R
import com.SICV.plurry.ranking.RankingMainActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

enum class BoxId {
    NORMAL,
    CREW,
}

class RaisingMainFragment : Fragment() {

    private var currentRaisingPoint : Int = -1;
    private var currentRaisingAmount : Int = -1;
    private var currentStoryLevel : Int = -1;
    private var currentNormalItemAmount : Int = -1;
    private var currentCrewItemAmount : Int = -1;

    private var showingDialogName:String = ""
    private lateinit var androidUIContainer: ViewGroup
    private lateinit var rankingActivityLauncher: ActivityResultLauncher<Intent>


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //LogLS.d("Begin")
        return inflater.inflate(R.layout.fragment_raising_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //LogLS.d("Begin")

        androidUIContainer = view as ViewGroup

        rankingActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // RankingMainActivity에서 돌아왔을 때 실행
            SendMessageToUnity("UnityProcessRanking")
        }

        loadUserDataFromFirebase()

        Handler(Looper.getMainLooper()).postDelayed({
            view.findViewById<ImageView>(R.id.img_loading)?.visibility = View.GONE
        }, 7000)
    }


/* ******************
*
* setData
*
* ******************/

    private fun loadUserDataFromFirebase() {
        //LogLS.d("Begin")
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            //LogLS.d("userID = ${userId}")
            val db = FirebaseFirestore.getInstance()
            db.collection("Game").document("users")
                .collection("userReward").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {

                        currentRaisingPoint = document.getLong("currentRaisingPoint")?.toInt() ?: -1
                        currentRaisingAmount = document.getLong("currentRaisingAmount")?.toInt() ?: -1
                        currentStoryLevel = document.getLong("level")?.toInt() ?: -1
                        currentNormalItemAmount = document.getLong("userRewardItem")?.toInt() ?: -1
                        currentCrewItemAmount = document.getLong("crewRewardItem")?.toInt() ?: -1

                        //Toast.makeText(requireContext(), "데이터 로드 성공", Toast.LENGTH_SHORT).show()
                        /*
                        LogLS.d("currentRaisingPoint = ${currentRaisingPoint}")
                        LogLS.d("currentRaisingAmount = ${currentRaisingAmount}")
                        LogLS.d("currentStoryLevel = ${currentStoryLevel}")
                        LogLS.d("currentNormalItemAmount = ${currentNormalItemAmount}")
                        LogLS.d("currentCrewItemAmount = ${currentCrewItemAmount}")
                        */
                        setupUIElements()
                    } else {
                        Toast.makeText(requireContext(), "사용자 데이터를 찾을 수 없습니다: $userId", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "데이터 로드 실패: $userId", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }


    private fun sendUserDataToFirebase() {
        //LogLS.d("Begin")
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            //LogLS.d("userID = ${userId}")
            val db = FirebaseFirestore.getInstance()

            val updates = hashMapOf<String, Any>(
                "currentRaisingPoint" to currentRaisingPoint,
                "currentRaisingAmount" to currentRaisingAmount,
                "level" to currentStoryLevel,
                "userRewardItem" to currentNormalItemAmount,
                "crewRewardItem" to currentCrewItemAmount
            )

            db.collection("Game").document("users")
                .collection("userReward").document(userId)
                .update(updates)
                .addOnSuccessListener {
                    LogLS.d("데이터 업데이트 성공")
                }
                .addOnFailureListener { e ->
                    LogLS.e("데이터 업데이트 실패: ${e.message}")
                }
        } else {
            Toast.makeText(requireContext(), "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }



/* ******************
*
* setupUIElements, setOnTouchListener
*
* ******************/
    private val handler = Handler(Looper.getMainLooper())
    private var isBtnGrowingpressed = false

    private fun setupUIElements() {

        //LogLS.d("Begin")

        //QuitButton
        val btnQuit = view?.findViewById<Button>(R.id.b_quit)
        btnQuit?.setOnClickListener {

            if(showingDialogName != "") {
                when(showingDialogName) {
                    "Growing"->{
                        SetGrowingButtonGone()
                    }
                    "Story"-> {
                        val popupView = androidUIContainer.findViewWithTag<View>("storyPopup")
                        popupView?.let {
                            setupPopupOutsideTouchClose(popupView, "UnityProcessStory")
                        }
                    }
                    "Item"-> {
                        val popupView = androidUIContainer.findViewWithTag<View>("itemPopup")
                        popupView?.let {
                            setupPopupOutsideTouchClose(popupView, "UnityProcessItem")
                        }
                    }

                }
            }
            else {
                sendUserDataToFirebase()
                if (activity is MainActivity) {
                    (activity as MainActivity).loadFragment("HOME")
                }
            }
        }

        //Raising
        val btnGrowing = view?.findViewById<Button>(R.id.b_growing)
        btnGrowing?.setOnTouchListener {_, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isBtnGrowingpressed = true
                    GrowingRepeat()
                }
                MotionEvent.ACTION_UP -> { isBtnGrowingpressed = false }
            }
            true
        }
        btnGrowing?.visibility = View.GONE
        btnGrowing?.isEnabled = false

        val txtcurrentLevel = view?.findViewById<TextView>(R.id.t_level)
        txtcurrentLevel?.text = currentStoryLevel.toString()

        val txtcurrentRaisingPoint = view?.findViewById<TextView>(R.id.t_raisingPoint)
        txtcurrentRaisingPoint?.text = currentRaisingPoint.toString()

        val txtcurrentRaisingAmount = view?.findViewById<TextView>(R.id.t_raisingAmount)
        txtcurrentRaisingAmount?.text = currentRaisingAmount.toString()
        txtcurrentRaisingAmount?.visibility = View.GONE

        //setting gage
        setupRaisingGage()
    }

    private fun setupPopupOutsideTouchClose(popupView: View, callUnityFunction: String) {

        //LogLS.d("Begin")

        showingDialogName = ""

        // parent 영역에 터치 리스너 설정
        val parentLayout = popupView.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.parent)
        val mainLayout = popupView.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.main)

        parentLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // main 영역인지 확인
                val mainBounds = IntArray(2)
                mainLayout.getLocationOnScreen(mainBounds)
                val mainLeft = mainBounds[0]
                val mainTop = mainBounds[1]
                val mainRight = mainLeft + mainLayout.width
                val mainBottom = mainTop + mainLayout.height

                // 터치 좌표가 main 영역 외부인 경우 팝업 닫기
                if (event.rawX < mainLeft || event.rawX > mainRight ||
                    event.rawY < mainTop || event.rawY > mainBottom) {
                    androidUIContainer.removeView(popupView)
                    SendMessageToUnity(callUnityFunction)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun setupRaisingGage() {
        val gageRaisingPoint = view?.findViewById<View>(R.id.g_raise)

        if(currentRaisingPoint<=5) {
            gageRaisingPoint?.visibility = View.INVISIBLE
        }
        else if (currentRaisingPoint>=97) {
            gageRaisingPoint?.visibility = View.VISIBLE
            val density = resources.displayMetrics.density
            var gWidthPx = (236 * density).toInt()
            if(gWidthPx==0) {
                gWidthPx=1
            }
            gageRaisingPoint?.layoutParams?.width = gWidthPx
            gageRaisingPoint?.requestLayout()
        }
        else {
            gageRaisingPoint?.visibility = View.VISIBLE
            val density = resources.displayMetrics.density
            var gWidthPx = ((currentRaisingPoint / 100.0) * 240 * density).toInt()
            if(gWidthPx==0) {
                gWidthPx=1
            }
            gageRaisingPoint?.layoutParams?.width = gWidthPx
            gageRaisingPoint?.requestLayout()
        }
    }


/* *********
*
* Raising
*
* *********/
    private fun SetGrowingButtonVisible() {
        //LogLS.d("Begin")

        showingDialogName = "Growing"

        activity?.runOnUiThread {
            val btnGrowing = view?.findViewById<Button>(R.id.b_growing)
            btnGrowing?.visibility = View.VISIBLE
            btnGrowing?.isEnabled = true

            val txtcurrentRaisingAmount = view?.findViewById<TextView>(R.id.t_raisingAmount)
            txtcurrentRaisingAmount?.text = currentRaisingAmount.toString()
            txtcurrentRaisingAmount?.visibility = View.VISIBLE

            // 컨테이너에 터치 리스너 추가 (버튼 외의 영역 터치 감지)
            androidUIContainer.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    // 버튼 영역인지 확인
                    val buttonBounds = IntArray(2)
                    btnGrowing?.getLocationOnScreen(buttonBounds)
                    val buttonLeft = buttonBounds[0]
                    val buttonTop = buttonBounds[1]
                    val buttonRight = buttonLeft + (btnGrowing?.width ?: 0)
                    val buttonBottom = buttonTop + (btnGrowing?.height ?: 0)

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
        //LogLS.d("Begin")

        showingDialogName = ""

        activity?.runOnUiThread {
            val btnGrowing = view?.findViewById<Button>(R.id.b_growing)
            btnGrowing?.visibility = View.GONE
            btnGrowing?.isEnabled = false

            val txtcurrentRaisingAmount = view?.findViewById<TextView>(R.id.t_raisingAmount)
            txtcurrentRaisingAmount?.visibility = View.GONE

            // 터치 리스너 제거
            androidUIContainer.setOnTouchListener(null)
        }

        //Call Unity
        SendMessageToUnity( "UnityProcessGrowing" )
    }

    private fun ProcessGrowing() {
        //LogLS.d("Begin")
        val txtcurrentRaisingPoint = view?.findViewById<TextView>(R.id.t_raisingPoint)
        val txtcurrentRaisingAmount = view?.findViewById<TextView>(R.id.t_raisingAmount)
        val txtcurrentLevel = view?.findViewById<TextView>(R.id.t_level)
        val gageRaisingPoint = view?.findViewById<View>(R.id.g_raise)

        if(currentRaisingAmount > 0) {
            currentRaisingPoint += 1
            currentRaisingAmount -= 1

            animateCoin()
        }

        if(currentRaisingPoint>=100) {
            currentStoryLevel += 1
            currentRaisingPoint = currentRaisingPoint % 100
        }

        txtcurrentRaisingPoint?.text = (currentRaisingPoint % 100).toString()
        txtcurrentRaisingAmount?.text = currentRaisingAmount.toString()
        txtcurrentLevel?.text = currentStoryLevel.toString()

        //setting gage
        setupRaisingGage()
    }

    private fun GrowingRepeat() {
        if(isBtnGrowingpressed) {
            ProcessGrowing()
            handler.postDelayed({ GrowingRepeat() }, 50)
        }
    }

    private fun animateCoin() {
        val btnGrowing = view?.findViewById<Button>(R.id.b_growing) ?: return
        val txtRaisingPoint = view?.findViewById<TextView>(R.id.t_raisingPoint) ?: return

        val imgCoin = ImageView(requireContext()).apply {
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
    private val storyCount: Int = 5

    private fun ShowStoryPopup() {
        //LogLS.d("Begin")
        if (showingDialogName=="Story") return

        activity?.runOnUiThread {
            showingDialogName="Story"

            // 스토리 팝업 레이아웃 inflate
            val inflater = LayoutInflater.from(requireContext())
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
        //LogLS.d("Begin")
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
            // XML 레이아웃을 inflate해서 버튼 생성
            val storyButton = LayoutInflater.from(requireContext())
                .inflate(R.layout.raising_story_btn, storyContainer, false) as Button

            storyButton.text = "스토리 ${i + 1}"

            if (i < currentStoryLevel) {
                storyButton.setOnClickListener {
                    showStoryPlayPopup(i + 1)
                }
            } else {
                storyButton.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.btn_grey_light)
                storyButton.setOnClickListener {
                    Toast.makeText(requireContext(), "레벨이 낮습니다!", Toast.LENGTH_SHORT).show()
                }
            }
            storyContainer.addView(storyButton)
        }
    }

    private fun showStoryPlayPopup(storyNumber: Int) {
        //LogLS.d("Begin")
        val intent = Intent(requireContext(), RaisingStoryPlayActivity::class.java)
        intent.putExtra("currentStory", storyNumber)
        startActivity(intent)
    }

/* *********
*
* Ranking
*
* *********/
    private fun ShowRankingPopup() {
        //LogLS.d("Begin")
        val intent = Intent(requireContext(), RankingMainActivity::class.java)
        rankingActivityLauncher.launch(intent)
    }

/* *********
*
* Item
*
* *********/
    private var popupTotalItemGrowingAmount = 0

    fun onItemDialogResult(totalItemGrowingAmount: Int, currentNormalItemAmount: Int, currentCrewItemAmount: Int) {
        //LogLS.d("Begin")
        this.currentRaisingAmount += totalItemGrowingAmount
        this.currentNormalItemAmount = currentNormalItemAmount
        this.currentCrewItemAmount = currentCrewItemAmount
    }

    private fun ShowItemPopup() {
        //LogLS.d("Begin")

        if (showingDialogName == "Item") return

        activity?.runOnUiThread {
            showingDialogName = "Item"
            popupTotalItemGrowingAmount = 0

            // DialogFragment 대신 직접 View를 overlay로 추가
            val inflater = LayoutInflater.from(requireContext())
            val popupView = inflater.inflate(R.layout.activity_raising_item, androidUIContainer, false)

            popupView.tag = "itemPopup"

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
        //LogLS.d("Begin")
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
        //LogLS.d("Begin")
        val tvNormalItem = popupView.findViewById<TextView>(R.id.tv_normal)
        tvNormalItem.text = currentNormalItemAmount.toString()

        val tvCrewlItem = popupView.findViewById<TextView>(R.id.tv_crew)
        tvCrewlItem.text = currentCrewItemAmount.toString()

        val tvGetGrowingAmount = popupView.findViewById<TextView>(R.id.tv_openamount)
        tvGetGrowingAmount.text = popupTotalItemGrowingAmount.toString()
    }

    private fun openItemBoxInPopup(boxId: BoxId, popupView: View) {
        //LogLS.d("Begin")
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

    private fun ShowScriptPopup() {
        //LogLS.d("Begin")
        activity?.runOnUiThread {
            // 기존 팝업이 있다면 제거
            val existingPopup = androidUIContainer.findViewWithTag<View>("scriptPopup")
            if (existingPopup != null) {
                androidUIContainer.removeView(existingPopup)
            }

            // 레이아웃 inflate
            val inflater = LayoutInflater.from(requireContext())
            val popupView = inflater.inflate(R.layout.popup_raising_character_script, androidUIContainer, false)

            // 태그 설정
            popupView.tag = "scriptPopup"

            val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 제약 조건 설정 (부모의 왼쪽 상단에 고정)
            layoutParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID

            layoutParams.horizontalBias = 0.5f
            layoutParams.verticalBias = 0.45f

            // 컨테이너에 추가
            androidUIContainer.addView(popupView, layoutParams)

            // 뷰가 그려진 후 크기를 측정해서 마진 적용
            popupView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    popupView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    // 마진 적용
                    layoutParams.bottomMargin = (popupView.height * 1.2).toInt()
                    popupView.layoutParams = layoutParams
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
    /********CallToUnity********/
    private fun SendMessageToUnity( inFunctionName : String )
    {
        //LogLS.d(inFunctionName)
        if (activity is MainActivity) {
            (activity as MainActivity).SendMessageToUnity(inFunctionName)
        }
    }

    /********CalledFromUnity********/
    public fun CalledFunctionFromUnity(inFunctionName:String)
    {
        //LogLS.d(inFunctionName)

        when(inFunctionName) {
            "UnityGrowingTriggerEnter"-> {
                SetGrowingButtonVisible()
                return
            }
            "UnityStoryTriggerEnter"-> {
                ShowStoryPopup()
                return
            }
            "UnityRankingTriggerEnter"-> {
                ShowRankingPopup()
                return
            }
            "UnityItemTriggerEnter"-> {
                ShowItemPopup()
                return
            }
            "UnityPopUpScript"-> {
                ShowScriptPopup()
                return
            }
        }
    }

    //Unity Call
//    public fun UnityGrowingTriggerEnter() {
//        //Log.d("LogLS", "UnityGrowingTriggerEnter call")
//
//        SetGrowingButtonVisible()
//    }

//    public fun UnityStoryTriggerEnter() {
//        //Log.d("LogLS", "UnityStoryTriggerEnter call")
//
//        ShowStoryPopup()
//        //SendMessageToUnity( "UnityProcessStory" )
//    }

//    public fun UnityRankingTriggerEnter() {
//        //Log.d("LogLS", "UnityRankingTriggerEnter call")
//
//        ShowRankingPopup()
//        //SendMessageToUnity( "UnityProcessRanking" )
//    }

//    public fun UnityItemTriggerEnter() {
//        //Log.d("LogLS", "UnityItemTriggerEnter call")
//
//        ShowItemPopup()
//    }

//    public fun UnityPopUpScript()
//    {
//        //Log.d("LogLS", "UnityPopUpScript call")
//        ShowScriptPopup()
//    }

}