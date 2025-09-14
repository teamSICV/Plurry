package com.SICV.plurry.mypage

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import com.SICV.plurry.R

class MyPageProfileDialog(
    context: Context,
    private val onConfirmCallback: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mypage_change_profileimage)

        Log.d("MyPageProfileDialog", "프로필 이미지 변경 다이얼로그 열림")

        setupButtons()
    }

    private fun setupButtons() {
        val backButton = findViewById<Button>(R.id.myPageProfileBack)
        val okButton = findViewById<Button>(R.id.myPageProfileOk)

        backButton.setOnClickListener {
            dismiss()
        }

        okButton.setOnClickListener {
            Log.d("MyPageProfileDialog", "프로필 이미지 변경 확인")
            dismiss()
            onConfirmCallback()
        }
    }
}