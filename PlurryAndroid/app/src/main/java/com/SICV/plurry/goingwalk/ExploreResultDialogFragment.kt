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
import android.util.Log

class ExploreResultDialogFragment : DialogFragment() {

    private lateinit var placeImageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var rewardTextView: TextView
    private lateinit var mainActionButton: Button
    private lateinit var secondaryButton: Button
    private lateinit var statsTextView: TextView

    private lateinit var onnxHelper: OnnxHelper

    private var mode: String = "confirm"
    private var imageUrl: String? = null
    private var placeId: String? = null
    private var totalSteps: Int = 0
    private var totalDistance: Double = 0.0
    private var totalCalories: Double = 0.0

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
        statsTextView = view.findViewById(R.id.tvStatsInfo)

        onnxHelper = OnnxHelper(requireContext())

        mode = arguments?.getString("mode") ?: "confirm"
        imageUrl = arguments?.getString("imageUrl")
        placeId = arguments?.getString("placeId")
        totalSteps = arguments?.getInt("totalSteps", 0) ?: 0
        totalDistance = arguments?.getDouble("totalDistance", 0.0) ?: 0.0
        totalCalories = arguments?.getDouble("totalCalories", 0.0) ?: 0.0

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
                statsTextView.visibility = View.GONE
                mainActionButton.text = "촬영하기"
                secondaryButton.visibility = View.GONE

                mainActionButton.setOnClickListener { launchCamera() }
            }

            "fail" -> {
                titleTextView.text = "사진이 일치하지 않아요\n다시 시도해볼까요?"
                rewardTextView.text = "유사도가 기준에 미달했습니다"
                rewardTextView.visibility = View.VISIBLE
                statsTextView.visibility = View.GONE
                mainActionButton.text = "다시 촬영하기"
                secondaryButton.visibility = View.GONE

                mainActionButton.setOnClickListener { launchCamera() }
            }

            "success" -> {
                titleTextView.text = "탐색 성공!\n보상을 획득했어요!"
                rewardTextView.visibility = View.VISIBLE
                mainActionButton.visibility = View.GONE
                secondaryButton.visibility = View.VISIBLE

                rewardTextView.text = "일반 보상 아이템 지급\n크루 보상 아이템 지급"
                statsTextView.text = "걸음: ${totalSteps} 걸음\n거리: %.2f km\n칼로리: %.1f kcal".format(totalDistance / 1000, totalCalories)
                statsTextView.visibility = View.VISIBLE

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
            val failDialog = newInstance("fail", imageUrl ?: "", placeId ?: "", totalSteps, totalDistance, totalCalories)
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
                    val successDialog = newInstance("success", imageUrl ?: "", placeId ?: "", totalSteps, totalDistance, totalCalories)
                    successDialog.show(parentFragmentManager, "explore_success")
                    dismiss()
                }
            }
            .addOnFailureListener {
                titleTextView.text = "업로드 실패\n다시 시도해주세요"
                mainActionButton.isEnabled = true
            }
    }

    // 🚀 수정: 이미지 URL을 운동 데이터와 같은 경로에 저장하도록 변경
    private fun saveImageUrlToFirestore(imageDownloadUrl: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()

        // placeId가 null이 아닌지 확인
        if (placeId == null) {
            Log.e("Firebase", "Place ID가 null입니다. 이미지 URL을 저장할 수 없습니다.")
            return
        }

        // 운동 데이터가 저장되는 경로와 동일하게 설정
        // Users > {userId} > walk > visitedPlace > {placeId} > data
        val visitedPlaceDataRef = firestore
            .collection("Users")
            .document(userId)
            .collection("walk")
            .document("visitedPlace") // 이 부분이 컬렉션인지 문서인지에 따라 다음 줄이 달라짐.
            .collection(placeId!!) // placeId를 컬렉션 이름으로 사용
            .document("data") // 운동 데이터가 저장되는 문서

        // 해당 문서에 imgUrl 필드만 업데이트 (또는 추가)
        visitedPlaceDataRef.update("imgUrl", imageDownloadUrl)
            .addOnSuccessListener {
                Log.d("Firebase", "이미지 URL Firebase 저장 성공 (운동 데이터와 함께)")
            }
            .addOnFailureListener { e ->
                // 만약 'data' 문서가 아직 존재하지 않는다면 update는 실패할 수 있으므로,
                // set(merge=true)를 사용하여 문서를 생성하거나 업데이트할 수 있습니다.
                // 그러나 ExploreTrackingFragment에서 운동 데이터가 먼저 저장되므로,
                // 대부분의 경우 'data' 문서는 이미 존재할 것입니다.
                Log.e("Firebase", "이미지 URL Firebase 저장 실패 (운동 데이터와 함께)", e)
                // 필요하다면 여기서 set(merge:true) 로직을 추가할 수 있습니다.
                // 예를 들어:
                /*
                visitedPlaceDataRef.set(hashMapOf("imgUrl" to imageDownloadUrl), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("Firebase", "이미지 URL Firebase 저장 성공 (머지 방식)")
                    }
                    .addOnFailureListener { e2 ->
                        Log.e("Firebase", "이미지 URL Firebase 저장 최종 실패 (머지 방식)", e2)
                    }
                */
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::onnxHelper.isInitialized) onnxHelper.close()
    }

    companion object {
        fun newInstance(mode: String, imageUrl: String, placeId: String, totalSteps: Int, totalDistance: Double, totalCalories: Double): ExploreResultDialogFragment {
            return ExploreResultDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("mode", mode)
                    putString("imageUrl", imageUrl)
                    putString("placeId", placeId)
                    putInt("totalSteps", totalSteps)
                    putDouble("totalDistance", totalDistance)
                    putDouble("totalCalories", totalCalories)
                }
            }
        }

        fun newInstance(mode: String, imageUrl: String, placeId: String): ExploreResultDialogFragment {
            return newInstance(mode, imageUrl, placeId, 0, 0.0, 0.0)
        }

        fun newInstance(mode: String, imageUrl: String): ExploreResultDialogFragment {
            return newInstance(mode, imageUrl, "", 0, 0.0, 0.0)
        }
    }
}