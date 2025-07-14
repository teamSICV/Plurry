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
import androidx.fragment.app.DialogFragment
import com.SICV.plurry.R
import com.SICV.plurry.goingwalk.GoingWalkMainActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class PointRecordDialog : DialogFragment() {

    companion object{
        fun newInstance(imageUrl: String, name: String, description: String): PointRecordDialog{
            val fragment = PointRecordDialog()
            val args = Bundle()
            args.putString("imageUrl", imageUrl)
            args.putString("name", name)
            args.putString("description", description)
            fragment.arguments = args
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

        Glide.with(requireContext())
            .load(imageUrl)
            .into(imageView)

        textView.text = "$name\n\n$description"

        extractAndReplaceUidWithName(description, name) { updatedDescription ->
            textView.text = "$name\n\n$updatedDescription"
        }

        val btnStart = view.findViewById<Button>(R.id.btnStart)
        btnStart.setOnClickListener {
            val intent = Intent(requireContext(), GoingWalkMainActivity::class.java)
            startActivity(intent)
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

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}