package com.SICV.plurry

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.crewstep.CrewLineChooseActivity
import com.SICV.plurry.crewstep.CrewLineMainActivity
import com.SICV.plurry.goingwalk.GoingWalkMainActivity
import com.SICV.plurry.login.LoginMainActivity
import com.SICV.plurry.mypage.MyPageMainActivity
import com.SICV.plurry.pointrecord.PointRecordMainActivity
import com.SICV.plurry.raising.RaisingMainActivity
import com.SICV.plurry.ranking.MainCrewRankingManager
import com.SICV.plurry.ranking.MainRankingManager
import com.SICV.plurry.ranking.RankingMainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var rankingManager: MainRankingManager
    private lateinit var crewRankingManager: MainCrewRankingManager
    private lateinit var myWalkRecord: MainMyWalkRecord

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [START] 루팅 및 무결성 탐지 코드 추가
        if (isCompromised()) {
            Toast.makeText(this, "보안 위반이 감지되었습니다. 앱을 종료합니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // [END] 루팅 및 무결성 탐지 코드 추가

        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        settingButton()

        //메인-랭킹
        setupRankingManager()

        //메인-크루데이터
        val caloTextView = findViewById<TextView>(R.id.mainCrewCalo)
        val countTextView = findViewById<TextView>(R.id.mainCrewCount)
        val distanceTextView = findViewById<TextView>(R.id.mainCrewDistance)

        crewRankingManager = MainCrewRankingManager(caloTextView, countTextView, distanceTextView)
        crewRankingManager.startUpdating()

        //메인-내 항해기록
        setupMyWalkRecord()
    }

    private fun settingButton() {
        val buttonGoingWalk = findViewById<Button>(R.id.b_goingWalk)
        val buttonPointRecord = findViewById<Button>(R.id.b_pointRecord)
        val buttonCrewLine = findViewById<Button>(R.id.b_crewLine)
        val buttonRaising = findViewById<Button>(R.id.b_raising)
        val buttonMyPage = findViewById<Button>(R.id.btnMyPage)

        buttonGoingWalk.setOnClickListener{
            val intent = Intent(this, GoingWalkMainActivity::class.java)
            startActivity(intent)
        }

        buttonPointRecord.setOnClickListener{
            val intent = Intent(this, PointRecordMainActivity::class.java)
            startActivity(intent)
        }

        buttonCrewLine.setOnClickListener {
            checkUserCrewStatus()
        }

        buttonRaising.setOnClickListener{
            val intent = Intent(this, RaisingMainActivity::class.java)
            startActivity(intent)
        }

        buttonMyPage.setOnClickListener{
            val intent = Intent(this, MyPageMainActivity::class.java)
            startActivity(intent)
        }
    }

    //메인-로그인, 로그아웃
    private fun checkUserCrewStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            val intent = Intent(this, CrewLineChooseActivity::class.java)
            startActivity(intent)
            return
        }

        val uid = currentUser.uid
        val db = FirebaseFirestore.getInstance()

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val crewAt = userDoc.getString("crewAt")

                    if (!crewAt.isNullOrEmpty()) {
                        val intent = Intent(this, CrewLineMainActivity::class.java)
                        intent.putExtra("crewId", crewAt)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, CrewLineChooseActivity::class.java)
                        startActivity(intent)
                    }
                } else {
                    val intent = Intent(this, CrewLineChooseActivity::class.java)
                    startActivity(intent)
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewCheck", "사용자 정보 확인 실패", e)
                val intent = Intent(this, CrewLineChooseActivity::class.java)
                startActivity(intent)
            }
    }

    private fun signOut(){
        auth.signOut()

        googleSignInClient.signOut().addOnCompleteListener(this) {task ->
            if(task.isSuccessful){
                Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
                goToLogin()
            }else{
                Log.e("Logout", "Google 로그아웃 실패", task.exception)
                goToLogin()
            }
        }
    }

    private fun goToLogin(){
        val intent = Intent(this, LoginMainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    //메인-랭킹
    private fun setupRankingManager() {
        val rankingLinearLayout = findViewById<LinearLayout>(R.id.mainRankLinearLayout)
        val titleTextView = rankingLinearLayout.getChildAt(0) as TextView
        val valueTextView = rankingLinearLayout.getChildAt(1) as TextView
        val unitTextView = rankingLinearLayout.getChildAt(2) as TextView

        val leftArrow = findViewById<ImageView>(R.id.btnPre)
        val rightArrow = findViewById<ImageView>(R.id.btnNext)

        rankingManager = MainRankingManager(
            titleTextView, valueTextView, unitTextView, leftArrow, rightArrow
        )

        rankingManager.initialize()
    }

    //메인-내 항해기록
    private fun setupMyWalkRecord() {
        val caloTextView = findViewById<TextView>(R.id.mainWalkCalo)
        val distanceTextView = findViewById<TextView>(R.id.mainWalkDistance)
        val countTextView = findViewById<TextView>(R.id.mainWalkCount)

        myWalkRecord = MainMyWalkRecord(caloTextView, distanceTextView, countTextView)
        myWalkRecord.startUpdating()
    }

    override fun onResume() {
        super.onResume()
        crewRankingManager.startUpdating()
        myWalkRecord.startUpdating()
    }

    override fun onPause() {
        super.onPause()
        crewRankingManager.stopUpdating()
        myWalkRecord.stopUpdating()
    }

    override fun onDestroy() {
        super.onDestroy()
        rankingManager.cleanup()
        crewRankingManager.cleanup()
        myWalkRecord.cleanup()
    }

    // [START] 루팅 및 무결성 탐지 로직
    private fun isCompromised(): Boolean {
        // 루팅 탐지
        if (checkRootMethod1() || checkRootMethod2() || checkRootMethod3()) {
            return true
        }

        // 디버거 연결 탐지
        if (isDebuggerAttached()) {
            return true
        }

        // 앱 무결성 확인
        if (!isAppIntegrityValid()) {
            return true
        }

        // 커스텀 루팅 파일 탐지 (선택 사항)
        if (isCustomRootFilePresent()) {
            return true
        }

        return false
    }

    // 루팅 관련 파일/폴더 존재 여부
    private fun checkRootMethod1(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) {
                Log.w("RootCheck", "Root file detected: $path")
                return true
            }
        }
        return false
    }

    // 루팅 관련 패키지 설치 여부
    private fun checkRootMethod2(): Boolean {
        val packages = arrayOf(
            "com.noshufou.android.su",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.topjohnwu.magisk"
        )
        for (pkg in packages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                Log.w("RootCheck", "Root package detected: $pkg")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // 패키지가 설치되지 않음
            }
        }
        return false
    }

    // 빌드 태그 확인
    private fun checkRootMethod3(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            Log.w("RootCheck", "Build tags indicate test keys.")
            return true
        }
        return false
    }

    // 디버거 연결 탐지
    private fun isDebuggerAttached(): Boolean {
        return try {
            val debuggable = applicationInfo.flags and 2
            if (debuggable != 0) {
                // 앱이 디버그 모드로 빌드된 경우, 디버거 연결 가능성을 확인
                val debuggerConnected = android.os.Debug.isDebuggerConnected()
                if (debuggerConnected) {
                    Log.w("DebuggerCheck", "Debugger is attached.")
                }
                debuggerConnected
            } else {
                // 릴리즈 빌드의 경우 디버거 연결은 비정상적인 상황
                val debuggerConnected = android.os.Debug.isDebuggerConnected()
                if (debuggerConnected) {
                    Log.w("DebuggerCheck", "Debugger attached to release build.")
                }
                debuggerConnected
            }
        } catch (e: Exception) {
            Log.e("DebuggerCheck", "Debugger check failed.", e)
            return false
        }
    }

    // 앱의 서명 무결성 확인
    private fun isAppIntegrityValid(): Boolean {
        try {
            @Suppress("Deprecation")
            val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.apkContentsSigners?.first()?.toByteArray()
            } else {
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures?.first()?.toByteArray()
            }

            // signature가 null일 경우 무결성 검사 실패로 간주
            if (signature == null) {
                Log.e("IntegrityCheck", "App signature is null!")
                return false
            }

            // SHA-256 알고리즘 사용
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signature)
            val base64Signature = Base64.encodeToString(digest, Base64.NO_WRAP)

            // 여기에 실제 앱의 디버그 키 또는 릴리즈 키의 해시 값을 Base64 형식으로 입력해야 함.
            val correctSignature = "D+vpIYYNQ9PzjHAfC/9SNllsLQmUr7Hajs7d8CyptGk="

            if (base64Signature != correctSignature) {
                Log.e("IntegrityCheck", "App signature mismatch!")
                return false
            }
            return true
        } catch (e: Exception) {
            Log.e("IntegrityCheck", "App integrity check failed.", e)
            return false
        }
    }

    // 특정 루팅 탐지용 파일 존재 여부 (선택 사항)
    private fun isCustomRootFilePresent(): Boolean {
        // 실제 앱 개발 시, 이 파일은 외부에서 쉽게 찾기 어려운 경로와 이름을 사용해야 합니다.
        val customRootFile = File("/data/local/tmp/.my_root_flag")
        if (customRootFile.exists()) {
            Log.w("RootCheck", "Custom root flag file detected.")
            return true
        }
        return false
    }
    // [END] 루팅 및 무결성 탐지 로직
}
