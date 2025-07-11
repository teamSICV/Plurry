package com.SICV.plurry.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.SICV.plurry.MainActivity
import com.SICV.plurry.R
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class LoginMainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var btnOtherLogin: Button
    private lateinit var googleSignInClient: GoogleSignInClient

    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_main)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        checkCurrentUser()

        val skipLogin = findViewById<TextView>(R.id.SkipLogin)

        skipLogin.setOnClickListener{
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // GoogleSignInOptions for fallback
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val btnLogin = findViewById<Button>(R.id.BtnLogin)
        btnOtherLogin = findViewById(R.id.BtnOtherLogin)

        btnOtherLogin.visibility = View.GONE

        btnLogin.setOnClickListener {
            googleSignInWithCredentialManager()
        }

        btnOtherLogin.setOnClickListener {
            btnOtherLogin.setOnClickListener {
                val intent = Intent(this, LoginOtherJoinActivity::class.java)
                startActivity(intent)}
        }
    }

    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkUserExistsAndRedirect(currentUser.uid)
        }
    }

    private fun googleSignInWithCredentialManager() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginMainActivity, request)
                val credential = result.credential

                when (credential) {
                    is GoogleIdTokenCredential -> {
                        firebaseAuthWithGoogle(credential.idToken)
                    }
                    is CustomCredential -> {
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
                        } else {
                            showLoginError()
                        }
                    }
                    else -> {
                        showLoginError()
                    }
                }
            } catch (e: GetCredentialException) {
                Log.e("Login", "CredentialManager 실패: ${e.message}")
                showLoginError()
                btnOtherLogin.visibility = View.VISIBLE
                googleSignInWithIntent()
            } catch (e: Exception) {
                Log.e("Login", "CredentialManager 에러: ${e.message}")
                showLoginError()
                btnOtherLogin.visibility = View.VISIBLE
            }
        }
    }

    private fun googleSignInWithIntent() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken ?: "")
            } catch (e: ApiException) {
                Log.e("Login", "GoogleSignIn 실패: ${e.statusCode}")
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
                    user?.let {
                        checkUserExistsAndRedirect(it.uid)
                    } ?: showLoginError()
                } else {
                    task.exception?.printStackTrace()
                    showLoginError()
                }
            }
    }

    private fun checkUserExistsAndRedirect(uid: String) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("Users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    goToMain()
                } else {
                    goToLoginJoin()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Login", "사용자 UID 확인 실패", e)
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