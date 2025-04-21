package com.SICV.plurry

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.crewstep.CrewLineMainActivity
import com.SICV.plurry.goingwalk.GoingWalkMainActivity
import com.SICV.plurry.pointrecord.PointRecordMainActivity
import com.SICV.plurry.raising.RaisingMainActivity
import com.SICV.plurry.ranking.RankingMainActivity

class MainActivity : AppCompatActivity() {

    //Unity Properties
    private enum class ActivityType {
        PLAYER_ACTIVITY, PLAYER_GAME_ACTIVITY, BOTH
    }

    var isUnityLoaded: Boolean = false
    private var mActivityType = ActivityType.BOTH
    private var isGameActivity = false

    private lateinit var mShowUnityButton: Button
    private lateinit var mShowUnityGameButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settingButton()

        //Unity Settings
        setupUnityButtons()
        handleIntent(intent)
    }

    private fun settingButton() {
        val buttonGoingWalk = findViewById<Button>(R.id.b_goingWalk)
        val buttonPointRecord = findViewById<Button>(R.id.b_pointRecord)
        val buttonCrewLine = findViewById<Button>(R.id.b_crewLine)
        val buttonRaising = findViewById<Button>(R.id.b_raising)
        val buttonRanking = findViewById<Button>(R.id.b_ranking)

        buttonGoingWalk.setOnClickListener{
            val intent = Intent(this, GoingWalkMainActivity::class.java)
            startActivity(intent)
        }

        buttonPointRecord.setOnClickListener{
            val intent = Intent(this, PointRecordMainActivity::class.java)
            startActivity(intent)
        }

        buttonCrewLine.setOnClickListener{
            val intent = Intent(this, CrewLineMainActivity::class.java)
            startActivity(intent)
        }

        buttonRaising.setOnClickListener{
            val intent = Intent(this, RaisingMainActivity::class.java)
            startActivity(intent)
        }

        buttonRanking.setOnClickListener{
            val intent = Intent(this, RankingMainActivity::class.java)
            startActivity(intent)
        }
    }

    //Unity Methods
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.extras == null) return

        if (intent.extras!!.containsKey("setColor")) {
            val v = findViewById<Button>(R.id.b_finish)
            when (intent.extras!!.getString("setColor")) {
                "yellow" -> v.setBackgroundColor(Color.YELLOW)
                "red" -> v.setBackgroundColor(Color.RED)
                "blue" -> v.setBackgroundColor(Color.BLUE)
                else -> v.setBackgroundColor(0xFFd6d7d7.toInt())
            }
        }
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
            enableShowUnityButtons()
            showToast("Unity finished.")
        }
    }

    fun unloadUnity(doShowToast: Boolean) {
        if (isUnityLoaded) {
            val intent = if (isGameActivity)
                Intent(this, getMainUnityGameActivityClass())
            else
                Intent(this, getMainUnityActivityClass())
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            intent.putExtra("doQuit", true)
            startActivity(intent)
            isUnityLoaded = false
        } else if (doShowToast) {
            showToast("Show Unity First")
        }
    }

    fun onClickFinish(v: View) {
        unloadUnity(true)
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

    private fun getMainUnityActivityClass(): Class<*>? {
        return findClassUsingReflection("com.SICV.plurry.unity.MainUnityActivity")
    }

    private fun getMainUnityGameActivityClass(): Class<*>? {
        return findClassUsingReflection("com.SICV.plurry.unity.MainUnityGameActivity")
    }

    private fun setupUnityButtons() {
        // 레이아웃에 유니티 버튼을 추가해야 함
        mShowUnityButton = findViewById(R.id.b_show_unity)
        mShowUnityGameButton = findViewById(R.id.b_show_unity_game)

        if (getMainUnityActivityClass() != null) {
            mShowUnityButton.visibility = View.VISIBLE
            mActivityType = ActivityType.PLAYER_ACTIVITY
        }

        if (getMainUnityGameActivityClass() != null) {
            mShowUnityGameButton.visibility = View.VISIBLE
            mActivityType = ActivityType.PLAYER_GAME_ACTIVITY
        }

        if (mShowUnityButton.visibility == View.VISIBLE && mShowUnityGameButton.visibility == View.VISIBLE) {
            mActivityType = ActivityType.BOTH
        }

        mShowUnityButton.setOnClickListener{
            Log.d("Logsicv", "MainActivity setOnClickListener")

            isUnityLoaded = true
            isGameActivity = false
            disableShowUnityButtons()
            startUnityWithClass(getMainUnityActivityClass())
            /*            val intent = Intent(this, RaisingMainActivity::class.java)
                        startActivity(intent)*/
        }

        mShowUnityGameButton.setOnClickListener{
            isUnityLoaded = true
            isGameActivity = true
            disableShowUnityButtons()
            startUnityWithClass(getMainUnityGameActivityClass())
            /*            val intent = Intent(this, RankingMainActivity::class.java)
                        startActivity(intent)*/
        }
    }

    private fun disableShowUnityButtons() {
        if (mActivityType != ActivityType.BOTH) return

        mShowUnityButton.isEnabled = !isGameActivity
        mShowUnityGameButton.isEnabled = isGameActivity
    }

    private fun enableShowUnityButtons() {
        if (mActivityType != ActivityType.BOTH) return

        mShowUnityButton.isEnabled = true
        mShowUnityGameButton.isEnabled = true
    }
}