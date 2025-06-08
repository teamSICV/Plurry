package com.SICV.plurry.goingwalk

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.SICV.plurry.R
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExploreResultDialogFragment : DialogFragment() {

    private lateinit var placeImageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var mainActionButton: Button
    private lateinit var secondaryButton: Button

    private var mode: String = "confirm"
    private var imageUrl: String? = null
    private val REQUEST_IMAGE_CAPTURE = 2020

    private var imageFile: File? = null
    private var imageUri: Uri? = null

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

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            val photoFile = createImageFile()
            imageFile = photoFile
            imageUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
        } else {
            Toast.makeText(context, "카메라 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir!!)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            uploadToFirebase()
        } else {
            Toast.makeText(context, "촬영이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadToFirebase() {
        val file = imageFile ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val fileName = "${userId}_${file.name}"
        val storageRef = FirebaseStorage.getInstance().reference
            .child("exploreTempImages/$fileName")

        storageRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                Toast.makeText(context, "사진이 임시 저장되었습니다.", Toast.LENGTH_SHORT).show()
                ExploreResultDialogFragment
                    .newInstance("success", imageUrl ?: "")
                    .show(parentFragmentManager, "explore_success")
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(context, "업로드 실패", Toast.LENGTH_SHORT).show()
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

