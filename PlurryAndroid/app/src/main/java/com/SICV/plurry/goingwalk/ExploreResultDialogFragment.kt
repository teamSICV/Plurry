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
//유사도 비교 통합 과정에서 추가된 부분
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.SICV.plurry.onnx.OnnxHelper
import com.SICV.plurry.onnx.OnnxComparator
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class ExploreResultDialogFragment : DialogFragment() {

    private lateinit var placeImageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var rewardTextView: TextView
    private lateinit var mainActionButton: Button
    private lateinit var secondaryButton: Button

    //onnx helper 추가
    private lateinit var onnxHelper: OnnxHelper

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
        rewardTextView = view.findViewById(R.id.tvRewardInfo)
        mainActionButton = view.findViewById(R.id.btnMainAction)
        secondaryButton = view.findViewById(R.id.btnSecondaryAction)

        // ONNX Helper 초기화
        onnxHelper = OnnxHelper(requireContext())

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
                rewardTextView.visibility = View.GONE
                mainActionButton.text = "촬영하기"
                secondaryButton.visibility = View.GONE

                mainActionButton.setOnClickListener {
                    launchCamera()
                }
            }

            "fail" -> {
                titleTextView.text = "사진이 일치하지 않아요\n다시 시도해볼까요?"
                rewardTextView.text = "유사도가 기준에 미달했습니다"
                rewardTextView.visibility = View.VISIBLE
                mainActionButton.text = "다시 촬영하기"
                secondaryButton.visibility = View.GONE

                mainActionButton.setOnClickListener {
                    launchCamera()
                }
            }

            "success" -> {
                titleTextView.text = "탐색 성공!\n보상을 획득했어요!"
                rewardTextView.visibility = View.VISIBLE
                mainActionButton.visibility = View.GONE
                secondaryButton.visibility = View.VISIBLE

                secondaryButton.setOnClickListener {
                    dismiss()
                    activity?.supportFragmentManager?.popBackStack()
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
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir!!)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            //변경 : ploadToFirebase()
            compareImages()
        }
    }

//유사도 비교 통합 과정에서 추가된 부분
    private fun compareImages() {
        // UI 업데이트 - 비교 중 표시
        titleTextView.text = "📷 이미지를 비교하고 있어요..."
        mainActionButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 촬영한 이미지 로드
                val userBitmap = loadImageFromFile(imageFile)
                if (userBitmap == null) {
                    withContext(Dispatchers.Main) {
                        showComparisonResult(false, 0f, "이미지 로드 실패")
                    }
                    return@launch
                }

                // 2. 참조 이미지 로드 (imageUrl에서)
                val referenceBitmap = loadImageFromUrl(imageUrl)
                if (referenceBitmap == null) {
                    withContext(Dispatchers.Main) {
                        showComparisonResult(false, 0f, "참조 이미지 로드 실패")
                    }
                    return@launch
                }

                // 3. ONNX 모델로 특징 추출
                val userFeatures = onnxHelper.runInference(userBitmap)
                val referenceFeatures = onnxHelper.runInference(referenceBitmap)

                if (userFeatures == null || referenceFeatures == null) {
                    withContext(Dispatchers.Main) {
                        showComparisonResult(false, 0f, "이미지 처리 실패")
                    }
                    return@launch
                }

                // 4. 코사인 유사도 계산
                val similarity = OnnxComparator.cosineSimilarity(userFeatures, referenceFeatures)

                // 5. 결과 판정 (임계값 0.8)
                val threshold = 0.8f
                val isMatch = similarity >= threshold

                // 6. UI 업데이트
                withContext(Dispatchers.Main) {
                    if (isMatch) {
                        // 성공 시 Firebase 업로드 후 성공 화면
                        uploadToFirebase()
                    } else {
                        // 실패 시 재촬영 옵션
                        showComparisonResult(false, similarity, null)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showComparisonResult(false, 0f, "비교 중 오류: ${e.message}")
                }
            }
        }
    }

    private fun loadImageFromFile(file: File?): Bitmap? {
        return try {
            file?.let { BitmapFactory.decodeFile(it.absolutePath) }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadImageFromUrl(url: String?): Bitmap? {
        return try {
            url?.let {
                val connection = URL(it).openConnection()
                connection.doInput = true
                connection.connect()
                val inputStream = connection.getInputStream()
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showComparisonResult(isSuccess: Boolean, similarity: Float, errorMessage: String?) {
        if (isSuccess) {
            // 성공 모드로 전환 (이미 uploadToFirebase에서 처리됨)
            return
        } else {
            // 실패 모드로 전환
            val failDialog = ExploreResultDialogFragment
                .newInstance("fail", imageUrl ?: "")

            failDialog.show(parentFragmentManager, "explore_fail")
            dismiss()
        }
    }

    private fun uploadToFirebase() {
        val file = imageFile ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val fileName = "${userId}_${file.name}"
        val storageRef = FirebaseStorage.getInstance().reference
            .child("exploreTempImages/$fileName")

        titleTextView.text = "📤 업로드 중..."

        storageRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                // 업로드 성공 시 성공 화면으로
                val successDialog = ExploreResultDialogFragment
                    .newInstance("success", imageUrl ?: "")

                successDialog.show(parentFragmentManager, "explore_success")
                dismiss()
            }
            .addOnFailureListener {
                // 업로드 실패 시
                titleTextView.text = "업로드 실패\n다시 시도해주세요"
                mainActionButton.isEnabled = true
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::onnxHelper.isInitialized) {
            onnxHelper.close()
        }
    }
//위에서부터 여기까지 추가됨
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
