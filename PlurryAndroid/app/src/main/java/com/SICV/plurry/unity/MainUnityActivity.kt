package com.SICV.plurry.unity

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