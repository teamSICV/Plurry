package com.SICV.plurry

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.SICV.plurry.ranking.MainCrewRankingManager
import com.SICV.plurry.ranking.MainRankingManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.media.MediaPlayer // MediaPlayer 임포트

class MainHomeFragment : Fragment() {

    //Data
    private lateinit var rankingManager: MainRankingManager
    private lateinit var crewRankingManager: MainCrewRankingManager
    private lateinit var myWalkRecord: MainMyWalkRecord

    // MainActivity Interface
    // 배경 음악을 위한 미디어 플레이어
    private var mediaPlayer: MediaPlayer? = null // MediaPlayer 인스턴스 선언

    // MainActivity 인터페이스
    interface OnFragmentInteractionListener {
        fun onNavigationRequested(destination: String, extras: Bundle? = null)
    }

    private var listener: OnFragmentInteractionListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        //LogLS.d("Begin")
        return inflater.inflate(R.layout.fragment_main_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //LogLS.d("Begin")

        // --- 음악 시작: 배경 음악 재생을 초기화하고 연속 재생 시작 ---
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.main)
            mediaPlayer?.isLooping = true // 음악을 계속 반복하도록 설정

            // 볼륨을 절반(0.5)으로 설정합니다. (좌우 채널)
            mediaPlayer?.setVolume(0.4f, 0.4f)

            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("MainHomeFragment", "미디어 플레이어 초기화 또는 시작 오류: ${e.message}")
        }
        // --- 음악 끝 ---

        settingButton(view)
        setupRankingManager(view)
        setupCrewRankingManager(view)
        setupMyWalkRecord(view)
    }

    private fun settingButton(view: View) {
        val buttonGoingWalk = view.findViewById<Button>(R.id.b_goingWalk)
        val buttonPointRecord = view.findViewById<Button>(R.id.b_pointRecord)
        val buttonCrewLine = view.findViewById<Button>(R.id.b_crewLine)
        val buttonRaising = view.findViewById<Button>(R.id.b_raising)
        val buttonMyPage = view.findViewById<Button>(R.id.btnMyPage)

        buttonGoingWalk.setOnClickListener {
            listener?.onNavigationRequested("GOING_WALK")
        }

        buttonPointRecord.setOnClickListener {
            listener?.onNavigationRequested("POINT_RECORD")
        }

        buttonCrewLine.setOnClickListener {
            checkUserCrewStatus()
        }

        buttonRaising.setOnClickListener {
            listener?.onNavigationRequested("RAISING")
        }

        buttonMyPage.setOnClickListener {
            listener?.onNavigationRequested("MY_PAGE")
        }
    }

    private fun checkUserCrewStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            listener?.onNavigationRequested("CREW_LINE_CHOOSE")
            return
        }

        val uid = currentUser.uid
        val db = FirebaseFirestore.getInstance()

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val crewAt = userDoc.getString("crewAt")

                    if (!crewAt.isNullOrEmpty()) {
                        val extras = Bundle().apply {
                            putString("crewId", crewAt)
                        }
                        listener?.onNavigationRequested("CREW_LINE_MAIN", extras)
                    } else {
                        listener?.onNavigationRequested("CREW_LINE_CHOOSE")
                    }
                } else {
                    listener?.onNavigationRequested("CREW_LINE_CHOOSE")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewCheck", "사용자 정보 확인 실패", e)
                listener?.onNavigationRequested("CREW_LINE_CHOOSE")
            }
    }

    private fun setupRankingManager(view: View) {
        val rankingConstraintLayout = view.findViewById<ConstraintLayout>(R.id.mainRankConstraintLayout)
        val titleTextView = rankingConstraintLayout.getChildAt(0) as TextView
        val valueTextView = rankingConstraintLayout.getChildAt(1) as TextView
        val unitTextView = rankingConstraintLayout.getChildAt(2) as TextView

        val leftArrow = view.findViewById<ImageView>(R.id.btnPre)
        val rightArrow = view.findViewById<ImageView>(R.id.btnNext)

        rankingManager = MainRankingManager(
            titleTextView, valueTextView, unitTextView, leftArrow, rightArrow
        )

        rankingManager.initialize()
    }

    private fun setupCrewRankingManager(view: View) {
        val caloTextView = view.findViewById<TextView>(R.id.mainCrewCalo)
        val countTextView = view.findViewById<TextView>(R.id.mainCrewCount)
        val distanceTextView = view.findViewById<TextView>(R.id.mainCrewDistance)

        crewRankingManager = MainCrewRankingManager(caloTextView, countTextView, distanceTextView)
        crewRankingManager.startUpdating()
    }

    private fun setupMyWalkRecord(view: View) {
        val caloTextView = view.findViewById<TextView>(R.id.mainWalkCalo)
        val distanceTextView = view.findViewById<TextView>(R.id.mainWalkDistance)
        val countTextView = view.findViewById<TextView>(R.id.mainWalkCount)

        myWalkRecord = MainMyWalkRecord(caloTextView, distanceTextView, countTextView)
        myWalkRecord.startUpdating()
    }

    override fun onResume() {
        super.onResume()
        crewRankingManager.startUpdating()
        myWalkRecord.startUpdating()

        // 프래그먼트로 돌아왔을 때 음악을 다시 재생합니다.
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        crewRankingManager.stopUpdating()
        myWalkRecord.stopUpdating()

        // 프래그먼트를 벗어나 다른 페이지로 이동할 때 음악을 일시 정지합니다.
        mediaPlayer?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rankingManager.cleanup()
        crewRankingManager.cleanup()
        myWalkRecord.cleanup()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
