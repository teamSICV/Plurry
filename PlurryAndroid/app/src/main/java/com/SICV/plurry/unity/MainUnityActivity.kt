//package com.SICV.plurry.unity

/*
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R

class MainUnityActivity : AppCompatActivity() {

    //Unity Properties
    var isUnityLoaded: Boolean = false
    private lateinit var mGoUnityButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unity_main)

        //Unity Settings
        setupUnityButtons()
    }

    //Unity Methods
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun startUnityWithClass(klass: Class<*>?) {
        if (klass == null) {
            showToast("Unity Activity not found")
            return
        }
        val intent = Intent(this, klass)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivityForResult(intent, 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            isUnityLoaded = false
            //enableShowUnityButtons()
            showToast("Unity finished.")
        }
    }

    private fun showToast(message: String) {
        val duration = Toast.LENGTH_SHORT
        Toast.makeText(applicationContext, message, duration).show()
    }

    override fun onBackPressed() {
        finishAffinity()
    }

    private fun findClassUsingReflection(className: String): Class<*>? {
        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            null
        }
    }

    private fun getMainUnityGameActivityClass(): Class<*>? {
        return findClassUsingReflection("com.SICV.plurry.unity.MainUnityGameActivity")
    }

    private fun setupUnityButtons() {
        mGoUnityButton = findViewById(R.id.b_go_unity)

        if (getMainUnityGameActivityClass() != null) {
            mGoUnityButton.visibility = View.VISIBLE
            //mActivityType = ActivityType.PLAYER_GAME_ACTIVITY
        }

        mGoUnityButton.setOnClickListener{
            Log.d("Logsicv", "MainActivity setOnClickListener")

            isUnityLoaded = true
            startUnityWithClass(getMainUnityGameActivityClass())
        }
    }
}
*/

package com.SICV.plurry.unity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R

class MainUnityActivity : AppCompatActivity(), UnityFragmentHostActivity {

    //Unity Properties
    var isUnityLoaded: Boolean = false

    // 유니티 프래그먼트
    private var unityFragment: MainUnityGameActivity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unity_main)

        Log.d("MainUnityActivity", "onCreate called")

        try {
            // 유니티 네이티브 라이브러리 로드
            UnityLoader.loadLibraries(this)

            // 액티비티 시작 즉시 프래그먼트 표시 (자동 시작)
            showUnityFragment()
        } catch (e: Exception) {
            Log.e("MainUnityActivity", "자동 시작 오류: ${e.message}", e)
            Toast.makeText(this, "초기화 오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    //Unity Methods
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("MainUnityActivity", "onNewIntent called")

        // 프래그먼트가 보이는 상태라면 인텐트 전달
        if (isUnityLoaded && unityFragment != null && unityFragment!!.isVisible) {
            try {
                unityFragment?.handleIntent(intent)
                Log.d("MainUnityActivity", "Intent handled by fragment")
            } catch (e: Exception) {
                Log.e("MainUnityActivity", "Error handling intent: ${e.message}", e)
            }
        }
    }

    private fun showUnityFragment() {
        Log.d("MainUnityActivity", "showUnityFragment called")

        try {
            if (unityFragment == null) {
                unityFragment = MainUnityGameActivity()
                Log.d("MainUnityActivity", "New unity fragment created")
            }

            // Fragment 트랜잭션으로 추가
            supportFragmentManager.beginTransaction()
                .replace(R.id.unity_fragment_container, unityFragment!!)
                .commitNow() // 즉시 커밋하여 동기적으로 처리

            isUnityLoaded = true
            Log.d("MainUnityActivity", "Unity fragment shown successfully")
        } catch (e: Exception) {
            Log.e("MainUnityActivity", "Error showing fragment: ${e.message}", e)
            Toast.makeText(this, "프래그먼트 표시 오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideUnityFragment() {
        Log.d("MainUnityActivity", "hideUnityFragment called")

        try {
            if (unityFragment != null && unityFragment!!.isVisible) {
                supportFragmentManager.beginTransaction()
                    .remove(unityFragment!!)
                    .commitNow() // 즉시 커밋

                Log.d("MainUnityActivity", "Fragment removed")
            }

            isUnityLoaded = false
        } catch (e: Exception) {
            Log.e("MainUnityActivity", "Error hiding fragment: ${e.message}", e)
        }
    }

    private fun showToast(message: String) {
        val duration = Toast.LENGTH_SHORT
        Toast.makeText(applicationContext, message, duration).show()
    }

    override fun onBackPressed() {
        Log.d("MainUnityActivity", "onBackPressed called")

        if (isUnityLoaded) {
            hideUnityFragment()
            // 앱을 바로 종료하지 않고 토스트 메시지 표시
            showToast("Unity 화면을 종료했습니다. 다시 뒤로 가기를 누르면 앱이 종료됩니다.")
        } else {
            finishAffinity()
        }
    }

    // UnityFragmentHostActivity 인터페이스 구현
    override fun createUnityView(): SurfaceView {
        // Unity용 SurfaceView 생성
        Log.d("MainUnityActivity", "createUnityView called")
        return GameActivitySurfaceView(this)
    }

    override fun onUnityPlayerUnloaded() {
        Log.d("MainUnityActivity", "onUnityPlayerUnloaded called")

        try {
            // 기존 코드 유지
            SharedClass.showMainActivity("")
            hideUnityFragment()
        } catch (e: Exception) {
            Log.e("MainUnityActivity", "Error in onUnityPlayerUnloaded: ${e.message}", e)
        }
    }

    // 추가 생명주기 메서드에 로그 추가
    override fun onResume() {
        super.onResume()
        Log.d("MainUnityActivity", "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainUnityActivity", "onPause called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainUnityActivity", "onDestroy called")
    }

    // GameActivitySurfaceView 구현
    inner class GameActivitySurfaceView(context: android.content.Context) : SurfaceView(context) {
        init {
            Log.d("MainUnityActivity", "GameActivitySurfaceView created")
        }
    }
}
