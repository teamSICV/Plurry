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
import com.google.firebase.firestore.FieldValue // FieldValue를 가져와서 증가시킵니다.
import android.util.Log // Log 임포트 추가

class AddPointDialogFragment : DialogFragment() {

    private val CAMERA_REQUEST_CODE = 101
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var imageUri: Uri
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isUploading = false

    // `currentPlaceName` 변수는 더 이상 필요 없으므로 제거됨

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.activity_goingwalk_dialog_add_point, null)
        val builder = AlertDialog.Builder(requireActivity()).setView(view)

        val nameInputLayout = view.findViewById<LinearLayout>(R.id.nameInputLayout)
        val etPlaceName = view.findViewById<EditText>(R.id.etPlaceName)
        // `btnSubmitName`은 XML에서 제거되었으므로 여기서도 제거
        val imagePreview = view.findViewById<ImageView>(R.id.imagePreview)
        val btnTakePhoto = view.findViewById<Button>(R.id.btnTakePhoto)
        val btnRetake = view.findViewById<Button>(R.id.btnRetake)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirm)
        val photoActions = view.findViewById<LinearLayout>(R.id.photoActionButtons)
        val btnClose = view.findViewById<Button>(R.id.btnClose)

        // `etPlaceNameDisplay`와 `tvPlaceNameLabel`은 더 이상 필요 없으므로 제거됨
        // val etPlaceNameDisplay = view.findViewById<EditText>(R.id.etPlaceNameDisplay)
        // val tvPlaceNameLabel = view.findViewById<TextView>(R.id.tvPlaceNameLabel)

        val completionLayout = view.findViewById<LinearLayout>(R.id.completionLayout)
        val tvReward = view.findViewById<TextView>(R.id.tvReward)
        val btnDone = view.findViewById<Button>(R.id.btnDone)

        val progressLayout = view.findViewById<LinearLayout>(R.id.progressLayout)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // 초기 가시성 설정: 이름 입력 필드와 사진 찍기 버튼은 항상 보임
        // `btnTakePhoto`는 XML에서 기본적으로 보이도록 설정되었으므로 여기서 `VISIBLE`로 설정할 필요 없음
        btnClose.visibility = View.GONE // 닫기 버튼은 사진 촬영 단계에서 나타남
        photoActions.visibility = View.GONE // 사진 촬영 전에는 액션 버튼 숨김
        imagePreview.visibility = View.GONE // 사진 미리보기는 사진 촬영 전에는 숨김

        // `btnSubmitName` 클릭 리스너는 더 이상 필요 없으므로 제거됨
        // 이름 입력은 `etPlaceName`에서 직접 이루어지고, 사진 촬영 버튼과 함께 제공됩니다.

        btnTakePhoto.setOnClickListener {
            // 이름 입력 필드가 비어있는지 확인
            val placeName = etPlaceName.text.toString().trim()
            if (placeName.isEmpty()) {
                Toast.makeText(requireContext(), "포인트 이름을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openCamera()
        }

        btnRetake.setOnClickListener {
            openCamera()
        }

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
                                                    etPlaceName.visibility = View.GONE // 이름 입력 필드 숨김
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
                                                                etPlaceName.visibility = View.GONE // 이름 입력 필드 숨김
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
                                                                // 문서 생성 실패 시에만 토스트 메시지 표시:
                                                                Toast.makeText(requireContext(), "❌ 보상 아이템 지급 최종 실패: ${setE.message}", Toast.LENGTH_SHORT).show()
                                                                isUploading = false
                                                                progressLayout.visibility = View.GONE
                                                                btnConfirm.isEnabled = true
                                                            }
                                                    } else { // 다른 종류의 업데이트 실패 오류인 경우
                                                        // 예상치 못한 다른 실패 시에만 토스트 메시지 표시:
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

        btnClose.setOnClickListener {
            dismiss()
        }

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
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            dialog?.findViewById<ImageView>(R.id.imagePreview)?.apply {
                setImageURI(imageUri)
                visibility = View.VISIBLE
            }
            dialog?.findViewById<Button>(R.id.btnTakePhoto)?.visibility = View.GONE
            dialog?.findViewById<LinearLayout>(R.id.photoActionButtons)?.visibility = View.VISIBLE
            dialog?.findViewById<Button>(R.id.btnClose)?.visibility = View.VISIBLE // 닫기 버튼 표시
            // 이름 입력 필드의 포커스를 해제하여 키보드가 자동으로 올라오지 않도록 함
            dialog?.findViewById<EditText>(R.id.etPlaceName)?.clearFocus()
        }
    }
}
