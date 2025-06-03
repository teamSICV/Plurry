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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class LoginJoinActivity : ComponentActivity() {

    private val PICK_IMAGE_REQUEST = 1

    private lateinit var profileImage: ImageView
    private lateinit var nicknameEditText: EditText
    private lateinit var createButton: Button

    private var imageUri: Uri? = null

    private val storage = FirebaseStorage.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_join)

        profileImage = findViewById(R.id.profile)
        nicknameEditText = findViewById(R.id.textInputEditText)
        createButton = findViewById(R.id.btnMakeProfile)

        profileImage.setOnClickListener {
            openImageChooser()
        }

        createButton.setOnClickListener {
            val nickname = nicknameEditText.text.toString().trim()
            if (nickname.isEmpty()) {
                Toast.makeText(this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = auth.currentUser
            if (currentUser == null) {
                Toast.makeText(this, "사용자 인증 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (imageUri == null) {
                AlertDialog.Builder(this)
                    .setTitle("기본 이미지 사용")
                    .setMessage("이미지를 선택하지 않았습니다.\n기본 이미지를 사용하시겠습니까?")
                    .setPositiveButton("예") { _, _ ->
                        uploadDefaultImageAndNickname(nickname, currentUser.uid)
                    }
                    .setNegativeButton("아니요", null)
                    .show()
            } else {
                uploadProfileAndNickname(nickname, currentUser.uid)
            }
        }
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
                imageUri = uri
                profileImage.setImageURI(uri)
            }
        }
    }

    private fun uploadProfileAndNickname(nickname: String, uid: String) {
        val newCharacterId = "$uid"

        val storageRef = storage.reference
            .child("$uid/profileImg/profile.png")

        val uploadTask = storageRef.putFile(imageUri!!)
        uploadTask.addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val userData = hashMapOf(
                    "name" to nickname,
                    "characterId" to newCharacterId,
                    "profileImg" to downloadUri.toString(),
                    "email" to (auth.currentUser?.email ?: "")
                )

                firestore.collection("Users").document(uid)
                    .update(userData.toMap())
                    .addOnSuccessListener {
                        Toast.makeText(this, "프로필 생성 완료", Toast.LENGTH_SHORT).show()
                        goToMain()
                    }
                    .addOnFailureListener { e ->
                        Log.e("LoginJoin", "Firestore 업데이트 실패", e)
                        firestore.collection("Users").document(uid)
                            .set(userData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "프로필 생성 완료", Toast.LENGTH_SHORT).show()
                                goToMain()
                            }
                            .addOnFailureListener { e2 ->
                                Log.e("LoginJoin", "Firestore 저장 실패", e2)
                                Toast.makeText(this, "프로필 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("LoginJoin", "이미지 업로드 실패", e)
            Toast.makeText(this, "이미지 업로드 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadDefaultImageAndNickname(nickname: String, uid: String) {
        val newCharacterId = "$uid"

        val storageRef = storage.reference
            .child("$uid/profileImg/profile.png")

        val drawable = ContextCompat.getDrawable(this, R.drawable.basicprofile) as BitmapDrawable
        val bitmap = drawable.bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val data = baos.toByteArray()

        val uploadTask = storageRef.putBytes(data)
        uploadTask.addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val userData = hashMapOf(
                    "name" to nickname,
                    "characterId" to newCharacterId,
                    "profileImg" to downloadUri.toString(),
                    "email" to (auth.currentUser?.email ?: "")
                )

                firestore.collection("Users").document(uid)
                    .update(userData.toMap())
                    .addOnSuccessListener {
                        Toast.makeText(this, "프로필 생성 완료", Toast.LENGTH_SHORT).show()
                        goToMain()
                    }
                    .addOnFailureListener { e ->
                        Log.e("LoginJoin", "Firestore 업데이트 실패", e)
                        firestore.collection("Users").document(uid)
                            .set(userData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "프로필 생성 완료", Toast.LENGTH_SHORT).show()
                                goToMain()
                            }
                            .addOnFailureListener { e2 ->
                                Log.e("LoginJoin", "Firestore 저장 실패", e2)
                                Toast.makeText(this, "프로필 저장 실패했습니다.", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("LoginJoin", "기본 이미지 업로드 실패", e)
            Toast.makeText(this, "이미지 업로드 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}