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
import com.SICV.plurry.ranking.MainCrewRankingManager
import com.SICV.plurry.ranking.MainRankingManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainHomeFragment : Fragment() {

    //Data
    private lateinit var rankingManager: MainRankingManager
    private lateinit var crewRankingManager: MainCrewRankingManager
    private lateinit var myWalkRecord: MainMyWalkRecord

    // MainActivity Interface
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
        val rankingLinearLayout = view.findViewById<LinearLayout>(R.id.mainRankLinearLayout)
        val titleTextView = rankingLinearLayout.getChildAt(0) as TextView
        val valueTextView = rankingLinearLayout.getChildAt(1) as TextView
        val unitTextView = rankingLinearLayout.getChildAt(2) as TextView

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
    }

    override fun onPause() {
        super.onPause()
        crewRankingManager.stopUpdating()
        myWalkRecord.stopUpdating()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rankingManager.cleanup()
        crewRankingManager.cleanup()
        myWalkRecord.cleanup()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
