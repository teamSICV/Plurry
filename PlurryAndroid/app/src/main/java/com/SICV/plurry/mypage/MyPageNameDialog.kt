package com.SICV.plurry.mypage

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.SICV.plurry.R
import com.SICV.plurry.security.AndroidSecurityValidator

class MyPageNameDialog(
    context: Context,
    private val currentName: String,
    private val onNameChangeCallback: (String) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mypage_change_name)

        setupViews()
    }

    private fun setupViews() {
        val editText = findViewById<EditText>(R.id.NameEditText)
        val changeButton = findViewById<Button>(R.id.myPageEditName)

        // 보안 검증 추가
        val securityValidator = AndroidSecurityValidator()

        editText.setText(currentName)
        editText.selectAll()

        changeButton.setOnClickListener {
            val newName = editText.text.toString().trim()

            // 보안 검증 적용
            val validationResult = securityValidator.validateNickname(newName)

            if (!validationResult.isValid) {
                Toast.makeText(context, validationResult.message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dismiss()
            onNameChangeCallback(newName)
        }
    }
}