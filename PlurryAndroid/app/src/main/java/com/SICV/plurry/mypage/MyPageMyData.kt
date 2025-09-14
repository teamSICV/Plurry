package com.SICV.plurry.mypage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.SICV.plurry.R
import com.SICV.plurry.security.AndroidSecurityValidator
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import de.hdodenhof.circleimageview.CircleImageView
import java.util.*

class MyPageMyData(private val activity: MyPageMainActivity) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // 보안 검증 추가
    private val securityValidator = AndroidSecurityValidator()

    private var currentProfileImgUrl: String? = null
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>

    init {
        setupGalleryLauncher()
        setupClickListeners()
    }

    private fun setupGalleryLauncher() {
        galleryLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val imageUri = result.data?.data
                if (imageUri != null) {
                    uploadProfileImage(imageUri)
                }
            }
        }
    }

    private fun setupClickListeners() {
        val profileImageView = activity.findViewById<CircleImageView>(R.id.myPageProfile)
        val nameTextView = activity.findViewById<TextView>(R.id.myPageName)

        profileImageView.setOnClickListener {
            showProfileDialog()
        }

        nameTextView.setOnClickListener {
            showNameDialog()
        }
    }

    fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("MyPageMyData", "사용자가 로그인되지 않았습니다.")
            return
        }

        val uid = currentUser.uid

        db.collection("Users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // 프로필 이미지 로드
                    val profileImg = document.getString("profileImg")
                    currentProfileImgUrl = profileImg
                    loadProfileImage(profileImg)

                    // 닉네임 로드시 보안 검증
                    val name = document.getString("name")
                    val sanitizedName = name?.let {
                        securityValidator.sanitizeForWebView(it)
                    }
                    loadUserName(sanitizedName)

                    // 크루 정보 확인
                    val crewAt = document.getString("crewAt")
                    if (crewAt != null && crewAt.isNotEmpty()) {
                        loadCrewLocation(crewAt)
                    } else {
                        hideLocationInfo()
                    }
                } else {
                    Log.d("MyPageMyData", "사용자 문서가 존재하지 않습니다.")
                    hideLocationInfo()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MyPageMyData", "사용자 정보 로드 실패", exception)
                hideLocationInfo()
            }
    }

    private fun loadCrewLocation(crewId: String) {
        // Firebase 쿼리용 ID 정제
        val sanitizedCrewId = securityValidator.sanitizeForFirebaseQuery(crewId)

        db.collection("Crew").document(sanitizedCrewId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val mainField = document.getString("mainField")
                    // 위치 정보 보안 검증
                    val sanitizedLocation = mainField?.let {
                        securityValidator.sanitizeForWebView(it)
                    }
                    showLocationInfo(sanitizedLocation)
                } else {
                    Log.d("MyPageMyData", "크루 문서가 존재하지 않습니다.")
                    hideLocationInfo()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MyPageMyData", "크루 정보 로드 실패", exception)
                hideLocationInfo()
            }
    }

    private fun showProfileDialog() {
        val dialog = MyPageProfileDialog(activity) {
            openGallery()
        }
        dialog.show()
    }

    private fun showNameDialog() {
        val currentName = activity.findViewById<TextView>(R.id.myPageName).text.toString()
        val dialog = MyPageNameDialog(activity, currentName) { newName ->
            // 새 닉네임 보안 검증
            validateAndUpdateUserName(auth.currentUser?.uid ?: "", newName)
        }
        dialog.show()
    }

    // 닉네임 보안 검증 함수
    private fun validateAndUpdateUserName(uid: String, newName: String) {
        // 보안 검증
        val validationResult = securityValidator.validateNickname(newName)

        if (!validationResult.isValid) {
            // 공격 시도 로그
            Log.w("MyPageMyData", "닉네임 보안 검증 실패: ${validationResult.message}")
            Toast.makeText(activity, validationResult.message, Toast.LENGTH_SHORT).show()
            return
        }

        val sanitizedName = securityValidator.sanitizeForFirebaseQuery(newName)

        // 업데이트 실행
        updateUserName(uid, sanitizedName)

        // 보안 이벤트 로그
        Log.i("MyPageMyData", "닉네임 업데이트 보안 검증 통과: $sanitizedName")
    }

    //프로필 변경
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        galleryLauncher.launch(intent)
    }

    private fun uploadProfileImage(imageUri: Uri) {
        val currentUser = auth.currentUser ?: return
        val uid = currentUser.uid

        // 이미지 파일 보안 검증
        val fileName = getFileNameFromUri(imageUri)
        val fileSize = getFileSizeFromUri(imageUri)

        val validationResult = securityValidator.validateImageFile(fileName, fileSize)

        if (!validationResult.isValid) {
            // 공격 시도 로그
            Log.w("MyPageMyData", "이미지 파일 보안 검증 실패: ${validationResult.message}")
            Toast.makeText(activity, validationResult.message, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(activity, "이미지를 업로드하는 중...", Toast.LENGTH_SHORT).show()

        // 안전한 파일명 생성
        val safeFileName = "profile_${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("$uid/profileImg/$safeFileName")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val newImageUrl = downloadUrl.toString()

                    updateProfileImageInFirestore(uid, newImageUrl)
                    deleteOldProfileImage()

                    currentProfileImgUrl = newImageUrl
                    loadProfileImage(newImageUrl)

                    Toast.makeText(activity, "프로필 이미지가 변경되었습니다.", Toast.LENGTH_SHORT).show()

                    // 보안 이벤트 로그
                    Log.i("MyPageMyData", "프로필 이미지 업로드 보안 검증 통과")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MyPageMyData", "이미지 업로드 실패", exception)
                Toast.makeText(activity, "이미지 업로드에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    // 파일 정보 추출 함수
    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "unknown.jpg"
        try {
            val cursor = activity.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex) ?: "unknown.jpg"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MyPageMyData", "파일명 추출 실패", e)
        }
        return fileName
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        var fileSize = 0L
        try {
            val cursor = activity.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        fileSize = it.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MyPageMyData", "파일 크기 추출 실패", e)
        }
        return fileSize
    }

    private fun updateProfileImageInFirestore(uid: String, newImageUrl: String) {
        db.collection("Users").document(uid)
            .update("profileImg", newImageUrl)
            .addOnSuccessListener {
                Log.d("MyPageMyData", "Firestore 프로필 이미지 업데이트 성공")
            }
            .addOnFailureListener { exception ->
                Log.e("MyPageMyData", "Firestore 프로필 이미지 업데이트 실패", exception)
            }
    }

    private fun deleteOldProfileImage() {
        if (currentProfileImgUrl != null && currentProfileImgUrl!!.contains("firebasestorage")) {
            try {
                val oldImageRef = storage.getReferenceFromUrl(currentProfileImgUrl!!)
                oldImageRef.delete()
                    .addOnSuccessListener {
                        Log.d("MyPageMyData", "기존 프로필 이미지 삭제 성공")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("MyPageMyData", "기존 프로필 이미지 삭제 실패", exception)
                    }
            } catch (e: Exception) {
                Log.e("MyPageMyData", "기존 이미지 URL 파싱 실패", e)
            }
        }
    }

    // 닉네임 변경
    private fun updateUserName(uid: String, newName: String) {
        db.collection("Users").document(uid)
            .update("name", newName)
            .addOnSuccessListener {
                val nameTextView = activity.findViewById<TextView>(R.id.myPageName)
                nameTextView.text = newName
                Toast.makeText(activity, "닉네임이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                Log.d("MyPageMyData", "닉네임 업데이트 성공")
            }
            .addOnFailureListener { exception ->
                Log.e("MyPageMyData", "닉네임 업데이트 실패", exception)
                Toast.makeText(activity, "닉네임 변경에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadProfileImage(profileImgUrl: String?) {
        val profileImageView = activity.findViewById<CircleImageView>(R.id.myPageProfile)

        if (profileImgUrl != null && profileImgUrl.isNotEmpty()) {
            Glide.with(activity)
                .load(profileImgUrl)
                .placeholder(R.drawable.basicprofile)
                .error(R.drawable.basicprofile)
                .into(profileImageView)
        } else {
            profileImageView.setImageResource(R.drawable.basicprofile)
        }
    }

    private fun loadUserName(name: String?) {
        val nameTextView = activity.findViewById<TextView>(R.id.myPageName)
        nameTextView.text = name ?: "이름 없음"
    }

    private fun showLocationInfo(location: String?) {
        val locationNameTextView = activity.findViewById<TextView>(R.id.myPageLocationName)
        val locationTextView = activity.findViewById<TextView>(R.id.myPageLocation)

        locationNameTextView.visibility = View.VISIBLE
        locationTextView.visibility = View.VISIBLE
        locationTextView.text = location ?: "위치 정보 없음"
    }

    private fun hideLocationInfo() {
        val locationNameTextView = activity.findViewById<TextView>(R.id.myPageLocationName)
        val locationTextView = activity.findViewById<TextView>(R.id.myPageLocation)

        locationNameTextView.visibility = View.GONE
        locationTextView.visibility = View.GONE
    }
}