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
import java.text.SimpleDateFormat
import java.util.*


class PointRecordDialog : DialogFragment() {

    companion object{
        fun newInstance(imageUrl: String, name: String, description: String, placeId: String = "", lat: Double = 0.0, lng: Double = 0.0, crewId: String = "", isVisited: Boolean = false): PointRecordDialog{
            val fragment = PointRecordDialog()
            val args = Bundle()
            args.putString("imageUrl", imageUrl)
            args.putString("name", name)
            args.putString("description", description)
            args.putString("placeId", placeId)
            args.putDouble("lat", lat)
            args.putDouble("lng", lng)
            args.putString("crewId", crewId)
            args.putBoolean("isVisited", isVisited)
            fragment.arguments = args

            Log.d("PointRecordDialog", "newInstance 호출 - placeId: $placeId, lat: $lat, lng: $lng, isVisited: $isVisited")

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
        val isVisited = arguments?.getBoolean("isVisited", false) ?: false

        Log.d("PointRecordDialog", "onCreateDialog - placeId: $placeId, lat: $lat, lng: $lng, isVisited: $isVisited")

        Glide.with(requireContext())
            .load(imageUrl)
            .into(imageView)

        textView.text = "$name\n\n$description"

        val btnStart = view.findViewById<Button>(R.id.btnStart)

        if (isVisited) {
            btnStart.text = "탐색완료"
            btnStart.setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
            btnStart.setTypeface(null, android.graphics.Typeface.BOLD)
            btnStart.isEnabled = false
            btnStart.isClickable = false
            showVisitedPlaceDescription(placeId, name, textView)
        } else {
            extractAndReplaceUidWithName(description, name, placeId) { updatedDescription ->
                textView.text = "$name\n\n$updatedDescription"
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null && placeId.isNotEmpty()) {
                checkPlaceOwnership(placeId, currentUser.uid) { isOwner ->
                    if (isOwner) {
                        btnStart.visibility = View.GONE
                        Log.d("PointRecordDialog", "현재 사용자가 장소 소유자이므로 탐색 버튼 숨김")
                    } else {
                        if (crewId.isNotEmpty()) {
                            checkCrewMembership(crewId) { isMember ->
                                btnStart.visibility = if (isMember) View.VISIBLE else View.GONE
                            }
                        } else {
                            btnStart.visibility = View.VISIBLE
                        }
                    }
                }
            } else {
                if (crewId.isNotEmpty()) {
                    checkCrewMembership(crewId) { isMember ->
                        btnStart.visibility = if (isMember) View.VISIBLE else View.GONE
                    }
                } else {
                    btnStart.visibility = View.VISIBLE
                }
            }
        }

        btnStart.setOnClickListener {
            if (placeId.isEmpty() || (lat == 0.0 && lng == 0.0)) {
                Log.w("PointRecordDialog", "유효하지 않은 위치 데이터: placeId=$placeId, lat=$lat, lng=$lng")
                Toast.makeText(requireContext(), "위치 정보가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val distance = extractDistanceValue(description)
            val exploreConfirmDialog = ExploreConfirmDialog(placeId, lat, lng, imageUrl, this, distance)
            exploreConfirmDialog.show(parentFragmentManager, "ExploreConfirmDialog")
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }

    private fun extractDistanceFromDescription(description: String): String {
        val lines = description.split("\n")
        for (line in lines) {
            if (line.startsWith("거리:")) {
                return line.replace("거리:", "").trim()
            }
        }
        return "0.00km"
    }

    private fun showVisitedPlaceDescription(placeId: String, placeName: String, textView: TextView) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            textView.text = "$placeName\n\n탐색한 장소"
            return
        }

        val uid = currentUser.uid
        val db = FirebaseFirestore.getInstance()

        db.collection("Users").document(uid).collection("visitedPlaces").document(placeId)
            .get()
            .addOnSuccessListener { visitedDoc ->
                val visitTimestamp = try {
                    val timestampField = visitedDoc.get("timestamp")
                    Log.d("PointRecordDialog", "timestamp 필드 타입: ${timestampField?.javaClass?.simpleName}")

                    when (timestampField) {
                        is Long -> timestampField
                        is Double -> timestampField.toLong()
                        is String -> timestampField.toLongOrNull() ?: 0L
                        is com.google.firebase.Timestamp -> {
                            val timestampValue = timestampField.toDate().time
                            Log.d("PointRecordDialog", "Timestamp를 Long으로 변환: $timestampValue")
                            db.collection("Users").document(uid).collection("visitedPlaces").document(placeId)
                                .update("timestamp", timestampValue)
                                .addOnSuccessListener {
                                    Log.d("PointRecordDialog", "visitedPlaces timestamp을 Timestamp에서 Long으로 변환 완료: $timestampValue")
                                }
                                .addOnFailureListener { e ->
                                    Log.e("PointRecordDialog", "visitedPlaces timestamp 변환 업데이트 실패", e)
                                }
                            timestampValue
                        }
                        null -> {
                            Log.d("PointRecordDialog", "timestamp 필드가 null")
                            0L
                        }
                        else -> {
                            Log.w("PointRecordDialog", "알 수 없는 timestamp 타입: ${timestampField.javaClass.simpleName}")
                            0L
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PointRecordDialog", "visitedPlaces timestamp 처리 중 오류: ${e.message}", e)
                    0L
                }
                val formattedVisitTime = formatTimestamp(visitTimestamp)

                db.collection("Places").document(placeId)
                    .get()
                    .addOnSuccessListener { placeDoc ->
                        if (placeDoc.exists()) {
                            val addedBy = placeDoc.getString("addedBy") ?: ""

                            getUserName(addedBy) { addedByName ->
                                val distanceStr = extractDistanceFromDescription(arguments?.getString("description") ?: "")

                                val visitedDescription = buildString {
                                    append("추가한 유저: $addedByName\n")
                                    append("거리: $distanceStr\n")
                                    append("탐색 시간: $formattedVisitTime")
                                }

                                textView.text = "$placeName\n\n$visitedDescription"
                            }
                        } else {
                            textView.text = "$placeName\n\n탐색 시간: $formattedVisitTime"
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("PointRecordDialog", "장소 정보 가져오기 실패", exception)
                        textView.text = "$placeName\n\n탐색 시간: $formattedVisitTime"
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("PointRecordDialog", "탐색 시간 가져오기 실패", exception)
                textView.text = "$placeName\n\n탐색한 장소"
            }
    }

    private fun extractDistanceValue(description: String): Double {
        val lines = description.split("\n")
        for (line in lines) {
            if (line.startsWith("거리:")) {
                val distanceStr = line.replace("거리:", "").replace("km", "").trim()
                return distanceStr.toDoubleOrNull() ?: 0.0
            }
        }
        return 0.0
    }

    private fun getUserName(userId: String, callback: (String) -> Unit) {
        if (userId.isEmpty()) {
            callback("알 수 없음")
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { userDocument ->
                val userName = if (userDocument.exists()) {
                    userDocument.getString("name") ?: userId
                } else {
                    userId
                }
                callback(userName)
            }
            .addOnFailureListener { exception ->
                Log.e("PointRecordDialog", "사용자 이름 가져오기 실패: $userId", exception)
                callback(userId)
            }
    }

    private fun checkPlaceOwnership(placeId: String, currentUserId: String, callback: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Places").document(placeId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val addedBy = document.getString("addedBy")
                    val isOwner = addedBy == currentUserId
                    Log.d("PointRecordDialog", "장소 소유권 확인: addedBy=$addedBy, currentUser=$currentUserId, isOwner=$isOwner")
                    callback(isOwner)
                } else {
                    Log.d("PointRecordDialog", "Places 문서가 존재하지 않음")
                    callback(false)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PointRecordDialog", "장소 소유권 확인 실패", exception)
                callback(false)
            }
    }

    private fun extractAndReplaceUidWithName(description: String, placeName: String, placeId: String, callback: (String) -> Unit) {
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
                .addOnSuccessListener { userDocument ->
                    val userName = if (userDocument.exists()) {
                        userDocument.getString("name") ?: uid
                    } else {
                        uid
                    }

                    Log.d("PointRecordDialog", "사용자 이름 가져오기 성공: $userName")

                    var updatedDescription = buildString {
                        append("추가한 유저: $userName")
                        if (distanceLine.isNotEmpty()) {
                            append("\n$distanceLine")
                        }
                    }

                    if (placeId.isNotEmpty()) {
                        db.collection("Places").document(placeId)
                            .get()
                            .addOnSuccessListener { placeDocument ->
                                if (placeDocument.exists()) {
                                    // imageTime 처리 로직은 그대로 유지하되, 거리 정보는 이미 포함되어 있음
                                    val imageTime = try {
                                        when (val imageTimeField = placeDocument.get("imageTime")) {
                                            is Long -> imageTimeField
                                            is Double -> imageTimeField.toLong()
                                            is String -> imageTimeField.toLongOrNull() ?: 0L
                                            is com.google.firebase.Timestamp -> {
                                                val timestampValue = imageTimeField.toDate().time
                                                db.collection("Places").document(placeId)
                                                    .update("imageTime", timestampValue)
                                                    .addOnSuccessListener {
                                                        Log.d("PointRecordDialog", "imageTime을 Timestamp에서 Long으로 변환 완료: $timestampValue")
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e("PointRecordDialog", "imageTime 변환 업데이트 실패", e)
                                                    }
                                                timestampValue
                                            }
                                            else -> 0L
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PointRecordDialog", "imageTime 처리 중 오류", e)
                                        0L
                                    }

                                    if (imageTime > 0L) {
                                        val formattedTime = formatTimestamp(imageTime)
                                        updatedDescription += "\n탐색 시간: $formattedTime"
                                        Log.d("PointRecordDialog", "imageTime 처리 완료: $imageTime -> $formattedTime")
                                    }
                                }
                                callback(updatedDescription)
                            }
                            .addOnFailureListener { exception ->
                                Log.e("PointRecordDialog", "imageTime 가져오기 실패", exception)
                                callback(updatedDescription)
                            }
                    } else {
                        callback(updatedDescription)
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

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val date = Date(timestamp)
            val format = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN)
            format.format(date)
        } catch (e: Exception) {
            Log.e("PointRecordDialog", "타임스탬프 포맷 실패", e)
            "시간 정보 없음"
        }
    }


    class ExploreConfirmDialog(
        private val placeId: String,
        private val lat: Double,
        private val lng: Double,
        private val imageUrl: String,
        private val parent: DialogFragment,
        private val distance: Double = 0.0
    ) : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
            val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.activity_goingwalk_exploreconfirm, null)

            val imageView = view.findViewById<ImageView>(R.id.dialogImage)
            val btnStart = view.findViewById<Button>(R.id.btnStartExplore)
            val btnCancel = view.findViewById<Button>(R.id.btnCancelExplore)

            val tvDistance = view.findViewById<TextView>(R.id.tvDistance)
            if (tvDistance != null && distance > 0.0) {
                tvDistance.text = "거리: ${String.format("%.2f", distance)}km"
            }

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