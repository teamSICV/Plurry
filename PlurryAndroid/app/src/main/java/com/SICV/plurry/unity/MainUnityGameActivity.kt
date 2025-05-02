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
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.SICV.plurry.R
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
        Log.d("UnityFragment", "onCreateView called")

        try {
            // XML 레이아웃 사용 (unity_fragment_layout.xml을 생성해야 함)
            return inflater.inflate(R.layout.unity_fragment_layout, container, false)
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error in onCreateView: ${e.message}", e)
            throw e
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("UnityFragment", "onViewCreated called")

        try {
            // FrameLayout 가져오기 (unity_fragment_layout.xml에 정의해야 함)
            mFrameLayout = view.findViewById(R.id.unity_frame_layout)
            Log.d("UnityFragment", "FrameLayout reference acquired: ${mFrameLayout != null}")

            // 활동에 접근
            val hostActivity = activity as? UnityFragmentHostActivity
                ?: throw IllegalStateException("호스트 액티비티가 UnityFragmentHostActivity를 구현해야 합니다")

            // SurfaceView 생성
            mSurfaceView = hostActivity.createUnityView()
            Log.d("UnityFragment", "SurfaceView created: ${mSurfaceView != null}")

            // UnityPlayer 초기화 시도
            try {
                val cmdLine = updateUnityCommandLineArguments(
                    (hostActivity as Activity).intent?.getStringExtra("unity") ?: ""
                )
                (hostActivity as Activity).intent?.putExtra("unity", cmdLine)

                if (UnityLoader.isLibraryLoaded()) {
                    // 유니티 라이브러리가 로드되었다면 정상적으로 초기화
                    mUnityPlayer = UnityPlayerForGameActivity(
                        hostActivity as Activity,
                        mFrameLayout,
                        mSurfaceView,
                        this
                    )
                    Log.d("UnityFragment", "UnityPlayerForGameActivity 초기화 성공")
                } else {
                    // 라이브러리가 로드되지 않았다면 래퍼 사용
                    Log.w("UnityFragment", "유니티 라이브러리 로드되지 않음, 래퍼 사용")
                    throw UnsatisfiedLinkError("유니티 라이브러리 로드되지 않음")
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.e("UnityFragment", "네이티브 라이브러리 오류: ${e.message}")
                // 대체 구현 사용
                val wrapper = UnityPlayerWrapper(hostActivity as Activity, mFrameLayout!!)
                Log.d("UnityFragment", "UnityPlayerWrapper로 대체")
            } catch (e: Exception) {
                Log.e("UnityFragment", "UnityPlayer 초기화 오류: ${e.message}", e)
                // 대체 구현 사용
                val wrapper = UnityPlayerWrapper(hostActivity as Activity, mFrameLayout!!)
                Log.d("UnityFragment", "UnityPlayerWrapper로 대체")
            }

            Log.d("UnityFragment", "UnityPlayer initialized: ${mUnityPlayer != null}")
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error in onViewCreated: ${e.message}", e)
            throw e
        }
    }

    // 유니티 커맨드 라인 인자 업데이트
    protected fun updateUnityCommandLineArguments(cmdLine: String): String {
        Log.d("UnityFragment", "updateUnityCommandLineArguments called with: $cmdLine")
        return cmdLine
    }

    // 생명주기 메서드
    override fun onPause() {
        super.onPause()
        Log.d("UnityFragment", "onPause called")
        try {
            mUnityPlayer?.onPause()
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error in onPause: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("UnityFragment", "onResume called")
        try {
            mUnityPlayer?.onResume()
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error in onResume: ${e.message}", e)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("UnityFragment", "onStart called")
        try {
            mUnityPlayer?.onStart()
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error in onStart: ${e.message}", e)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("UnityFragment", "onStop called")
        try {
            mUnityPlayer?.onStop()
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error in onStop: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        Log.d("UnityFragment", "onDestroy called")
        try {
            mUnityPlayer?.destroy()
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error in onDestroy: ${e.message}", e)
        }
        super.onDestroy()
    }

    override fun onUnityPlayerUnloaded() {
        Log.d("UnityFragment", "onUnityPlayerUnloaded called")
        try {
            // SharedClass 호출
            (activity as? UnityFragmentHostActivity)?.onUnityPlayerUnloaded()
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error in onUnityPlayerUnloaded: ${e.message}", e)
        }
    }

    override fun onUnityPlayerQuitted() {
        Log.d("UnityFragment", "onUnityPlayerQuitted called")
        // 필요한 경우 구현
    }

    // 설정 변경 처리
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("UnityFragment", "onConfigurationChanged called")
        try {
            mUnityPlayer?.configurationChanged(newConfig)
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error in onConfigurationChanged: ${e.message}", e)
        }
    }

    // Intent 처리
    fun handleIntent(intent: Intent?) {
        Log.d("UnityFragment", "handleIntent called")
        try {
            if (intent == null || intent.extras == null) {
                Log.d("UnityFragment", "Intent or extras is null")
                return
            }

            if (intent.extras!!.containsKey("doQuit")) {
                Log.d("UnityFragment", "doQuit detected in intent")
                mUnityPlayer?.let {
                    activity?.finish()
                }
            }

            // 새 인텐트 유니티에 전달
            mUnityPlayer?.newIntent(intent)
            Log.d("UnityFragment", "New intent forwarded to Unity player")
        } catch (e: Exception) {
            Log.e("UnityFragment", "Error handling intent: ${e.message}", e)
        }
    }
}