package com.SICV.plurry.goingwalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.SICV.plurry.R

class GoingWalkMainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_going_walk_main)

        val startWalkButton = findViewById<Button>(R.id.startWalkButton)
        startWalkButton.setOnClickListener {
            if (hasAllPermissions()) {
                // 모든 권한 허용된 경우
                startActivity(Intent(this, MapViewActivity::class.java))
            } else {
                // 권한 요청 (권한 없어도 실행되게 할 거니까, 먼저 요청만)
                requestAllPermissions()
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllPermissions() {
        val permissions = getRequiredPermissions()
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun getRequiredPermissions(): MutableList<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        }

        return permissions
    }

    // 사용자가 권한 허용/거절한 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.zip(grantResults.toTypedArray())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }

            if (deniedPermissions.isNotEmpty()) {
                val message = deniedPermissions.joinToString("\n") {
                    when (it) {
                        Manifest.permission.CAMERA -> "카메라 권한"
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION -> "위치 권한"
                        Manifest.permission.ACTIVITY_RECOGNITION -> "신체 활동 권한"
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO -> "저장소 접근 권한"
                        else -> it
                    }
                }
                Toast.makeText(this, "다음 권한이 거부됨:\n$message\n일부 기능이 제한될 수 있습니다.", Toast.LENGTH_LONG).show()
            }

            // 일부 권한이 없어도 앱은 실행 가능하게 함
            startActivity(Intent(this, MapViewActivity::class.java))
        }
    }
}