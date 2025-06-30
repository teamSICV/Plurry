package com.SICV.plurry.goingwalk

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.SICV.plurry.onnx.OnnxComparator
import com.SICV.plurry.onnx.OnnxHelper
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class ExploreResultDialogFragment : DialogFragment() {

    private lateinit var placeImageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var rewardTextView: TextView
    private lateinit var mainActionButton: Button
    private lateinit var secondaryButton: Button

    private lateinit var onnxHelper: OnnxHelper

    private var mode: String = "confirm"
    private var imageUrl: String? = null
    private var placeId: String? = null

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

        onnxHelper = OnnxHelper(requireContext())

        mode = arguments?.getString("mode") ?: "confirm"
        imageUrl = arguments?.getString("imageUrl")
        placeId = arguments?.getString("placeId")

        imageUrl?.let {
            Glide.with(this).load(it).into(placeImageView)
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

                mainActionButton.setOnClickListener { launchCamera() }
            }

            "fail" -> {
                titleTextView.text = "사진이 일치하지 않아요\n다시 시도해볼까요?"
                rewardTextView.text = "유사도가 기준에 미달했습니다"
                rewardTextView.visibility = View.VISIBLE
                mainActionButton.text = "다시 촬영하기"
                secondaryButton.visibility = View.GONE

                mainActionButton.setOnClickListener { launchCamera() }
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
            compareImages()
        }
    }

    private fun compareImages() {
        titleTextView.text = "📷 이미지를 비교하고 있어요..."
        mainActionButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userBitmap = loadImageFromFile(imageFile)
                val referenceBitmap = loadImageFromUrl(imageUrl)

                if (userBitmap == null || referenceBitmap == null) {
                    withContext(Dispatchers.Main) {
                        showComparisonResult(false, 0f, "이미지 로드 실패")
                    }
                    return@launch
                }

                val userFeatures = onnxHelper.runInference(userBitmap)
                val referenceFeatures = onnxHelper.runInference(referenceBitmap)

                if (userFeatures == null || referenceFeatures == null) {
                    withContext(Dispatchers.Main) {
                        showComparisonResult(false, 0f, "이미지 처리 실패")
                    }
                    return@launch
                }

                val similarity = OnnxComparator.cosineSimilarity(userFeatures, referenceFeatures)
                val threshold = 0.8f
                val isMatch = similarity >= threshold

                withContext(Dispatchers.Main) {
                    if (isMatch) {
                        uploadToFirebase()
                    } else {
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
        return try { file?.let { BitmapFactory.decodeFile(it.absolutePath) } } catch (e: Exception) { null }
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
        } catch (e: Exception) { null }
    }

    private fun showComparisonResult(isSuccess: Boolean, similarity: Float, errorMessage: String?) {
        if (!isSuccess) {
            val failDialog = newInstance("fail", imageUrl ?: "", placeId ?: "")
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
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    saveImageUrlToFirestore(uri.toString())  // ✅ Firestore에 저장
                    val successDialog = newInstance("success", imageUrl ?: "", placeId ?: "")
                    successDialog.show(parentFragmentManager, "explore_success")
                    dismiss()
                }
            }
            .addOnFailureListener {
                titleTextView.text = "업로드 실패\n다시 시도해주세요"
                mainActionButton.isEnabled = true
            }
    }

    private fun saveImageUrlToFirestore(imageDownloadUrl: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()

        // 경로: Users/{uid}/walk/{walkId}/visitedPlace/
        val visitedPlaceRef = firestore
            .collection("Users")
            .document(userId)
            .collection("walk")
            .document("latest")  // TODO: 필요하면 진짜 walkId로 교체
            .collection("visitedPlace")

        val data = hashMapOf(
            "imgUrl" to imageDownloadUrl,
            "placeId" to placeId,
            "timestamp" to System.currentTimeMillis()
        )

        visitedPlaceRef.add(data)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::onnxHelper.isInitialized) onnxHelper.close()
    }

    companion object {
        fun newInstance(mode: String, imageUrl: String, placeId: String): ExploreResultDialogFragment {
            return ExploreResultDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("mode", mode)
                    putString("imageUrl", imageUrl)
                    putString("placeId", placeId)
                }
            }
        }

        fun newInstance(mode: String, imageUrl: String): ExploreResultDialogFragment {
            return newInstance(mode, imageUrl, "")
        }
    }
}
