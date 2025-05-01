//package com.SICV.plurry.unity

/*
import android.content.Intent
import android.os.Bundle
import com.unity3d.player.UnityPlayerGameActivity

class MainUnityGameActivity : UnityPlayerGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup activity layout
        //SharedClass.addControlsToUnityFrame(this)
        val intent = intent
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null || intent.extras == null) return

        if (intent.extras!!.containsKey("doQuit")) {
            if (mUnityPlayer != null) {
                finish()
            }
        }
    }

    override fun onUnityPlayerUnloaded() {
        SharedClass.showMainActivity("")
    }
}
*/
package com.SICV.plurry.unity

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.SICV.plurry.R
import com.google.androidgamesdk.GameActivity
import com.unity3d.player.IUnityPlayerLifecycleEvents
import com.unity3d.player.UnityPlayerForGameActivity

interface UnityFragmentHostActivity {
    fun createUnityView(): SurfaceView  // 일반 SurfaceView 반환
    fun onUnityPlayerUnloaded()
}

class MainUnityGameActivity : Fragment(), IUnityPlayerLifecycleEvents {

    // 유니티 플레이어 인스턴스
    protected var mUnityPlayer: UnityPlayerForGameActivity? = null
    private var mFrameLayout: FrameLayout? = null
    private var mSurfaceView: SurfaceView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // XML 레이아웃 사용 (unity_fragment_layout.xml을 생성해야 함)
        return inflater.inflate(R.layout.unity_fragment_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // FrameLayout 가져오기 (unity_fragment_layout.xml에 정의해야 함)
        mFrameLayout = view.findViewById(R.id.unity_frame_layout)

        // 활동에 접근
        val hostActivity = activity as? UnityFragmentHostActivity
            ?: throw IllegalStateException("호스트 액티비티가 UnityFragmentHostActivity를 구현해야 합니다")

        // SurfaceView 생성
        mSurfaceView = hostActivity.createUnityView()

        // UnityPlayer 초기화
        val cmdLine = updateUnityCommandLineArguments(
            (hostActivity as Activity).intent?.getStringExtra("unity") ?: ""
        )
        (hostActivity as Activity).intent?.putExtra("unity", cmdLine)

        mUnityPlayer = UnityPlayerForGameActivity(
            hostActivity as Activity,
            mFrameLayout,
            mSurfaceView,
            this
        )
    }

    // 유니티 커맨드 라인 인자 업데이트
    protected fun updateUnityCommandLineArguments(cmdLine: String): String {
        return cmdLine
    }

    // 생명주기 메서드
    override fun onPause() {
        super.onPause()
        mUnityPlayer?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mUnityPlayer?.onResume()
    }

    override fun onStart() {
        super.onStart()
        mUnityPlayer?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mUnityPlayer?.onStop()
    }

    override fun onDestroy() {
        mUnityPlayer?.destroy()
        super.onDestroy()
    }

    override fun onUnityPlayerUnloaded() {
        // SharedClass 호출
        (activity as? UnityFragmentHostActivity)?.onUnityPlayerUnloaded()
    }

    override fun onUnityPlayerQuitted() {
        // 필요한 경우 구현
    }

    // 설정 변경 처리
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mUnityPlayer?.configurationChanged(newConfig)
    }

    // Intent 처리
    fun handleIntent(intent: Intent?) {
        if (intent == null || intent.extras == null) return

        if (intent.extras!!.containsKey("doQuit")) {
            mUnityPlayer?.let {
                activity?.finish()
            }
        }

        // 새 인텐트 유니티에 전달
        mUnityPlayer?.newIntent(intent)
    }
}
