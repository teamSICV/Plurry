package com.SICV.plurry.unity

import android.content.Intent
import android.os.Bundle
import com.SICV.plurry.unity.SharedClass
import com.unity3d.player.UnityPlayerActivity

class MainUnityActivity : UnityPlayerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup activity layout
        SharedClass.addControlsToUnityFrame(this)
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