package com.SICV.plurry.raising

import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R
import java.io.BufferedReader
import java.io.InputStreamReader

class RaisingStoryPlayActivity : AppCompatActivity() {
    private lateinit var storyContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private var storyLines = mutableListOf<String>()
    private var currentLineIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_raising_story_play)

        supportActionBar?.hide()
        setFinishOnTouchOutside(true)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        window.setGravity(Gravity.CENTER)

        setupUIElements()
        loadStoryFromAssets()
    }

    private fun setupUIElements() {
        val btnQuit = findViewById<Button>(R.id.b_quit)
        btnQuit.setOnClickListener { onBackPressed() }

        storyContainer = findViewById(R.id.storyContainer)
        scrollView = findViewById(R.id.scrollView)

        // ScrollView에 터치 리스너 추가
        scrollView.setOnTouchListener { _, event ->
            Log.d("Story", "ScrollView touch detected")
            if (event.action == MotionEvent.ACTION_DOWN) {
                addNextStoryLine()
                Log.d("Story", "addNextStoryLine Called")
                true
            } else {
                false
            }
        }
    }

    private fun loadStoryFromAssets() {
        try {
            val inputStream = assets.open("story/teststory.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            storyLines = reader.readLines().toMutableList()
            reader.close()
        } catch (e: Exception) {
            // story 폴더에서 파일을 찾을 수 없는 경우 기본 텍스트 사용
            storyLines = mutableListOf(
                "테스트스토리1",
                "테스트스토리2",
                "테스트스토리3",
                "테스트스토리4",
                "테스트스토리5"
            )
        }
    }

    private fun addNextStoryLine() {
        if (currentLineIndex < storyLines.size) {
            val textView = TextView(this).apply {
                text = storyLines[currentLineIndex]
                textSize = 20f
                setPadding(0, 20, 0, 20)
                gravity = Gravity.CENTER
            }

            storyContainer.addView(textView)
            currentLineIndex++

            // 스크롤을 맨 아래로 이동
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
}