package com.SICV.plurry

import LogLS
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.SICV.plurry.crewstep.CrewLineChooseActivity
import com.SICV.plurry.crewstep.CrewLineMainActivity
import com.SICV.plurry.goingwalk.GoingWalkMainFragment
import com.SICV.plurry.login.LoginMainActivity
import com.SICV.plurry.mypage.MyPageMainActivity
import com.SICV.plurry.pointrecord.PointRecordMainActivity
import com.SICV.plurry.raising.RaisingMainFragment
import com.SICV.plurry.ranking.MainCrewRankingManager
import com.SICV.plurry.ranking.MainRankingManager
import com.SICV.plurry.ranking.RankingMainActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.unity3d.player.UnityPlayer
import com.unity3d.player.UnityPlayerGameActivity
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory

class MainActivity : UnityPlayerGameActivity(), MainHomeFragment.OnFragmentInteractionListener {

/* ******************
*
* Property Define
*
* ******************/

    //인증
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    //Fragment
    lateinit var destinarionFragment: Fragment
    private lateinit var androidUIContainer: ViewGroup
    private var isStartFragment: Boolean = true;

/* ******************
*
* onCreate
*
* ******************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //LogLS.d("Begin")

        // [START] 루팅 및 무결성 탐지 코드 추가
//        if (isCompromised()) {
//            Toast.makeText(this, "보안 위반이 감지되었습니다. 앱을 종료합니다.", Toast.LENGTH_LONG).show()
//            finish()
//            return
//        }
        // [END] 루팅 및 무결성 탐지 코드 추가

        //배경 투명하게 하는 코드들
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.setBackgroundDrawableResource(android.R.color.transparent)
        findViewById<FrameLayout>(R.id.fragment_container)?.setBackgroundColor(Color.TRANSPARENT)
        findViewById<FrameLayout>(R.id.fragment_container)?.background = null

        // Unity 오버레이 UI 설정
        setupUnityOverlay()

        // Login
        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        handleExploreIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleExploreIntent(it) }
    }

    private fun setupUnityOverlay() {
        try {
            // Unity의 contentViewId 로그 출력
            //LogLS.d("contentViewId: $contentViewId")

            // Unity의 루트 레이아웃 가져오기
            val rootLayout = findViewById<FrameLayout>(contentViewId)
            //LogLS.d("rootLayout: $rootLayout")

            if (rootLayout == null) {
                LogLS.e("rootLayout is null - Unity may not be initialized")
                return
            }

            // 안드로이드 UI 컨테이너 생성
            val inflater = LayoutInflater.from(this)
            val inflatedView = inflater.inflate(R.layout.activity_main, rootLayout, false)
            //LogLS.d("inflatedView: $inflatedView")
            //LogLS.d("inflatedView type: ${inflatedView::class.java.simpleName}")

            androidUIContainer = inflatedView as ViewGroup
            //LogLS.d("androidUIContainer: $androidUIContainer")

            // 오버레이로 추가
            val layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            rootLayout.addView(androidUIContainer, layoutParams)
            //LogLS.d("UI overlay added successfully")

            // Fragment 컨테이너 배경 투명하게 설정
            val fragmentContainer = androidUIContainer.findViewById<FrameLayout>(R.id.fragment_container)
            //LogLS.d("fragmentContainer: $fragmentContainer")

            fragmentContainer?.setBackgroundColor(Color.TRANSPARENT)
            fragmentContainer?.background = null

        } catch (e: Exception) {
            LogLS.e("Error adding Android UI overlay: ${e.message}")
            e.printStackTrace()
        }
    }

/* ******************
*
* main Login
*
* ******************/

    private fun signOut(){

        //LogLS.d("Begin")

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

        //LogLS.d("Begin")

        val intent = Intent(this, LoginMainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

/* ******************
*
* Fragment Setting
*
* ******************/

    public fun loadFragment(inFragmentName:String) {
        //LogLS.d("Begin-inFragmentName:${inFragmentName}")

        runOnUiThread {
            findViewById<ImageView>(R.id.img_loading)?.visibility = View.VISIBLE
        }

        // 현재 실행중인 프래그먼트 종료
        val fragmentContainer = androidUIContainer.findViewById<FrameLayout>(R.id.fragment_container)
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment != null) {
            supportFragmentManager.beginTransaction()
                .remove(currentFragment)
                .commitNow() // 즉시 실행
        }

        when (inFragmentName) {
            "HOME"->{
                destinarionFragment = MainHomeFragment()
                LoadUnityScene( "MainHome" )
            }
            "GOING_WALK"->{
                destinarionFragment = GoingWalkMainFragment()
                LoadUnityScene( "GoingWalk" )
            }
            "RAISING"->{
                destinarionFragment = RaisingMainFragment()
                LoadUnityScene( "RaisingMain" )
            }
        }

        if(destinarionFragment != null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, destinarionFragment)
                .commit()
        }
        else {
            LogLS.e("No destinarionFragment!!")
        }
    }

    public fun EndUnitySplash()
    {
        //LogLS.d("Begin")

        if(!isStartFragment) {
            //LogLS.e("It is not StartFragment!!")
            return
        }

        // 현재 실행중인 프래그먼트 종료
        val fragmentContainer = androidUIContainer.findViewById<FrameLayout>(R.id.fragment_container)
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment != null) {
            supportFragmentManager.beginTransaction()
                .remove(currentFragment)
                .commitNow() // 즉시 실행
        }

        // Fragment 실행
        destinarionFragment = MainHomeFragment()
        if(destinarionFragment != null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, destinarionFragment)
                .commit()
        }
        else {
            LogLS.e("No destinarionFragment!!")
        }

        isStartFragment = false
    }

    public fun EndUnityLoadingScene()
    {
        //LogLS.d("Begin")

        if(isStartFragment) {
            LogLS.e("It is StartFragment!!");
            return
        }

        runOnUiThread {
            findViewById<ImageView>(R.id.img_loading)?.visibility = View.GONE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //LogLS.d("Begin - requestCode: $requestCode, resultCode: $resultCode")
        super.onActivityResult(requestCode, resultCode, data)

        // 🔥 Google Fit 권한 결과를 Fragment로 직접 전달
        if (requestCode == 1003) { // GOOGLE_FIT_PERMISSIONS_REQUEST_CODE
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is GoingWalkMainFragment) {
                //LogLS.d("Fragment로 결과 전달")
                currentFragment.handleGoogleFitResult(resultCode)
            }
        }
    }

/* ******************
*
* Main Home Bridge
*
* ******************/

    // MainHomeFragment.OnFragmentInteractionListener 구현
    override fun onNavigationRequested(destination: String, extras: Bundle?) {
        //LogLS.d("Navigation requested to: ${destination}")

        when (destination) {
            "GOING_WALK" -> {
                //val intent = Intent(this, GoingWalkMainActivity::class.java)
                //startActivity(intent)
                loadFragment(destination)
            }
            "POINT_RECORD" -> {
                val intent = Intent(this, PointRecordMainActivity::class.java)
                startActivity(intent)
            }
            "CREW_LINE_CHOOSE" -> {
                val intent = Intent(this, CrewLineChooseActivity::class.java)
                startActivity(intent)
            }
            "CREW_LINE_MAIN" -> {
                val intent = Intent(this, CrewLineMainActivity::class.java)
                extras?.let { intent.putExtras(it) }
                startActivity(intent)
            }
            "RAISING" -> {
                loadFragment(destination)
            }
            "MY_PAGE" -> {
                val intent = Intent(this, MyPageMainActivity::class.java)
                startActivity(intent)
            }
        }
    }

/* ******************
*
* Point Record Explore Flow
*
* ******************/

    private fun handleExploreIntent(intent: Intent) {
        if (intent.getBooleanExtra("startExplore", false)) {
            val placeId = intent.getStringExtra("placeId") ?: ""
            val lat = intent.getDoubleExtra("lat", 0.0)
            val lng = intent.getDoubleExtra("lng", 0.0)
            val imageUrl = intent.getStringExtra("imageUrl") ?: ""
            val placeName = intent.getStringExtra("placeName") ?: "알 수 없는 장소"

            // GoingWalkMainFragment로 전달할 Bundle 생성
            val bundle = Bundle().apply {
                putBoolean("startExplore", true)
                putString("placeId", placeId)
                putDouble("lat", lat)
                putDouble("lng", lng)
                putString("imageUrl", imageUrl)
                putString("placeName", placeName)
            }

            // GoingWalkMainFragment 로드
            destinarionFragment = GoingWalkMainFragment().apply {
                arguments = bundle
            }
            LoadUnityScene("GoingWalk")

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, destinarionFragment)
                .commit()
        }
    }

/* ******************
*
* Unity Call Bridge
*
* ******************/
/********CallToUnity********/
    public fun SendMessageToUnity( inFunctionName : String )
    {
        LogLS.d("Begin-inFunctionName:${inFunctionName}")
        try {
            UnityPlayer.UnitySendMessage("GameController", inFunctionName, "")
        } catch (e: Exception) {
            Log.e("LogLS", "Failed to UnitySendMessage : ${e.message}")
        }
    }

    public fun LoadUnityScene( inSceneName : String )
    {
        //LogLS.d("inSceneName:${inSceneName}")
        try {
            UnityPlayer.UnitySendMessage("GameController", "LoadUnityScene", inSceneName)
        } catch (e: Exception) {
            Log.e("LogLS", "Failed to UnitySendMessage : ${e.message}")
        }
    }

    public fun SetSafety(safety : String)
    {
        try {
            UnityPlayer.UnitySendMessage("GameController", "SetSafety", safety)
        } catch (e: Exception) {
            Log.e("LogLS", "Failed to UnitySendMessage : ${e.message}")
        }
    }

/********CalledFromUnity********/
//Debug Log
    public fun UnityDebugLog(debugLog : String)
    {
        Log.d("LogLS-Unity", debugLog)
    }

    public fun UnityDebugWarning(debugLog : String)
    {
        Log.w("LogLS-Unity", debugLog)
    }

    public fun UnityDebugError(debugLog : String)
    {
        Log.e("LogLS-Unity", debugLog)
    }

    public fun CalledFunctionFromUnity(inFunctionName:String)
    {
        //LogLS.d(inFunctionName)

        when(inFunctionName) {
            "EndUnitySplash"-> {
                EndUnitySplash()
                return
            }
            "EndUnityLoadingScene"-> {
                EndUnityLoadingScene()
                return
            }
            else -> {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFragment is RaisingMainFragment) {
                    currentFragment.CalledFunctionFromUnity(inFunctionName)
                }
            }
        }
    }






/* ******************
*
* Raising Main Bridge
*
* ******************/

/* ******************
*
* GoingWalk Main Bridge
*
* ******************/


/* ******************
*
* 루팅 및 무결성 탐지 로직
*
* ******************/

    /*
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
            val correctSignature = "앱 출시후에 릴리즈 키 입력"

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
    */
    // [END] 루팅 및 무결성 탐지 로직
}
