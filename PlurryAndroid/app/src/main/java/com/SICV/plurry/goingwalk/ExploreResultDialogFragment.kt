package com.SICV.plurry.goingwalk

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import com.SICV.plurry.onnx.FaceMosaicHelper  // 새로 추가
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import com.google.firebase.firestore.FieldValue // FieldValue를 가져옵니다.

class ExploreResultDialogFragment : DialogFragment() {

    private lateinit var placeImageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var rewardTextView: TextView
    private lateinit var mainActionButton: Button
    private lateinit var secondaryButton: Button
    private lateinit var statsTextView: TextView

    // 이미지 비교용 뷰들 추가
    private lateinit var referenceImageView: ImageView
    private lateinit var userImageView: ImageView
    private lateinit var similarityTextView: TextView
    private lateinit var imageComparisonLayout: LinearLayout

    private lateinit var onnxHelper: OnnxHelper
    private lateinit var faceMosaicHelper: FaceMosaicHelper  // 새로 추가

    private var mode: String = "confirm"
    private var imageUrl: String? = null
    private var placeId: String? = null
    private var totalSteps: Int = 0
    private var totalDistance: Double = 0.0
    private var totalCalories: Double = 0.0

    // 이미지 비교용 데이터 추가
    private var userImagePath: String? = null
    private var similarity: Float = 0f

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

        // 이미지 비교용 뷰들 초기화 (없으면 null로 처리)
        try {
            referenceImageView = view.findViewById(R.id.ivReferenceImage)
            userImageView = view.findViewById(R.id.ivUserImage)
            similarityTextView = view.findViewById(R.id.tvSimilarity)
            imageComparisonLayout = view.findViewById(R.id.layoutImageComparison)
        } catch (e: Exception) {
            Log.d("ExploreDialog", "이미지 비교 뷰를 찾을 수 없습니다 (레이아웃 업데이트 필요)")
        }

        // Helper 클래스 초기화
        onnxHelper = OnnxHelper(requireContext())
        faceMosaicHelper = FaceMosaicHelper(requireContext())  // 새로 추가

        mode = arguments?.getString("mode") ?: "confirm"
        imageUrl = arguments?.getString("imageUrl")
        placeId = arguments?.getString("placeId")
        totalSteps = arguments?.getInt("totalSteps", 0) ?: 0
        totalDistance = arguments?.getDouble("totalDistance", 0.0) ?: 0.0
        totalCalories = arguments?.getDouble("totalCalories", 0.0) ?: 0.0

        // 이미지 비교용 데이터 가져오기
        userImagePath = arguments?.getString("userImagePath")
        similarity = arguments?.getFloat("similarity", 0f) ?: 0f

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

                // 이미지 비교 표시 (새로 추가)
                setupImageComparison()

                // ** 일반 보상 아이템 지급 로직 추가 시작 **
                giveGeneralRewardItem()
                // ** 일반 보상 아이템 지급 로직 추가 끝 **

                secondaryButton.setOnClickListener {
                    dismiss()
                    activity?.supportFragmentManager?.popBackStack()
                }
            }
        }
    }

    // 새로 추가된 메서드: 일반 보상 아이템 지급
    private fun giveGeneralRewardItem() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid

        if (uid == null) {
            Log.e("ExploreResultDialog", "❌ 사용자 인증 오류: UID가 null입니다.")
            Toast.makeText(requireContext(), "❌ 사용자 인증 오류. 보상 지급 실패.", Toast.LENGTH_SHORT).show()
            return
        }

        val userRewardRef = FirebaseFirestore.getInstance()
            .collection("Game")
            .document("users")
            .collection("userReward")
            .document(uid) // UID를 문서 ID로 사용

        userRewardRef.update("userRewardItem", FieldValue.increment(1))
            .addOnSuccessListener {
                Log.d("ExploreResultDialog", "✅ 일반 보상 아이템 1개 지급 완료!")
                // 이미 화면에는 지급 메시지가 표시되므로 추가 UI 변경은 필요 없음.
            }
            .addOnFailureListener { e ->
                Log.e("ExploreResultDialog", "❌ 일반 보상 아이템 지급 실패: ${e.message}")
                Toast.makeText(requireContext(), "❌ 일반 보상 아이템 지급 실패: ${e.message}", Toast.LENGTH_SHORT).show()

                // 문서가 없어서 업데이트에 실패한 경우, 새로 생성하는 로직 (초기값 1로)
                if (e.message?.contains("NOT_FOUND") == true || e.message?.contains("No document to update") == true) {
                    val initialRewardData = hashMapOf(
                        "userRewardItem" to 1,
                        "characterName" to "", // 필요에 따라 초기값 설정
                        "crewRewardItem" to null,
                        "level" to 0,
                        "storyLevel" to 0
                    )
                    userRewardRef.set(initialRewardData)
                        .addOnSuccessListener {
                            Log.d("ExploreResultDialog", "✅ userReward 문서 새로 생성 및 일반 보상 아이템 1개 지급 완료!")
                        }
                        .addOnFailureListener { setE ->
                            Log.e("ExploreResultDialog", "❌ userReward 문서 생성 실패: ${setE.message}")
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
                val userBitmap = loadImageFromFile(imageFile)  // 회전 보정 포함된 메서드 사용
                val referenceBitmap = loadImageFromUrl(imageUrl)

                if (userBitmap == null || referenceBitmap == null) {
                    withContext(Dispatchers.Main) {
                        showComparisonResult(false, 0f, "이미지 로드 실패")
                    }
                    return@launch
                }

                // 1단계: 유사도 비교 (기존 코드)
                val userFeatures = onnxHelper.runInference(userBitmap)
                val referenceFeatures = onnxHelper.runInference(referenceBitmap)

                if (userFeatures == null || referenceFeatures == null) {
                    withContext(Dispatchers.Main) {
                        showComparisonResult(false, 0f, "이미지 처리 실패")
                    }
                    return@launch
                }

                val similarity = OnnxComparator.cosineSimilarity(userFeatures, referenceFeatures)
                val threshold = 0.5f
                val isMatch = similarity >= threshold

                withContext(Dispatchers.Main) {
                    if (isMatch) {
                        // 2단계: 얼굴 모자이크 처리 (새로 추가)
                        processFaceMosaicAndUpload(userBitmap, similarity)
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

    // 새로 추가된 메서드: 얼굴 모자이크 처리 후 업로드
    private fun processFaceMosaicAndUpload(originalBitmap: Bitmap, similarity: Float) {
        titleTextView.text = "🎭 얼굴을 모자이크 처리하고 있어요..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 얼굴 모자이크 적용
                val mosaicBitmap = faceMosaicHelper.applyFaceMosaic(originalBitmap, mosaicSize = 15)

                // 처리된 이미지를 임시 파일로 저장
                val processedFile = if (mosaicBitmap != null) {
                    Log.d("ExploreDialog", "✅ 얼굴 모자이크 처리 성공")
                    saveBitmapToFile(mosaicBitmap, "processed_")
                } else {
                    Log.d("ExploreDialog", "❌ 얼굴 모자이크 처리 실패, 원본 사용")
                    saveBitmapToFile(originalBitmap, "original_")
                }

                if (processedFile != null) {
                    // 🔍 디버깅: 파일 교체 전후 비교
                    Log.d("ExploreDialog", "📁 기존 imageFile: ${imageFile?.absolutePath}")
                    Log.d("ExploreDialog", "📁 새로운 processedFile: ${processedFile.absolutePath}")

                    // 기존 imageFile을 처리된 파일로 교체
                    val oldFile = imageFile
                    imageFile = processedFile

                    Log.d("ExploreDialog", "🔄 imageFile 교체 완료: ${imageFile?.absolutePath}")
                    Log.d("ExploreDialog", "📏 처리된 파일 크기: ${processedFile.length() / 1024}KB")

                    withContext(Dispatchers.Main) {
                        // 3단계: Firebase 업로드 및 성공 다이얼로그에 이미지 전달
                        uploadToFirebaseWithImageComparison(processedFile, mosaicBitmap ?: originalBitmap, similarity)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        titleTextView.text = "이미지 처리 실패\n다시 시도해주세요"
                        mainActionButton.isEnabled = true
                    }
                }

            } catch (e: Exception) {
                Log.e("ExploreDialog", "얼굴 모자이크 처리 중 오류: ${e.message}")
                withContext(Dispatchers.Main) {
                    // 오류 발생시 원본 이미지로 업로드 진행
                    uploadToFirebase()
                }
            }
        }
    }

    // 새로 추가된 메서드: 이미지 비교 표시
    private fun setupImageComparison() {
        try {
            if (::imageComparisonLayout.isInitialized && ::referenceImageView.isInitialized &&
                ::userImageView.isInitialized && ::similarityTextView.isInitialized) {

                imageComparisonLayout.visibility = View.VISIBLE

                // 기준 이미지 로드 (목표 장소)
                imageUrl?.let {
                    Glide.with(this).load(it).into(referenceImageView)
                }

                // 사용자 촬영 이미지 로드 (모자이크된 이미지)
                userImagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        Glide.with(this).load(file).into(userImageView)
                    }
                }

                // 유사도 표시
                val similarityPercent = (similarity * 100).toInt()
                similarityTextView.text = "유사도: ${similarityPercent}%"
                similarityTextView.setTextColor(
                    if (similarityPercent >= 50)
                        android.graphics.Color.GREEN
                    else
                        android.graphics.Color.RED
                )

                Log.d("ExploreDialog", "이미지 비교 표시 완료 - 유사도: ${similarityPercent}%")
            } else {
                Log.d("ExploreDialog", "이미지 비교 뷰가 초기화되지 않음 - 레이아웃 업데이트 필요")
            }
        } catch (e: Exception) {
            Log.e("ExploreDialog", "이미지 비교 표시 실패: ${e.message}")
        }
    }
    private fun saveBitmapToFile(bitmap: Bitmap, prefix: String): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val file = File.createTempFile("${prefix}${timeStamp}_", ".jpg", storageDir!!)

            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }

            Log.d("ExploreDialog", "이미지 저장 완료: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("ExploreDialog", "이미지 저장 실패: ${e.message}")
            null
        }
    }

    private fun loadImageFromFile(file: File?): Bitmap? {
        return try {
            file?.let {
                val bitmap = BitmapFactory.decodeFile(it.absolutePath)

                // EXIF 회전 보정 포함
                try {
                    val exif = androidx.exifinterface.media.ExifInterface(it.absolutePath)
                    val orientation = exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED
                    )

                    val rotationDegrees = when (orientation) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }

                    if (rotationDegrees != 0f) {
                        Log.d("ExploreDialog", "📸 이미지 회전 보정: ${rotationDegrees}도")
                        val matrix = Matrix()
                        matrix.postRotate(rotationDegrees)
                        val rotatedBitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                        bitmap.recycle()
                        rotatedBitmap
                    } else {
                        bitmap
                    }
                } catch (e: Exception) {
                    Log.d("ExploreDialog", "회전 보정 실패, 원본 사용: ${e.message}")
                    bitmap
                }
            }
        } catch (e: Exception) {
            Log.e("ExploreDialog", "이미지 로딩 실패: ${e.message}")
            null
        }
    }
    private fun loadImageFromFileWithRotation(file: File?): Bitmap? {
        return try {
            file?.let {
                val bitmap = BitmapFactory.decodeFile(it.absolutePath)

                // EXIF 정보로 회전 각도 확인
                val exif = androidx.exifinterface.media.ExifInterface(it.absolutePath)
                val orientation = exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED
                )

                // 회전 각도 계산
                val rotationDegrees = when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }

                if (rotationDegrees != 0f) {
                    Log.d("ExploreDialog", "📸 이미지 회전 보정: ${rotationDegrees}도")
                    val matrix = Matrix()
                    matrix.postRotate(rotationDegrees)
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    bitmap.recycle() // 원본 메모리 해제
                    rotatedBitmap
                } else {
                    bitmap
                }
            }
        } catch (e: Exception) {
            Log.e("ExploreDialog", "이미지 로딩 실패: ${e.message}")
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
        } catch (e: Exception) { null }
    }

    private fun showComparisonResult(isSuccess: Boolean, similarity: Float, errorMessage: String?) {
        if (!isSuccess) {
            val failDialog = newInstance("fail", imageUrl ?: "", placeId ?: "", totalSteps, totalDistance, totalCalories)
            failDialog.show(parentFragmentManager, "explore_fail")
            dismiss()
        }
    }

    // 기존 uploadToFirebase 메서드 (오류 처리용)
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
                    saveImageUrlToFirestore(uri.toString())
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
    private fun uploadToFirebaseWithImageComparison(processedFile: File, mosaicBitmap: Bitmap, similarity: Float) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val fileName = "${userId}_${processedFile.name}"
        val storageRef = FirebaseStorage.getInstance().reference
            .child("exploreTempImages/$fileName")

        titleTextView.text = "📤 업로드 중..."

        storageRef.putFile(Uri.fromFile(processedFile))
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    Log.d("ExploreDialog", "🔥 Firebase Storage 업로드 성공!")
                    Log.d("ExploreDialog", "📍 Firebase URL: ${uri.toString()}")
                    saveImageUrlToFirestore(uri.toString())

                    // 이미지 비교를 위한 임시 파일 생성 (성공 다이얼로그용)
                    val tempMosaicFile = saveBitmapToFile(mosaicBitmap, "temp_mosaic_")

                    // 성공 다이얼로그에 이미지 비교 정보 전달
                    val successDialog = newInstanceWithImages(
                        mode = "success",
                        imageUrl = imageUrl ?: "",
                        placeId = placeId ?: "",
                        totalSteps = totalSteps,
                        totalDistance = totalDistance,
                        totalCalories = totalCalories,
                        userImagePath = tempMosaicFile?.absolutePath ?: "",
                        similarity = similarity
                    )
                    successDialog.show(parentFragmentManager, "explore_success")
                    dismiss()
                }
            }
            .addOnFailureListener {
                Log.e("ExploreDialog", "🔥 Firebase Storage 업로드 실패: ${it.message}")
                titleTextView.text = "업로드 실패\n다시 시도해주세요"
                mainActionButton.isEnabled = true
            }
    }

    private fun saveImageUrlToFirestore(imageDownloadUrl: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()

        if (placeId == null) {
            Log.e("Firebase", "Place ID가 null입니다. 이미지 URL을 저장할 수 없습니다.")
            return
        }

        val visitedPlaceDataRef = firestore
            .collection("Users")
            .document(userId)
            .collection("walk")
            .document("visitedPlace")
            .collection(placeId!!)
            .document("data")

        visitedPlaceDataRef.update("imgUrl", imageDownloadUrl)
            .addOnSuccessListener {
                Log.d("Firebase", "이미지 URL Firebase 저장 성공 (운동 데이터와 함께)")
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "이미지 URL Firebase 저장 실패 (운동 데이터와 함께)", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::onnxHelper.isInitialized) onnxHelper.close()
        if (::faceMosaicHelper.isInitialized) faceMosaicHelper.close()  // 새로 추가
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

        // 새로 추가된 메서드: 이미지 비교 정보 포함
        fun newInstanceWithImages(
            mode: String,
            imageUrl: String,
            placeId: String,
            totalSteps: Int,
            totalDistance: Double,
            totalCalories: Double,
            userImagePath: String,
            similarity: Float
        ): ExploreResultDialogFragment {
            return ExploreResultDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("mode", mode)
                    putString("imageUrl", imageUrl)
                    putString("placeId", placeId)
                    putInt("totalSteps", totalSteps)
                    putDouble("totalDistance", totalDistance)
                    putDouble("totalCalories", totalCalories)
                    putString("userImagePath", userImagePath)
                    putFloat("similarity", similarity)
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