package com.SICV.plurry.raising

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R

class RaisingStoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_raising_story)

        //supportActionBar?.hide()
/*        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setFinishOnTouchOutside(true)
        window.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.CENTER)*/

        setupUIElements()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setResult(RESULT_CANCELED)
    }

    private fun setupUIElements() {
        val btnQuit = findViewById<Button>(R.id.b_quit)
        btnQuit.setOnClickListener { onBackPressed() }

        val btnStory1 = findViewById<Button>(R.id.b_story1)
        btnQuit.setOnClickListener { onBackPressed() }
    }

    private fun StoryPopup() {
        val intent = Intent(this, RaisingStoryPlayActivity::class.java)
        startActivity(intent)
    }
}