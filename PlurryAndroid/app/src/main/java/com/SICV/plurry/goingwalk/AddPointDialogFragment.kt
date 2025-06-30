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

class AddPointDialogFragment : DialogFragment() {

    private val CAMERA_REQUEST_CODE = 101
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var imageUri: Uri
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isUploading = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.activity_goingwalk_dialog_add_point, null)
        val builder = AlertDialog.Builder(requireActivity()).setView(view)

        val nameInputLayout = view.findViewById<LinearLayout>(R.id.nameInputLayout)
        val etPlaceName = view.findViewById<EditText>(R.id.etPlaceName)
        val btnSubmitName = view.findViewById<Button>(R.id.btnSubmitName)
        val imagePreview = view.findViewById<ImageView>(R.id.imagePreview)
        val btnTakePhoto = view.findViewById<Button>(R.id.btnTakePhoto)
        val btnRetake = view.findViewById<Button>(R.id.btnRetake)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirm)
        val photoActions = view.findViewById<LinearLayout>(R.id.photoActionButtons)
        val btnClose = view.findViewById<Button>(R.id.btnClose)

        val completionLayout = view.findViewById<LinearLayout>(R.id.completionLayout)
        val tvReward = view.findViewById<TextView>(R.id.tvReward)
        val btnDone = view.findViewById<Button>(R.id.btnDone)

        val progressLayout = view.findViewById<LinearLayout>(R.id.progressLayout)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        btnTakePhoto.visibility = View.GONE
        btnClose.visibility = View.GONE

        btnSubmitName.setOnClickListener {
            val placeName = etPlaceName.text.toString().trim()
            if (placeName.isNotEmpty()) {
                nameInputLayout.visibility = View.GONE
                btnTakePhoto.visibility = View.VISIBLE
                btnClose.visibility = View.VISIBLE
            } else {
                Toast.makeText(requireContext(), "포인트 이름을 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }

        btnTakePhoto.setOnClickListener {
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
                                        "name" to etPlaceName.text.toString().trim(),
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
                                            completionLayout.visibility = View.VISIBLE
                                            tvReward.text = "일반 보상 아이템 지급!"

                                            nameInputLayout.visibility = View.GONE
                                            btnSubmitName.visibility = View.GONE
                                            btnTakePhoto.visibility = View.GONE
                                            btnRetake.visibility = View.GONE
                                            btnConfirm.visibility = View.GONE
                                            btnClose.visibility = View.GONE
                                            photoActions.visibility = View.GONE
                                            progressLayout.visibility = View.GONE

                                            isUploading = false
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
        }
    }
}
