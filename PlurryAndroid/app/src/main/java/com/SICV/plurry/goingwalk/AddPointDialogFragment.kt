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

class AddPointDialogFragment : DialogFragment() {

    private val CAMERA_REQUEST_CODE = 101
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var imageUri: Uri
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isUploading = false

    // UI 요소들을 전역 변수로 선언하여 onActivityResult에서 접근 가능하도록 합니다.
    private lateinit var nameInputLayout: LinearLayout
    private lateinit var etPlaceName: EditText
    private lateinit var imagePreview: ImageView
    private lateinit var btnTakePhoto: Button // 이제 이 버튼은 초기 카메라 실행 버튼 역할만 합니다.
    private lateinit var btnRetake: Button
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
        btnTakePhoto = view.findViewById(R.id.btnTakePhoto)
        btnRetake = view.findViewById(R.id.btnRetake)
        btnConfirm = view.findViewById(R.id.btnConfirm)
        photoActions = view.findViewById(R.id.photoActionButtons)
        btnClose = view.findViewById(R.id.btnClose)
        completionLayout = view.findViewById(R.id.completionLayout)
        tvReward = view.findViewById(R.id.tvReward)
        btnDone = view.findViewById(R.id.btnDone)
        progressLayout = view.findViewById(R.id.progressLayout)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // 초기 가시성 설정: 이름 입력 필드와 사진 관련 UI를 모두 숨깁니다.
        nameInputLayout.visibility = View.GONE
        etPlaceName.visibility = View.GONE
        imagePreview.visibility = View.GONE
        photoActions.visibility = View.GONE
        btnTakePhoto.visibility = View.GONE // 초기에는 이 버튼도 숨깁니다.
        btnClose.visibility = View.GONE // 닫기 버튼은 완료 화면에서 나타납니다.
        completionLayout.visibility = View.GONE
        progressLayout.visibility = View.GONE

        // 다이얼로그가 열리자마자 카메라를 엽니다.
        openCamera()

        // '다시 찍기' 버튼 클릭 리스너
        btnRetake.setOnClickListener {
            openCamera()
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
                isUploading = false
                progressLayout.visibility = View.GONE
                btnConfirm.isEnabled = true
                return@setOnClickListener
            }

            // 이미지 URI가 설정되었는지 확인
            if (!::imageUri.isInitialized) {
                Toast.makeText(requireContext(), "사진을 촬영해주세요.", Toast.LENGTH_SHORT).show()
                isUploading = false
                progressLayout.visibility = View.GONE
                btnConfirm.isEnabled = true
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
                isUploading = false
                progressLayout.visibility = View.GONE
                btnConfirm.isEnabled = true
                return@setOnClickListener
            }

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        val uid = currentUser?.uid

                        if (uid == null) {
                            Toast.makeText(requireContext(), "❌ 사용자 인증 오류", Toast.LENGTH_SHORT).show()
                            isUploading = false
                            progressLayout.visibility = View.GONE
                            btnConfirm.isEnabled = true
                            return@addOnSuccessListener
                        }

                        val usersRef = FirebaseFirestore.getInstance().collection("Users").document(uid)
                        usersRef.get().addOnSuccessListener { userDoc ->
                            val characterId = userDoc.getString("characterId")

                            if (characterId == null) {
                                Toast.makeText(requireContext(), "❌ 사용자 정보 없음 (characterId)", Toast.LENGTH_SHORT).show()
                                isUploading = false
                                progressLayout.visibility = View.GONE
                                btnConfirm.isEnabled = true
                                return@addOnSuccessListener
                            }

                            val storageRef = FirebaseStorage.getInstance().reference
                            val timeStamp = System.currentTimeMillis()
                            val imageRef = storageRef.child("places/${timeStamp}.jpg")

                            imageRef.putFile(imageUri).addOnSuccessListener {
                                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->

                                    val placeData = hashMapOf(
                                        "name" to finalPlaceName, // `etPlaceName`에서 이름 가져옴
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
                                                .document(uid) // UID를 문서 ID로 사용

                                            userRewardRef.update("userRewardItem", FieldValue.increment(1))
                                                .addOnSuccessListener {
                                                    Log.d("AddPointDialog", "✅ 일반 보상 아이템 1개 지급 완료!")
                                                    completionLayout.visibility = View.VISIBLE
                                                    tvReward.text = "일반 보상 아이템 1개 지급"

                                                    // 모든 이전 UI 요소 숨김
                                                    nameInputLayout.visibility = View.GONE
                                                    etPlaceName.visibility = View.GONE
                                                    btnTakePhoto.visibility = View.GONE
                                                    btnRetake.visibility = View.GONE
                                                    btnConfirm.visibility = View.GONE
                                                    btnClose.visibility = View.GONE
                                                    photoActions.visibility = View.GONE
                                                    progressLayout.visibility = View.GONE
                                                    imagePreview.visibility = View.GONE

                                                    isUploading = false
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e("AddPointDialog", "❌ 일반 보상 아이템 지급 실패 (업데이트): ${e.message}")
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
                                                                Log.d("AddPointDialog", "✅ userReward 문서 새로 생성 및 일반 보상 아이템 1개 지급 완료!")
                                                                // 문서 생성 성공 시에도 UI 업데이트
                                                                completionLayout.visibility = View.VISIBLE
                                                                tvReward.text = "일반 보상 아이템 1개 지급"

                                                                // 모든 이전 UI 요소 숨김
                                                                nameInputLayout.visibility = View.GONE
                                                                etPlaceName.visibility = View.GONE
                                                                btnTakePhoto.visibility = View.GONE
                                                                btnRetake.visibility = View.GONE
                                                                btnConfirm.visibility = View.GONE
                                                                btnClose.visibility = View.GONE
                                                                photoActions.visibility = View.GONE
                                                                progressLayout.visibility = View.GONE
                                                                imagePreview.visibility = View.GONE

                                                                isUploading = false
                                                            }
                                                            .addOnFailureListener { setE ->
                                                                Log.e("AddPointDialog", "❌ userReward 문서 생성 실패: ${setE.message}")
                                                                Toast.makeText(requireContext(), "❌ 보상 아이템 지급 최종 실패: ${setE.message}", Toast.LENGTH_SHORT).show()
                                                                isUploading = false
                                                                progressLayout.visibility = View.GONE
                                                                btnConfirm.isEnabled = true
                                                            }
                                                    } else { // 다른 종류의 업데이트 실패 오류인 경우
                                                        Toast.makeText(requireContext(), "❌ 보상 아이템 지급 알 수 없는 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        isUploading = false
                                                        progressLayout.visibility = View.GONE
                                                        btnConfirm.isEnabled = true
                                                    }
                                                }
                                            // **보상 로직 끝**

                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(requireContext(), "❌ Firestore 저장 실패", Toast.LENGTH_SHORT).show()
                                            isUploading = false
                                            progressLayout.visibility = View.GONE
                                            btnConfirm.isEnabled = true
                                        }

                                }.addOnFailureListener {
                                    Toast.makeText(requireContext(), "❌ URL 획득 실패", Toast.LENGTH_SHORT).show()
                                    isUploading = false
                                    progressLayout.visibility = View.GONE
                                    btnConfirm.isEnabled = true
                                }
                            }.addOnFailureListener {
                                Toast.makeText(requireContext(), "❌ 사진 업로드 실패", Toast.LENGTH_SHORT).show()
                                isUploading = false
                                progressLayout.visibility = View.GONE
                                btnConfirm.isEnabled = true
                            }

                        }.addOnFailureListener {
                            Toast.makeText(requireContext(), "❌ 사용자 정보 로드 실패", Toast.LENGTH_SHORT).show()
                            isUploading = false
                            progressLayout.visibility = View.GONE
                            btnConfirm.isEnabled = true
                        }

                    } else {
                        Toast.makeText(requireContext(), "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                        isUploading = false
                        progressLayout.visibility = View.GONE
                        btnConfirm.isEnabled = true
                    }
                }.addOnFailureListener {
                    Toast.makeText(requireContext(), "위치 획득 실패", Toast.LENGTH_SHORT).show()
                    isUploading = false
                    progressLayout.visibility = View.GONE
                    btnConfirm.isEnabled = true
                }
        }

        // '닫기' 버튼 클릭 리스너
        btnClose.setOnClickListener {
            dismiss()
        }

        // '완료' 버튼 클릭 리스너 (보상 화면에서)
        btnDone.setOnClickListener {
            dismiss()
        }

        return builder.create()
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

                nameInputLayout.visibility = View.VISIBLE // 이름 입력 필드 표시
                etPlaceName.visibility = View.VISIBLE
                etPlaceName.requestFocus() // 이름 입력 필드에 포커스

                photoActions.visibility = View.VISIBLE // '다시 찍기', '촬영 완료' 버튼 표시
                btnTakePhoto.visibility = View.GONE // 초기 '사진 찍기' 버튼 숨김
                btnClose.visibility = View.VISIBLE // 닫기 버튼 표시

            } else if (resultCode == Activity.RESULT_CANCELED) {
                // 사용자가 카메라 앱에서 취소했을 경우 다이얼로그 닫기
                dismiss()
                Toast.makeText(requireContext(), "사진 촬영이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
