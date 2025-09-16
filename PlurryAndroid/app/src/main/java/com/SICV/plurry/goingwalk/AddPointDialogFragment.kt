package com.SICV.plurry.goingwalk

import com.SICV.plurry.R
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.core.content.FileProvider
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.firestore.FieldValue
import android.util.Log
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
// 얼굴 모자이크 처리를 위한 추가 import
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.SICV.plurry.onnx.FaceMosaicHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class AddPointDialogFragment : DialogFragment() {

    private val CAMERA_REQUEST_CODE = 101
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var imageUri: Uri
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isUploading = false

    // 얼굴 모자이크 처리를 위한 Helper 추가
    private lateinit var faceMosaicHelper: FaceMosaicHelper
    private var capturedImageFile: File? = null // 촬영된 원본 이미지 파일

    // UI 요소들을 전역 변수로 선언하여 onActivityResult에서 접근 가능하도록 합니다.
    private lateinit var nameInputLayout: LinearLayout
    private lateinit var etPlaceName: EditText
    private lateinit var imagePreview: ImageView
    private lateinit var btnConfirm: Button
    private lateinit var photoActions: LinearLayout
    private lateinit var btnClose: Button
    private lateinit var completionLayout: LinearLayout
    private lateinit var tvReward: TextView
    private lateinit var btnDone: Button
    private lateinit var progressLayout: LinearLayout

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.activity_goingwalk_dialog_add_point, null)
        val builder = AlertDialog.Builder(requireActivity()).setView(view)

        // UI 요소 초기화
        nameInputLayout = view.findViewById(R.id.nameInputLayout)
        etPlaceName = view.findViewById(R.id.etPlaceName)
        imagePreview = view.findViewById(R.id.imagePreview)
        btnConfirm = view.findViewById(R.id.btnConfirm)
        photoActions = view.findViewById(R.id.photoActionButtons)
        btnClose = view.findViewById(R.id.btnClose)
        completionLayout = view.findViewById(R.id.completionLayout)
        tvReward = view.findViewById(R.id.tvReward)
        btnDone = view.findViewById(R.id.btnDone)
        progressLayout = view.findViewById(R.id.progressLayout)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // 얼굴 모자이크 헬퍼 초기화
        faceMosaicHelper = FaceMosaicHelper(requireContext())

        // 초기 가시성 설정: 이름 입력 필드와 사진 관련 UI를 모두 숨깁니다.
        nameInputLayout.visibility = View.GONE
        etPlaceName.visibility = View.GONE
        imagePreview.visibility = View.GONE
        photoActions.visibility = View.GONE
        btnClose.visibility = View.GONE
        completionLayout.visibility = View.GONE
        progressLayout.visibility = View.GONE

        // 다이얼로그가 열리자마자 카메라를 엽니다.
        openCamera()

        // 사진 미리보기 클릭 리스너를 추가하여 재촬영 여부를 묻는 Dialog를 띄웁니다.
        imagePreview.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage("사진을 재촬영하시겠습니까?")
                .setPositiveButton("재촬영") { dialog, which ->
                    openCamera()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        // '촬영 완료' 버튼 클릭 리스너
        btnConfirm.setOnClickListener {
            if (isUploading) return@setOnClickListener
            isUploading = true

            // 중복 클릭 방지 + 로딩 표시
            btnConfirm.isEnabled = false
            progressLayout.visibility = View.VISIBLE

            // 최종적으로 등록될 포인트 이름은 `etPlaceName`에서 가져옴
            val finalPlaceName = etPlaceName.text.toString().trim()
            if (finalPlaceName.isEmpty()) {
                Toast.makeText(requireContext(), "포인트 이름을 입력하세요.", Toast.LENGTH_SHORT).show()
                resetUploadState()
                return@setOnClickListener
            }

            // 이미지 URI가 설정되었는지 확인
            if (!::imageUri.isInitialized || capturedImageFile == null) {
                Toast.makeText(requireContext(), "사진을 촬영해주세요.", Toast.LENGTH_SHORT).show()
                resetUploadState()
                return@setOnClickListener
            }

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
                resetUploadState()
                return@setOnClickListener
            }

            // 얼굴 모자이크 처리 후 업로드
            processFaceMosaicAndUpload(finalPlaceName)
        }

        // '닫기' 버튼 클릭 리스너
        btnClose.setOnClickListener {
            dismiss()
        }

        // '완료' 버튼 클릭 리스너 (보상 화면에서)
        btnDone.setOnClickListener {
            dismiss()
        }

        //배경투명화
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    private fun resetUploadState() {
        isUploading = false
        progressLayout.visibility = View.GONE
        btnConfirm.isEnabled = true
    }

    private fun processFaceMosaicAndUpload(placeName: String) {
        // UI 상태 업데이트
        val originalText = "처리 중..."
        progressLayout.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 원본 이미지 로드
                val originalBitmap = loadImageFromFile(capturedImageFile)
                if (originalBitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "이미지 로드 실패", Toast.LENGTH_SHORT).show()
                        resetUploadState()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    // Progress UI 업데이트 (얼굴 모자이크 처리 단계)
                    Log.d("AddPointDialog", "🎭 얼굴 모자이크 처리 시작...")
                }

                // 이미지를 작게 만들어 처리 (성능 최적화)
                val smallBitmap = makeImageSmaller(originalBitmap)
                Log.d("AddPointDialog", "작은 이미지 크기: ${smallBitmap.width} x ${smallBitmap.height}")

                // 작은 이미지로 얼굴 모자이크 처리
                var mosaicBitmap = faceMosaicHelper.applyFaceMosaic(smallBitmap, mosaicSize = 10)

                if (mosaicBitmap != null) {
                    Log.d("AddPointDialog", "✅ 얼굴 탐지 성공!")
                    // 원본 크기로 복원 (필터 off로 픽셀 보존)
                    mosaicBitmap = Bitmap.createScaledBitmap(
                        mosaicBitmap,
                        originalBitmap.width,
                        originalBitmap.height,
                        /* filter = */ false
                    )
                } else {
                    Log.w("AddPointDialog", "⚠️ 얼굴 탐지 실패, 간단한 모자이크 적용")
                    mosaicBitmap = applySimpleBlur(originalBitmap)
                }

                // 처리된 이미지를 임시 파일로 저장
                val processedFile = if (mosaicBitmap != null) {
                    Log.d("AddPointDialog", "✅ 얼굴 모자이크 처리 성공")
                    saveBitmapToFile(mosaicBitmap, "processed_")
                } else {
                    Log.d("AddPointDialog", "❌ 얼굴 모자이크 처리 실패, 원본 사용")
                    capturedImageFile
                }

                if (processedFile != null) {
                    withContext(Dispatchers.Main) {
                        uploadToFirebase(processedFile, placeName)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "이미지 처리 실패", Toast.LENGTH_SHORT).show()
                        resetUploadState()
                    }
                }

            } catch (e: Exception) {
                Log.e("AddPointDialog", "얼굴 모자이크 처리 중 오류: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "이미지 처리 중 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetUploadState()
                }
            }
        }
    }

    private fun makeImageSmaller(bitmap: Bitmap): Bitmap {
        val maxSize = 1000
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap

        val scale = if (width > height) maxSize.toFloat() / width else maxSize.toFloat() / height
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun applySimpleBlur(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint()

        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 4
        val blurWidth = bitmap.width / 3
        val blurHeight = bitmap.height / 5

        val left = centerX - blurWidth / 2
        val top = centerY - blurHeight / 2
        val right = centerX + blurWidth / 2
        val bottom = centerY + blurHeight / 2

        val blockSize = 20
        for (y in top until bottom step blockSize) {
            for (x in left until right step blockSize) {
                val endX = kotlin.math.min(x + blockSize, right)
                val endY = kotlin.math.min(y + blockSize, bottom)
                if (x < bitmap.width && y < bitmap.height) {
                    val avgColor = bitmap.getPixel(x, y)
                    paint.color = avgColor
                    canvas.drawRect(x.toFloat(), y.toFloat(), endX.toFloat(), endY.toFloat(), paint)
                }
            }
        }
        Log.d("SimpleMosaic", "간단한 모자이크 적용 완료: ($left, $top) ~ ($right, $bottom)")
        return result
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
                        Log.d("AddPointDialog", "📸 이미지 회전 보정: ${rotationDegrees}도")
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
                    Log.d("AddPointDialog", "회전 보정 실패, 원본 사용: ${e.message}")
                    bitmap
                }
            }
        } catch (e: Exception) {
            Log.e("AddPointDialog", "이미지 로딩 실패: ${e.message}")
            null
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

            Log.d("AddPointDialog", "이미지 저장 완료: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("AddPointDialog", "이미지 저장 실패: ${e.message}")
            null
        }
    }

    private fun uploadToFirebase(processedFile: File, placeName: String) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val uid = currentUser?.uid

                    if (uid == null) {
                        Toast.makeText(requireContext(), "❌ 사용자 인증 오류", Toast.LENGTH_SHORT).show()
                        resetUploadState()
                        return@addOnSuccessListener
                    }

                    val usersRef = FirebaseFirestore.getInstance().collection("Users").document(uid)
                    usersRef.get().addOnSuccessListener { userDoc ->
                        val characterId = userDoc.getString("characterId")

                        if (characterId == null) {
                            Toast.makeText(requireContext(), "❌ 사용자 정보 없음 (characterId)", Toast.LENGTH_SHORT).show()
                            resetUploadState()
                            return@addOnSuccessListener
                        }

                        val storageRef = FirebaseStorage.getInstance().reference
                        val timeStamp = System.currentTimeMillis()
                        val imageRef = storageRef.child("places/${timeStamp}.jpg")

                        // 처리된 파일을 Firebase Storage에 업로드
                        imageRef.putFile(Uri.fromFile(processedFile)).addOnSuccessListener {
                            imageRef.downloadUrl.addOnSuccessListener { downloadUri ->

                                val placeData = hashMapOf(
                                    "name" to placeName,
                                    "geo" to GeoPoint(location.latitude, location.longitude),
                                    "imageTime" to timeStamp,
                                    "myImg" to true,
                                    "myImgUrl" to downloadUri.toString(),
                                    "addedBy" to characterId
                                )

                                FirebaseFirestore.getInstance()
                                    .collection("Places")
                                    .add(placeData)
                                    .addOnSuccessListener {
                                        // **보상 로직 시작**
                                        val userRewardRef = FirebaseFirestore.getInstance()
                                            .collection("Game")
                                            .document("users")
                                            .collection("userReward")
                                            .document(uid)

                                        userRewardRef.update("userRewardItem", FieldValue.increment(1))
                                            .addOnSuccessListener {
                                                Log.d("AddPointDialog", "✅ 일반 보상 아이템 1개 지급 완료!")
                                                showCompletionUI()
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("AddPointDialog", "❌ 일반 보상 아이템 지급 실패 (업데이트): ${e.message}")
                                                handleRewardFailure(e, userRewardRef)
                                            }
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(requireContext(), "❌ Firestore 저장 실패", Toast.LENGTH_SHORT).show()
                                        resetUploadState()
                                    }

                            }.addOnFailureListener {
                                Toast.makeText(requireContext(), "❌ URL 획득 실패", Toast.LENGTH_SHORT).show()
                                resetUploadState()
                            }
                        }.addOnFailureListener {
                            Toast.makeText(requireContext(), "❌ 사진 업로드 실패", Toast.LENGTH_SHORT).show()
                            resetUploadState()
                        }

                    }.addOnFailureListener {
                        Toast.makeText(requireContext(), "❌ 사용자 정보 로드 실패", Toast.LENGTH_SHORT).show()
                        resetUploadState()
                    }

                } else {
                    Toast.makeText(requireContext(), "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                    resetUploadState()
                }
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "위치 획득 실패", Toast.LENGTH_SHORT).show()
                resetUploadState()
            }
    }

    private fun handleRewardFailure(e: Exception, userRewardRef: com.google.firebase.firestore.DocumentReference) {
        // 문서가 없어서 업데이트에 실패한 경우, 새로 생성하는 로직 (초기값 1로)
        if (e.message?.contains("NOT_FOUND") == true || e.message?.contains("No document to update") == true) {
            val initialRewardData = hashMapOf(
                "userRewardItem" to 1,
                "characterName" to "",
                "crewRewardItem" to null,
                "level" to 0,
                "storyLevel" to 0
            )
            userRewardRef.set(initialRewardData)
                .addOnSuccessListener {
                    Log.d("AddPointDialog", "✅ userReward 문서 새로 생성 및 일반 보상 아이템 1개 지급 완료!")
                    showCompletionUI()
                }
                .addOnFailureListener { setE ->
                    Log.e("AddPointDialog", "❌ userReward 문서 생성 실패: ${setE.message}")
                    Toast.makeText(requireContext(), "❌ 보상 아이템 지급 최종 실패: ${setE.message}", Toast.LENGTH_SHORT).show()
                    resetUploadState()
                }
        } else {
            Toast.makeText(requireContext(), "❌ 보상 아이템 지급 알 수 없는 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            resetUploadState()
        }
    }

    private fun showCompletionUI() {
        completionLayout.visibility = View.VISIBLE
        tvReward.text = "일반 보상 아이템 1개 지급"

        // 모든 이전 UI 요소 숨김
        nameInputLayout.visibility = View.GONE
        etPlaceName.visibility = View.GONE
        photoActions.visibility = View.GONE
        btnConfirm.visibility = View.GONE
        btnClose.visibility = View.GONE
        progressLayout.visibility = View.GONE
        imagePreview.visibility = View.GONE

        isUploading = false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "위치 권한 허용됨. 다시 촬영 완료를 눌러주세요.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                val photoFile = createImageFile()
                if (photoFile == null) {
                    Toast.makeText(requireContext(), "❌ 사진 파일 생성 실패!", Toast.LENGTH_SHORT).show()
                    return
                }

                capturedImageFile = photoFile // 원본 이미지 파일 참조 저장
                val authority = "${requireContext().packageName}.fileprovider"
                imageUri = FileProvider.getUriForFile(requireContext(), authority, photoFile)

                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)

                startActivityForResult(intent, CAMERA_REQUEST_CODE)
            } else {
                Toast.makeText(requireContext(), "❌ 카메라 앱 없음", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "🚨오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return if (storageDir != null) {
            File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        } else {
            Toast.makeText(requireContext(), "저장소를 찾을 수 없습니다!", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                // 사진 촬영 성공 시 UI 업데이트
                imagePreview.setImageURI(imageUri)
                imagePreview.visibility = View.VISIBLE

                nameInputLayout.visibility = View.VISIBLE
                etPlaceName.visibility = View.VISIBLE
                etPlaceName.requestFocus()

                photoActions.visibility = View.VISIBLE
                btnClose.visibility = View.VISIBLE

            } else if (resultCode == Activity.RESULT_CANCELED) {
                // 사용자가 카메라 앱에서 취소했을 경우 다이얼로그 닫기
                dismiss()
                Toast.makeText(requireContext(), "사진 촬영이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 리소스 정리
        if (::faceMosaicHelper.isInitialized) {
            faceMosaicHelper.close()
        }
    }
}