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
import androidx.core.content.ContextCompat
import com.SICV.plurry.R
import java.io.BufferedReader
import java.io.InputStreamReader

class RaisingStoryPlayActivity : AppCompatActivity() {
    private lateinit var storyContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private var currentStory : Int = 0
    private var storyLines = mutableListOf<List<String>>()
    private var currentLineIndex = 1

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

        currentStory = intent.getIntExtra("currentStory", 0)

        loadStoryFromAssets()
        setupUIElements()
    }

    private fun setupUIElements() {
        val btnQuit = findViewById<Button>(R.id.b_quit)
        btnQuit.setOnClickListener { onBackPressed() }

        storyContainer = findViewById(R.id.storyContainer)
        scrollView = findViewById(R.id.scrollView)

        // ScrollView에 터치 리스너 추가
        scrollView.setOnTouchListener { _, event ->
            //Log.d("Story", "ScrollView touch detected")
            if (event.action == MotionEvent.ACTION_DOWN) {
                addNextStoryLine()
                //Log.d("Story", "addNextStoryLine Called")
                true
            } else {
                false
            }
        }
    }

    private fun loadStoryFromAssets() {
        //Log.d("Story", "loadStoryFromAssets called")
        val assetDir : String = "story/plurrystory$currentStory.csv"
        //Log.d("Story", "assetDir : ${assetDir}")
        try {
            val inputStream = assets.open(assetDir)
            val reader = BufferedReader(InputStreamReader(inputStream))
            storyLines = reader.readLines()
                .map { it.split(",") }
                .toMutableList()
            reader.close()
        } catch (e: Exception) {
            // story 폴더에서 파일을 찾을 수 없는 경우 기본 텍스트 사용
            storyLines = mutableListOf(listOf("스토리 로드 실패"))
        }
    }

    private fun addNextStoryLine() {
        if (currentLineIndex < storyLines.size) {

            val textView = TextView(this).apply {
                text = storyLines[currentLineIndex][0]

                when (storyLines[currentLineIndex][1]) {
                    "r" -> textAlignment = TextView.TEXT_ALIGNMENT_TEXT_END
                    "l" -> textAlignment = TextView.TEXT_ALIGNMENT_TEXT_START
                    else -> textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                }

                when (storyLines[currentLineIndex][2]) {
                    "b" -> setTypeface(null, android.graphics.Typeface.BOLD)
                    "i" -> setTypeface(null, android.graphics.Typeface.ITALIC)
                    else -> setTypeface(null, android.graphics.Typeface.NORMAL)
                }

                textSize = storyLines[currentLineIndex][3].toFloat() ?: 17f

                when (storyLines[currentLineIndex][4]) {
                    "b" -> setTextColor(ContextCompat.getColor(context, R.color.txt_blue_light))
                    "g" -> setTextColor(ContextCompat.getColor(context, R.color.txt_grey_light))
                    else -> setTextColor(ContextCompat.getColor(context, R.color.txt_white))
                }

                setPadding(0, 0, 0, 100)
            }

            storyContainer.addView(textView)
            currentLineIndex++

            // 스크롤을 맨 아래로 이동
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }

            if(currentLineIndex==storyLines.size) {
                findViewById<TextView>(R.id.tv_info).text = "스토리가 종료되었습니다"
            }
        }
    }
}