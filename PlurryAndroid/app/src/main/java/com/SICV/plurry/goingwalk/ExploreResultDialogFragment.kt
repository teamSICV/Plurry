package com.SICV.plurry.goingwalk

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.SICV.plurry.R
import com.bumptech.glide.Glide

class ExploreResultDialogFragment : DialogFragment() {

    private lateinit var placeImageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var mainActionButton: Button
    private lateinit var secondaryButton: Button

    private var mode: String = "confirm"
    private var imageUrl: String? = null
    private val REQUEST_IMAGE_CAPTURE = 2020

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = android.app.AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.activity_goingwalk_explore_dialog, null)

        titleTextView = view.findViewById(R.id.tvDialogTitle)
        placeImageView = view.findViewById(R.id.ivPlaceImage)
        mainActionButton = view.findViewById(R.id.btnMainAction)
        secondaryButton = view.findViewById(R.id.btnSecondaryAction)

        mode = arguments?.getString("mode") ?: "confirm"
        imageUrl = arguments?.getString("imageUrl")

        imageUrl?.let {
            Glide.with(this)
                .load(it)
                .into(placeImageView)
        }

        setupDialogByMode()
        builder.setView(view)
        return builder.create()
    }

    private fun setupDialogByMode() {
        when (mode) {
            "confirm" -> {
                titleTextView.text = "장소에 도착했어요!\n사진을 찍어주세요"
                mainActionButton.text = "촬영하기"
                secondaryButton.visibility = View.GONE

                mainActionButton.setOnClickListener {
                    launchCamera()
                }
            }

            "fail" -> {
                titleTextView.text = "사진이 일치하지 않아요\n다시 시도해볼까요?"
                mainActionButton.text = "다시 촬영하기"
                secondaryButton.visibility = View.GONE

                mainActionButton.setOnClickListener {
                    launchCamera()
                }
            }

            "success" -> {
                titleTextView.text = "탐색 성공!\n보상을 획득했어요!"
                mainActionButton.visibility = View.GONE
                secondaryButton.visibility = View.VISIBLE

                secondaryButton.setOnClickListener {
                    dismiss()
                }
            }
        }
    }

    // 🔧 저장 없이 단순히 카메라 앱 실행만
    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
        } else {
            Toast.makeText(context, "카메라 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔁 사진 찍은 후: 무조건 성공으로 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            ExploreResultDialogFragment
                .newInstance("success", imageUrl ?: "")
                .show(parentFragmentManager, "explore_success")

            dismiss() // 현재 confirm 팝업 닫기
        } else {
            Toast.makeText(context, "촬영이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newInstance(mode: String, imageUrl: String): ExploreResultDialogFragment {
            return ExploreResultDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("mode", mode)
                    putString("imageUrl", imageUrl)
                }
            }
        }
    }
}
