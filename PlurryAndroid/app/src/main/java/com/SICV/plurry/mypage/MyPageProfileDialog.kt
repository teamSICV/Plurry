package com.SICV.plurry.mypage

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import com.SICV.plurry.R

class MyPageProfileDialog(
    context: Context,
    private val onConfirmCallback: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mypage_change_profileimage)

        setupButtons()
    }

    private fun setupButtons() {
        val backButton = findViewById<Button>(R.id.myPageProfileBack)
        val okButton = findViewById<Button>(R.id.myPageProfileOk)

        backButton.setOnClickListener {
            dismiss()
        }

        okButton.setOnClickListener {
            dismiss()
            onConfirmCallback()
        }
    }
}