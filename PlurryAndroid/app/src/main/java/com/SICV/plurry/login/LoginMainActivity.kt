package com.SICV.plurry.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.SICV.plurry.MainActivity
import com.SICV.plurry.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialException

class LoginMainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_main)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        val btnLogin = findViewById<Button>(R.id.BtnLogin)
        btnLogin.setOnClickListener {
            googleSignIn()
        }
    }

    private fun googleSignIn() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginMainActivity, request)
                val credential = result.credential

                Log.d("Login", "Credential type: ${credential::class.java.simpleName}")
                Log.d("Login", "Credential data: ${credential.data}")

                when (credential) {
                    is GoogleIdTokenCredential -> {
                        firebaseAuthWithGoogle(credential.idToken)
                    }
                    is CustomCredential -> {
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            try {
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
                            } catch (e: Exception) {
                                showLoginError()
                            }
                        } else {
                            showLoginError()
                        }
                    }
                    else -> {
                        showLoginError()
                    }
                }

            } catch (e: GetCredentialException) {
                showLoginError()
            } catch (e: Exception) {
                Log.e("Login", "구글 로그인 실패: ${e.message}")
                e.printStackTrace()
                showLoginError()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    checkUserExistsAndRedirect(user?.email)
                } else {
                    task.exception?.printStackTrace()
                    showLoginError()
                }
            }
    }

    private fun checkUserExistsAndRedirect(email: String?) {
        if (email.isNullOrEmpty()) {
            showLoginError()
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("Users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    goToMain()
                } else {
                    goToLoginJoin()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Login", "사용자 이메일 확인 실패", e)
                showLoginError()
            }
    }

    private fun showLoginError() {
        Toast.makeText(this, "로그인 실패", Toast.LENGTH_SHORT).show()
    }

    private fun goToLoginJoin() {
        val intent = Intent(this, LoginJoinActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
