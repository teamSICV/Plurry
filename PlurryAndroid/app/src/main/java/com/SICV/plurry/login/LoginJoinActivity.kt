package com.SICV.plurry.login

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.SICV.plurry.MainActivity
import com.SICV.plurry.R
import com.SICV.plurry.UserRewardInitializer
import com.SICV.plurry.BuildConfig
import com.SICV.plurry.security.AndroidSecurityValidator
import com.SICV.plurry.security.ValidationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class LoginJoinActivity : ComponentActivity() {

    private val PICK_IMAGE_REQUEST = 1

    private val MAX_IMAGE_SIZE = 10 * 1024 * 1024

    private lateinit var profileImage: ImageView
    private lateinit var nicknameEditText: EditText
    private lateinit var createButton: Button
    private lateinit var securityValidator: AndroidSecurityValidator

    private var imageUri: Uri? = null
    private var selectedFileName: String = ""
    private var selectedFileSize: Long = 0

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_join)

        initializeComponents()
        setupEventListeners()
        validateCurrentUser()
    }

    private fun initializeComponents() {
        profileImage = findViewById(R.id.profile)
        nicknameEditText = findViewById(R.id.textInputEditText)
        createButton = findViewById(R.id.btnMakeProfile)
        securityValidator = AndroidSecurityValidator()
    }

    private fun setupEventListeners() {
        profileImage.setOnClickListener {
            openImageChooser()
        }

        createButton.setOnClickListener {
            handleCreateProfile()
        }
    }

    // 보안: 현재 로그인된 사용자가 유효한지 검증
    private fun validateCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            logError("No authenticated user found", null)
            showError("인증 정보가 없습니다. 다시 로그인해주세요.")
            finish()
            return
        }

        // UID 보안 검증
        if (!isValidUID(currentUser.uid)) {
            logError("Invalid UID in join activity: ${currentUser.uid}", null)
            showError("유효하지 않은 사용자 정보입니다.")
            auth.signOut()
            finish()
            return
        }

        // 토큰 유효성 재검증
        currentUser.getIdToken(false)
            .addOnFailureListener { e ->
                logError("Token validation failed in join", e)
                showError("인증이 만료되었습니다. 다시 로그인해주세요.")
                auth.signOut()
                finish()
            }
    }

    override fun onBackPressed() {
        // 보안: 뒤로가기 시 완전 로그아웃
        auth.signOut()
        super.onBackPressed()
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    // 파일 정보 수집
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)

                            selectedFileName = if (displayNameIndex != -1) {
                                it.getString(displayNameIndex) ?: "unknown.jpg"
                            } else {
                                "unknown.jpg"
                            }

                            selectedFileSize = if (sizeIndex != -1) {
                                it.getLong(sizeIndex)
                            } else {
                                0L
                            }
                        }
                    }

                    // MIME 타입 확인
                    val mimeType = contentResolver.getType(uri)

                    // AndroidSecurityValidator로 이미지 검증
                    val validationResult = securityValidator.validateImageFile(
                        selectedFileName,
                        selectedFileSize,
                        mimeType
                    )

                    if (validationResult.isValid) {
                        imageUri = uri
                        profileImage.setImageURI(uri)
                        logDebug("이미지 선택 및 검증 성공: $selectedFileName")
                    } else {
                        imageUri = null
                        showError(validationResult.message)
                        logError("이미지 검증 실패: ${validationResult.message}", null)
                    }

                } catch (e: Exception) {
                    logError("이미지 처리 중 오류", e)
                    showError("이미지 파일을 처리할 수 없습니다.")
                    imageUri = null
                }
            }
        }
    }

    private fun handleCreateProfile() {
        val nickname = nicknameEditText.text.toString().trim()

        // AndroidSecurityValidator로 닉네임 검증
        val nicknameValidation = securityValidator.validateNickname(nickname)
        if (!nicknameValidation.isValid) {
            showError(nicknameValidation.message)
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            showError("사용자 인증 정보가 없습니다.")
            return
        }

        // 보안: UID 재검증
        if (!isValidUID(currentUser.uid)) {
            logError("Invalid UID during profile creation: ${currentUser.uid}", null)
            showError("유효하지 않은 사용자 정보입니다.")
            return
        }

        if (imageUri == null) {
            showDefaultImageDialog(nickname, currentUser.uid)
        } else {
            uploadProfileAndNickname(nickname, currentUser.uid)
        }
    }

    private fun showDefaultImageDialog(nickname: String, uid: String) {
        AlertDialog.Builder(this)
            .setTitle("기본 이미지 사용")
            .setMessage("이미지를 선택하지 않았습니다.\n기본 이미지를 사용하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                uploadDefaultImageAndNickname(nickname, uid)
            }
            .setNegativeButton("아니요", null)
            .show()
    }

    private fun uploadProfileAndNickname(nickname: String, uid: String) {
        if (imageUri == null) {
            showError("선택된 이미지가 없습니다.")
            return
        }

        // 재검증 (더블 체크)
        val validationResult = securityValidator.validateImageFile(
            selectedFileName,
            selectedFileSize,
            contentResolver.getType(imageUri!!)
        )

        if (!validationResult.isValid) {
            showError(validationResult.message)
            return
        }

        val sanitizedUID = securityValidator.sanitizeForFirebaseQuery(uid)
        val storageRef = storage.reference
            .child("$sanitizedUID/profileImg/profile.png")

        createButton.isEnabled = false // 중복 클릭 방지

        val uploadTask = storageRef.putFile(imageUri!!)
        uploadTask.addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val userData = createUserData(nickname, uid, downloadUri.toString())
                saveUserProfileAndInitializeReward(uid, userData)
            }.addOnFailureListener { e ->
                logError("다운로드 URL 가져오기 실패", e)
                showError("프로필 생성에 실패했습니다.")
                createButton.isEnabled = true
            }
        }.addOnFailureListener { e ->
            logError("이미지 업로드 실패", e)
            showError("이미지 업로드에 실패했습니다.")
            createButton.isEnabled = true
        }
    }

    private fun uploadDefaultImageAndNickname(nickname: String, uid: String) {
        val sanitizedUID = securityValidator.sanitizeForFirebaseQuery(uid)
        val storageRef = storage.reference
            .child("$sanitizedUID/profileImg/profile.png")

        createButton.isEnabled = false // 중복 클릭 방지

        try {
            val drawable = ContextCompat.getDrawable(this, R.drawable.basicprofile) as? BitmapDrawable
            if (drawable == null) {
                showError("기본 이미지를 로드할 수 없습니다.")
                createButton.isEnabled = true
                return
            }

            val bitmap = drawable.bitmap
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val data = baos.toByteArray()

            // 기본 이미지도 크기 검증
            if (data.size > MAX_IMAGE_SIZE) {
                showError("기본 이미지 크기가 제한을 초과합니다.")
                createButton.isEnabled = true
                return
            }

            val uploadTask = storageRef.putBytes(data)
            uploadTask.addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val userData = createUserData(nickname, uid, downloadUri.toString())
                    saveUserProfileAndInitializeReward(uid, userData)
                }.addOnFailureListener { e ->
                    logError("기본 이미지 다운로드 URL 가져오기 실패", e)
                    showError("프로필 생성에 실패했습니다.")
                    createButton.isEnabled = true
                }
            }.addOnFailureListener { e ->
                logError("기본 이미지 업로드 실패", e)
                showError("기본 이미지 업로드에 실패했습니다.")
                createButton.isEnabled = true
            }
        } catch (e: Exception) {
            logError("기본 이미지 처리 실패", e)
            showError("기본 이미지 처리에 실패했습니다.")
            createButton.isEnabled = true
        }
    }

    // 보안: 사용자 데이터 생성 (AndroidSecurityValidator 사용)
    private fun createUserData(nickname: String, uid: String, profileImageUrl: String): HashMap<String, String> {
        val currentUser = auth.currentUser
        val email = currentUser?.email ?: ""

        return hashMapOf(
            "name" to securityValidator.sanitizeForFirebaseQuery(nickname),
            "characterId" to securityValidator.sanitizeForFirebaseQuery(uid),
            "profileImg" to profileImageUrl, // URL은 Firebase에서 이미 검증됨
            "email" to securityValidator.sanitizeForFirebaseQuery(email)
        )
    }

    private fun isValidUID(uid: String): Boolean {
        return uid.isNotBlank() &&
                uid.length >= 10 &&
                uid.length <= 128 && // Firebase UID 최대 길이
                uid.matches(Regex("^[a-zA-Z0-9]+$")) &&
                !uid.lowercase().contains("admin") &&
                !uid.lowercase().contains("system")
    }

    private fun saveUserProfileAndInitializeReward(uid: String, userData: HashMap<String, String>) {
        firestore.collection("Users").document(uid)
            .update(userData.toMap())
            .addOnSuccessListener {
                logDebug("사용자 프로필 업데이트 성공")
                initializeUserReward()
            }
            .addOnFailureListener { e ->
                logError("Firestore 업데이트 실패", e)
                // 업데이트 실패 시 새로 생성 시도
                firestore.collection("Users").document(uid)
                    .set(userData)
                    .addOnSuccessListener {
                        logDebug("사용자 프로필 생성 성공")
                        initializeUserReward()
                    }
                    .addOnFailureListener { e2 ->
                        logError("Firestore 생성 실패", e2)
                        showError("프로필 저장에 실패했습니다.")
                        createButton.isEnabled = true
                    }
            }
    }

    private fun initializeUserReward() {
        UserRewardInitializer.intializeUserReward(
            onSucces = {
                logDebug("사용자 리워드 초기화 성공")
                showSuccess("프로필 생성이 완료되었습니다!")
                goToMain()
            },
            onFailure = { e ->
                logError("사용자 리워드 초기화 실패", e)
                // 리워드 초기화 실패해도 프로필은 생성되었으므로 진행
                showSuccess("프로필 생성이 완료되었습니다!")
                goToMain()
            }
        )
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun logError(message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) {
            Log.e("LoginJoin", message, throwable)
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("LoginJoin", message)
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}