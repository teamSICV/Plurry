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
import com.google.firebase.firestore.FieldValue // FieldValue 임포트 추가
import java.util.Date
import kotlin.math.floor // floor 함수 임포트 추가

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

        // 1. 산책 기록 저장
        db.collection("Users").document(userId)
            .collection("goWalk")
            .add(walkData)
            .addOnSuccessListener {
                // 저장 성공 로그 (선택)
            }
            .addOnFailureListener {
                // 저장 실패 로그 (선택)
            }

        // 2. currentRaisingAmount 업데이트
        val stepsDividedBy100 = floor(steps / 100.0).toInt() // 걸음수를 100으로 나누고 소수점 버림

        if (userId != "anonymous") { // 익명 사용자가 아닌 경우에만 업데이트
            val userRewardRef = db.collection("Game")
                .document("users")
                .collection("userReward")
                .document(userId)

            userRewardRef.update("currentRaisingAmount", FieldValue.increment(stepsDividedBy100.toLong()))
                .addOnSuccessListener {
                    // currentRaisingAmount 업데이트 성공 로그
                    android.util.Log.d("WalkEndDialog", "currentRaisingAmount 업데이트 성공: $stepsDividedBy100 추가")
                }
                .addOnFailureListener { e ->
                    // currentRaisingAmount 업데이트 실패 로그
                    android.util.Log.e("WalkEndDialog", "currentRaisingAmount 업데이트 실패: ${e.message}")
                    // 문서가 없어서 업데이트에 실패하는 경우의 로직은 제거되었습니다.
                    // 계정 생성 시 'currentRaisingAmount' 필드가 항상 존재한다고 가정합니다.
                }
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
