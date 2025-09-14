package com.SICV.plurry.login

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.SICV.plurry.MainActivity
import com.SICV.plurry.R
import com.SICV.plurry.UserRewardInitializer
import com.SICV.plurry.BuildConfig
import com.SICV.plurry.security.AndroidSecurityValidator
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
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var securityValidator: AndroidSecurityValidator
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var btnLogin: Button
    private lateinit var tvLockoutMessage: TextView

    private val RC_SIGN_IN = 9001

    // 보안: 로그인 시도 횟수 제한 및 잠금 시간
    private var loginAttempts = 0
    private val MAX_LOGIN_ATTEMPTS = 5
    private val LOCKOUT_DURATION_MS = 3 * 60 * 1000L
    private var lockoutTimer: CountDownTimer? = null

    // SharedPreferences 키
    private companion object {
        const val PREFS_NAME = "login_security"
        const val KEY_LOGIN_ATTEMPTS = "login_attempts"
        const val KEY_LOCKOUT_TIME = "lockout_time"
        const val KEY_LAST_ATTEMPT_TIME = "last_attempt_time"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_main)

        initializeComponents()
        checkCurrentUser()
        checkLockoutStatus()
        setupLoginButton()
    }

    private fun initializeComponents() {
        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)
        securityValidator = AndroidSecurityValidator()
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        btnLogin = findViewById(R.id.BtnLogin)
        tvLockoutMessage = findViewById(R.id.tvLockoutMessage)

        // 저장된 로그인 시도 횟수 복원
        loginAttempts = sharedPrefs.getInt(KEY_LOGIN_ATTEMPTS, 0)

        // GoogleSignInOptions for fallback
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun checkLockoutStatus() {
        val lockoutEndTime = sharedPrefs.getLong(KEY_LOCKOUT_TIME, 0L)
        val currentTime = System.currentTimeMillis()

        if (lockoutEndTime > currentTime) {
            val remainingTime = lockoutEndTime - currentTime
            startLockoutTimer(remainingTime)
        } else {
            if (lockoutEndTime > 0) {
                resetLoginAttempts()
            }
            enableLoginButton()
        }
    }

    private fun setupLoginButton() {
        btnLogin.setOnClickListener {
            if (isLockedOut()) {
                showError("잠시 후 다시 시도해주세요.")
                return@setOnClickListener
            }

            signOutAndTryAgain()
        }
    }

    private fun isLockedOut(): Boolean {
        val lockoutEndTime = sharedPrefs.getLong(KEY_LOCKOUT_TIME, 0L)
        return System.currentTimeMillis() < lockoutEndTime
    }

    private fun signOutAndTryAgain() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInWithCredentialManager()
        }
    }

    // 보안: 토큰 유효성 검사 추가
    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // 토큰 유효성 검사
            currentUser.getIdToken(true)
                .addOnSuccessListener { result ->
                    if (result.token != null) {
                        // UID 보안 검증
                        if (isValidUID(currentUser.uid)) {
                            checkUserExistsAndRedirect(currentUser.uid)
                        } else {
                            logError("Invalid UID format during auto-login", null)
                            performSignOut("사용자 정보가 유효하지 않습니다.")
                        }
                    } else {
                        performSignOut("토큰이 유효하지 않습니다.")
                    }
                }
                .addOnFailureListener { e ->
                    logError("Token validation failed", e)
                    performSignOut("인증 정보를 확인할 수 없습니다.")
                }
        }
    }

    private fun performSignOut(message: String? = null) {
        auth.signOut()
        googleSignInClient.signOut()
        message?.let { showError(it) }
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
                        // 보안: 토큰 검증
                        if (isValidIdToken(credential.idToken)) {
                            firebaseAuthWithGoogle(credential.idToken)
                        } else {
                            handleLoginFailure("유효하지 않은 인증 토큰입니다.")
                        }
                    }
                    is CustomCredential -> {
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            if (isValidIdToken(googleIdTokenCredential.idToken)) {
                                firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
                            } else {
                                handleLoginFailure("유효하지 않은 인증 토큰입니다.")
                            }
                        } else {
                            handleLoginFailure("지원하지 않는 인증 방식입니다.")
                        }
                    }
                    else -> {
                        handleLoginFailure("알 수 없는 인증 오류가 발생했습니다.")
                    }
                }
            } catch (e: GetCredentialException) {
                logError("CredentialManager failed", e)
                handleLoginFailure("로그인에 실패했습니다.\n다시 시도해주세요.")
            } catch (e: Exception) {
                logError("CredentialManager error", e)
                handleLoginFailure("예상치 못한 오류가 발생했습니다.")
            }
        }
    }

    private fun isValidIdToken(token: String?): Boolean {
        return !token.isNullOrBlank() &&
                token.length > 50 &&
                !token.contains(Regex("[<>\"'&]")) // 기본적인 XSS 방지
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken

                if (isValidIdToken(idToken)) {
                    firebaseAuthWithGoogle(idToken ?: "")
                } else {
                    handleLoginFailure("유효하지 않은 인증 정보입니다.")
                }
            } catch (e: ApiException) {
                logError("GoogleSignIn failed: ${e.statusCode}", e)
                handleLoginFailure("Google 로그인에 실패했습니다.")
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
                        // 성공 시 로그인 시도 횟수 초기화
                        resetLoginAttempts()

                        if (isValidUID(it.uid)) {
                            checkUserExistsAndRedirect(it.uid)
                        } else {
                            logError("Invalid UID after successful auth: ${it.uid}", null)
                            handleLoginFailure("사용자 정보가 유효하지 않습니다.")
                        }
                    } ?: handleLoginFailure("사용자 정보를 가져올 수 없습니다.")
                } else {
                    logError("Firebase auth failed", task.exception)
                    handleLoginFailure("인증에 실패했습니다.")
                }
            }
    }

    private fun checkUserExistsAndRedirect(uid: String) {
        // 추가 UID 검증
        if (!isValidUID(uid)) {
            logError("Invalid UID format: $uid", null)
            handleLoginFailure("유효하지 않은 사용자 정보입니다.")
            return
        }

        // Firebase 쿼리용 UID 정제
        val sanitizedUID = securityValidator.sanitizeForFirebaseQuery(uid)

        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("Users").document(sanitizedUID)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val documentUID = document.id
                    if (documentUID == sanitizedUID) {
                        logDebug("기존 사용자 로그인 성공")
                        goToMain()
                    } else {
                        logError("Document UID mismatch", null)
                        handleLoginFailure("사용자 정보가 일치하지 않습니다.")
                    }
                } else {
                    logDebug("신규 사용자 - 회원가입 화면으로 이동")
                    goToLoginJoin()
                }
            }
            .addOnFailureListener { e ->
                logError("사용자 UID 확인 실패", e)
                handleLoginFailure("사용자 정보 확인에 실패했습니다.")
            }
    }

    // 강화된 UID 검증
    private fun isValidUID(uid: String): Boolean {
        return uid.isNotBlank() &&
                uid.length >= 10 &&
                uid.length <= 128 && // Firebase UID 최대 길이
                uid.matches(Regex("^[a-zA-Z0-9]+$")) &&
                !uid.lowercase().contains("admin") &&
                !uid.lowercase().contains("system")
    }

    private fun handleLoginFailure(message: String) {
        incrementLoginAttempts()

        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            startLockout()
            showError("3분 후 다시 시도할 수 있습니다.")
        } else {
            val remainingAttempts = MAX_LOGIN_ATTEMPTS - loginAttempts
            showError("$message\n남은 시도 횟수: $remainingAttempts")
        }
    }

    private fun incrementLoginAttempts() {
        loginAttempts++
        val currentTime = System.currentTimeMillis()

        sharedPrefs.edit()
            .putInt(KEY_LOGIN_ATTEMPTS, loginAttempts)
            .putLong(KEY_LAST_ATTEMPT_TIME, currentTime)
            .apply()
    }

    private fun startLockout() {
        val lockoutEndTime = System.currentTimeMillis() + LOCKOUT_DURATION_MS

        sharedPrefs.edit()
            .putLong(KEY_LOCKOUT_TIME, lockoutEndTime)
            .apply()

        startLockoutTimer(LOCKOUT_DURATION_MS)
    }

    private fun startLockoutTimer(remainingTime: Long) {
        disableLoginButton()

        lockoutTimer = object : CountDownTimer(remainingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000

                tvLockoutMessage.text = "${minutes}분 ${seconds}초 후 재시도 가능"
                tvLockoutMessage.visibility = View.VISIBLE
            }

            override fun onFinish() {
                resetLoginAttempts()
                enableLoginButton()
                tvLockoutMessage.visibility = View.GONE
            }
        }.start()
    }

    private fun resetLoginAttempts() {
        loginAttempts = 0
        sharedPrefs.edit()
            .remove(KEY_LOGIN_ATTEMPTS)
            .remove(KEY_LOCKOUT_TIME)
            .remove(KEY_LAST_ATTEMPT_TIME)
            .apply()
    }

    private fun enableLoginButton() {
        btnLogin.isEnabled = true
        btnLogin.alpha = 1.0f
    }

    private fun disableLoginButton() {
        btnLogin.isEnabled = false
        btnLogin.alpha = 0.5f
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun logError(message: String, throwable: Throwable?) {
        if (BuildConfig.DEBUG) {
            Log.e("LoginMain", message, throwable)
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("LoginMain", message)
        }
    }

    private fun goToLoginJoin() {
        val intent = Intent(this, LoginJoinActivity::class.java)
        startActivity(intent)
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        lockoutTimer?.cancel()
    }
}