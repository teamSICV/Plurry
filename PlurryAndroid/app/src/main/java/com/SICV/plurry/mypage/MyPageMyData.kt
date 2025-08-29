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

                    // 닉네임 로드
                    val name = document.getString("name")
                    loadUserName(name)

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
        db.collection("Crew").document(crewId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val mainField = document.getString("mainField")
                    showLocationInfo(mainField)
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
            updateUserName(auth.currentUser?.uid ?: "", newName)
        }
        dialog.show()
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

        Toast.makeText(activity, "이미지를 업로드하는 중...", Toast.LENGTH_SHORT).show()

        val fileName = "profile_${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("$uid/profileImg/$fileName")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val newImageUrl = downloadUrl.toString()

                    updateProfileImageInFirestore(uid, newImageUrl)
                    deleteOldProfileImage()

                    currentProfileImgUrl = newImageUrl
                    loadProfileImage(newImageUrl)

                    Toast.makeText(activity, "프로필 이미지가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("MyPageMyData", "이미지 업로드 실패", exception)
                Toast.makeText(activity, "이미지 업로드에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
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