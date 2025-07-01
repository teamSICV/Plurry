package com.SICV.plurry.raising

import android.util.Log
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R

class RaisingStoryActivity : AppCompatActivity() {
    private var currentLevel : Int = 100
    private val storyCount : Int = 5
    private lateinit var scrollView: ScrollView
    private lateinit var storyContainer: LinearLayout
    private var storyButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Story", "onCreate called")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_raising_story)

        supportActionBar?.hide()
        //window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setFinishOnTouchOutside(false)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        window.setGravity(Gravity.CENTER)

        currentLevel = intent.getIntExtra("currentLevel", 100)

        setupUIElements()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setResult(RESULT_CANCELED)
    }

    private fun StoryPopup(inButtonNum : Int) {
        val intent = Intent(this, RaisingStoryPlayActivity::class.java)
        intent.putExtra("currentStory", inButtonNum)
        startActivity(intent)
    }

    private fun setupUIElements() {
        Log.d("Story", "setupUIElements called")
        val btnQuit = findViewById<Button>(R.id.b_quit)
        btnQuit.setOnClickListener { onBackPressed() }

        scrollView = findViewById(R.id.scrollView)
        storyContainer = findViewById(R.id.storyContainer)

        if (storyContainer == null) {
            Log.e("Story", "storyContainer is null!")
            return
        }
        else{
            Log.d("Story", "storyContainer: $storyContainer")
        }

        for (i in 0 until storyCount) {
            Log.d("Story", "storyCount start : ${i}")
            val storyButton = Button(this).apply {
                text = "스토리 ${i + 1}"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 8, 16, 8)
                }

                if(i < currentLevel) {
                    setBackgroundColor(Color.BLUE)
                    setOnClickListener { StoryPopup(i + 1) }
                } else {
                    setBackgroundColor(Color.GRAY)
                    setOnClickListener { Toast.makeText(this@RaisingStoryActivity,"레벨이 낮습니다!", Toast.LENGTH_SHORT).show() }
                }
            }
            storyButtons.add(storyButton)
            storyContainer.addView(storyButton)
        }
    }
}