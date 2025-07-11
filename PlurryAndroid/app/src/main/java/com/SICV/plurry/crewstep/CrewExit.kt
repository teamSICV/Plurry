package com.SICV.plurry.crewstep

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import com.SICV.plurry.R

class CrewExit(context: Context, private val onConfirm: () -> Unit) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_exit)

        setCancelable(false)
        setCanceledOnTouchOutside(false)

        val btnConfirm = findViewById<Button>(R.id.btnExitOk)
        val btnCancel = findViewById<Button>(R.id.btnExitBack)

        btnConfirm.setOnClickListener {
            onConfirm()
            dismiss()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }
}