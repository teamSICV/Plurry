package com.SICV.plurry.pointrecord

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.SICV.plurry.R
import com.SICV.plurry.goingwalk.GoingWalkMainActivity

class PointRecordDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view: View = LayoutInflater.from(requireContext())
            .inflate(R.layout.activity_point_record_popup, null)

        val imageView = view.findViewById<ImageView>(R.id.imageView)
        val textView = view.findViewById<TextView>(R.id.textView3)

        val btnStart = view.findViewById<Button>(R.id.btnStart)
        btnStart.setOnClickListener {
            val intent = Intent(requireContext(), GoingWalkMainActivity::class.java)
            startActivity(intent)
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}