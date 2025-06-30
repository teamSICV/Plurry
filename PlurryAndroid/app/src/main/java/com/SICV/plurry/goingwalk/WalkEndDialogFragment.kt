package com.SICV.plurry.goingwalk

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.SICV.plurry.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class WalkEndDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.activity_goingwalk_dialog_walk_end, null)

        val distance = arguments?.getString("distance") ?: "0.00"
        val steps = arguments?.getInt("steps") ?: 0
        val calories = arguments?.getString("calories") ?: "0.0"
        val startTime = arguments?.getLong("startTime") ?: 0L

        val tvDistance = view.findViewById<TextView>(R.id.tvWalkDistance)
        val tvSteps = view.findViewById<TextView>(R.id.tvWalkSteps)
        val tvCalories = view.findViewById<TextView>(R.id.tvWalkCalories)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirmWalkEnd)

        tvDistance.text = "거리: $distance km"
        tvSteps.text = "걸음 수: $steps 걸음"
        tvCalories.text = "칼로리 소모: $calories kcal"

        // ✅ Firestore 저장
        val db = FirebaseFirestore.getInstance()
        val walkData = hashMapOf(
            "distance" to distance.toDouble(),
            "stepCount" to steps,
            "calories" to calories.toDouble(),
            "startTime" to Date(startTime),
            "endTime" to Date()
        )

        // 🔐 로그인된 Firebase 사용자 UID로 저장
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

        db.collection("Users").document(userId)
            .collection("goWalk")
            .add(walkData)
            .addOnSuccessListener {
                // 저장 성공 로그 (선택)
            }
            .addOnFailureListener {
                // 저장 실패 로그 (선택)
            }

        btnConfirm.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), GoingWalkMainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            requireActivity().finish()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }

    companion object {
        fun newInstance(distance: String, steps: Int, calories: String, startTime: Long): WalkEndDialogFragment {
            val fragment = WalkEndDialogFragment()
            val args = Bundle().apply {
                putString("distance", distance)
                putInt("steps", steps)
                putString("calories", calories)
                putLong("startTime", startTime)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
