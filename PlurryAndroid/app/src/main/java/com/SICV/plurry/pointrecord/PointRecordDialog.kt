package com.SICV.plurry.pointrecord

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R
import com.SICV.plurry.goingwalk.GoingWalkMainActivity
import com.SICV.plurry.goingwalk.ExploreTrackingFragment
import com.SICV.plurry.goingwalk.MapViewActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class PointRecordDialog : DialogFragment() {

    companion object{
        fun newInstance(imageUrl: String, name: String, description: String, placeId: String = "", lat: Double = 0.0, lng: Double = 0.0, crewId: String = ""): PointRecordDialog{
            val fragment = PointRecordDialog()
            val args = Bundle()
            args.putString("imageUrl", imageUrl)
            args.putString("name", name)
            args.putString("description", description)
            args.putString("placeId", placeId)
            args.putDouble("lat", lat)
            args.putDouble("lng", lng)
            args.putString("crewId", crewId)
            fragment.arguments = args

            Log.d("PointRecordDialog", "newInstance 호출 - placeId: $placeId, lat: $lat, lng: $lng")

            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view: View = LayoutInflater.from(requireContext())
            .inflate(R.layout.activity_point_record_popup, null)

        val imageView = view.findViewById<ImageView>(R.id.imageView)
        val textView = view.findViewById<TextView>(R.id.textView3)

        val imageUrl = arguments?.getString("imageUrl") ?: ""
        val name = arguments?.getString("name") ?: ""
        val description = arguments?.getString("description") ?: ""
        val placeId = arguments?.getString("placeId") ?: ""
        val lat = arguments?.getDouble("lat") ?: 0.0
        val lng = arguments?.getDouble("lng") ?: 0.0
        val crewId = arguments?.getString("crewId") ?: ""

        Log.d("PointRecordDialog", "onCreateDialog - placeId: $placeId, lat: $lat, lng: $lng")

        Glide.with(requireContext())
            .load(imageUrl)
            .into(imageView)

        textView.text = "$name\n\n$description"

        extractAndReplaceUidWithName(description, name) { updatedDescription ->
            textView.text = "$name\n\n$updatedDescription"
        }

        val btnStart = view.findViewById<Button>(R.id.btnStart)

        if (crewId.isNotEmpty()) {
            checkCrewMembership(crewId) { isMember ->
                if (!isMember) {
                    btnStart.visibility = View.GONE
                }
            }
        } else {
            btnStart.visibility = View.VISIBLE
        }

        btnStart.setOnClickListener {
            if (placeId.isEmpty() || (lat == 0.0 && lng == 0.0)) {
                Log.w("PointRecordDialog", "유효하지 않은 위치 데이터: placeId=$placeId, lat=$lat, lng=$lng")
                Toast.makeText(requireContext(), "위치 정보가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val exploreConfirmDialog = ExploreConfirmDialog(placeId, lat, lng, imageUrl, this)
            exploreConfirmDialog.show(parentFragmentManager, "ExploreConfirmDialog")
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }

    private fun extractAndReplaceUidWithName(description: String, placeName: String, callback: (String) -> Unit) {
        val lines = description.split("\n")
        var userLine = ""
        var distanceLine = ""

        for (line in lines) {
            when {
                line.startsWith("추가한 유저:") -> userLine = line
                line.startsWith("거리:") -> distanceLine = line
            }
        }

        if (userLine.isNotEmpty()) {
            val uid = userLine.replace("추가한 유저:", "").trim()
            Log.d("PointRecordDialog", "UID 추출: $uid")

            val db = FirebaseFirestore.getInstance()
            db.collection("Users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val userName = document.getString("name") ?: uid
                        Log.d("PointRecordDialog", "사용자 이름 가져오기 성공: $userName")

                        val updatedDescription = buildString {
                            append("추가한 유저: $userName")
                            if (distanceLine.isNotEmpty()) {
                                append("\n$distanceLine")
                            }
                        }

                        callback(updatedDescription)
                    } else {
                        Log.d("PointRecordDialog", "사용자 문서가 존재하지 않음")
                        callback(description)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("PointRecordDialog", "사용자 이름 가져오기 실패", exception)
                    callback(description)
                }
        } else {
            Log.d("PointRecordDialog", "UID를 찾을 수 없음")
            callback(description)
        }
    }


    class ExploreConfirmDialog(
        private val placeId: String,
        private val lat: Double,
        private val lng: Double,
        private val imageUrl: String,
        private val parent: DialogFragment
    ) : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
            val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.activity_goingwalk_exploreconfirm, null)

            val imageView = view.findViewById<ImageView>(R.id.dialogImage)
            val btnStart = view.findViewById<Button>(R.id.btnStartExplore)
            val btnCancel = view.findViewById<Button>(R.id.btnCancelExplore)

            Glide.with(view).load(imageUrl).into(imageView)

            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create()

            btnCancel.setOnClickListener {
                dismiss()
            }

            btnStart.setOnClickListener {
                try {
                    Log.d("ExploreConfirmDialog", "전달받은 데이터: placeId=$placeId, lat=$lat, lng=$lng, imageUrl=$imageUrl")

                    val intent = Intent(requireContext(), MapViewActivity::class.java)
                    intent.putExtra("placeId", placeId)
                    intent.putExtra("lat", lat)
                    intent.putExtra("lng", lng)
                    intent.putExtra("imageUrl", imageUrl)
                    intent.putExtra("startExplore", true)

                    Log.d("ExploreConfirmDialog", "MapViewActivity로 이동 시도")
                    startActivity(intent)

                    parent.dismiss()
                    dismiss()
                } catch (e: Exception) {
                    Log.e("ExploreConfirmDialog", "MapViewActivity 시작 실패", e)
                    Toast.makeText(requireContext(), "탐색 시작 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            return dialog
        }
    }

    private fun checkCrewMembership(crewId: String, callback: (Boolean) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            callback(false)
            return
        }

        val uid = currentUser.uid
        FirebaseFirestore.getInstance().collection("Users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val userCrewId = userDoc.getString("crewAt")
                    callback(userCrewId == crewId)
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}