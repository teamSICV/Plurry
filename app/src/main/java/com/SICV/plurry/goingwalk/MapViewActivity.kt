package com.SICV.plurry.goingwalk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.SICV.plurry.R

class MapViewActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var walkInfoText: TextView
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var currentSteps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_view)

        walkInfoText = findViewById(R.id.walkIZnfo)
        val btnEndWalk = findViewById<Button>(R.id.btnEndWalk)

        btnEndWalk.setOnClickListener {
            val intent = Intent(this, GoingWalkMainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // 권한 체크 및 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                1001
            )
        } else {
            initStepSensor()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initStepSensor()
        } else {
            Toast.makeText(this, "걸음 수 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initStepSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        if (stepSensor == null) {
            walkInfoText.text = "걸음 센서를 사용할 수 없습니다."
        }
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            currentSteps++  // 한 걸음 감지될 때마다 1씩 증가
            walkInfoText.text = "거리: 0.0km     |   걸음: $currentSteps 걸음   |     칼로리: 0kcal"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 필요 없음
    }
}
