package com.SICV.plurry.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.SICV.plurry.MainActivity
import com.SICV.plurry.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest

class LoginOtherJoinActivity : ComponentActivity() {

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnJoinNext: Button
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_other_join)

        auth = FirebaseAuth.getInstance()
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        btnJoinNext = findViewById(R.id.btnJoinNext)

        btnJoinNext.setOnClickListener {
            val email = editEmail.text.toString().trim()
            val password = editPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isPasswordValid(password)) {
                Toast.makeText(this, "비밀번호는 대문자, 숫자, 특수문자를 포함해야 합니다.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { signInTask ->
                    if (signInTask.isSuccessful) {
                        val user = auth.currentUser
                        user?.let {
                            checkUserProfileAndRedirect(it.uid)
                        }
                    } else {
                        createNewUser(email, password)
                    }
                }
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }
        val isLongEnough = password.length >= 6

        return hasUpperCase && hasDigit && hasSpecialChar && isLongEnough
    }

    private fun createNewUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        val hashedPassword = hashPassword(password)
                        val newUser = hashMapOf(
                            "email" to email,
                            "password" to hashedPassword
                        )

                        val db = FirebaseFirestore.getInstance()
                        db.collection("Users").document(it.uid)
                            .set(newUser)
                            .addOnSuccessListener {
                                val intent = Intent(this, LoginJoinActivity::class.java)
                                intent.putExtra("email", email)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "사용자 정보 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Log.e("LoginOtherJoin", "계정 생성 실패", task.exception)
                    Toast.makeText(this, "계정 생성 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkUserProfileAndRedirect(uid: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists() && document.contains("name") && document.contains("profileImg")) {
                    goToMain()
                } else {
                    val intent = Intent(this, LoginJoinActivity::class.java)
                    intent.putExtra("email", auth.currentUser?.email)
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("LoginOtherJoin", "사용자 프로필 확인 실패", e)
                Toast.makeText(this, "사용자 정보 확인에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}