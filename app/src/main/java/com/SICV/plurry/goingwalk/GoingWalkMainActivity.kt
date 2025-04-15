package com.SICV.plurry.goingwalk

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R

class GoingWalkMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_going_walk_main)

        val startWalkButton = findViewById<Button>(R.id.startWalkButton)
        startWalkButton.setOnClickListener {
            val intent = Intent(this, MapViewActivity::class.java)
            startActivity(intent)
        }
    }
}