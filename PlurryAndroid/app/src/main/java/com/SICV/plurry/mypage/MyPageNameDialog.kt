package com.SICV.plurry.mypage

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.SICV.plurry.R

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

        editText.setText(currentName)
        editText.selectAll()

        changeButton.setOnClickListener {
            val newName = editText.text.toString().trim()

            if (newName.isEmpty()) {
                Toast.makeText(context, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newName.length > 10) {
                Toast.makeText(context, "닉네임은 10자 이하로 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dismiss()
            onNameChangeCallback(newName)
        }
    }
}