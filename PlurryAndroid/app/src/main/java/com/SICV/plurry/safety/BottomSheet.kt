package com.SICV.plurry.safety

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.SICV.plurry.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

class BottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.activity_goingwalk_safety_bottomsheet, container, false)

        val current = view.findViewById<MaterialTextView>(R.id.tvCurrentRoute)
        val detour = view.findViewById<MaterialTextView>(R.id.tvDetourRoute)
        val btnStay = view.findViewById<MaterialButton>(R.id.btnStayRoute)
        val btnDetour = view.findViewById<MaterialButton>(R.id.btnTakeDetour)

        // 기본 텍스트 (나중에 arguments로 교체 가능)
        current.text = "현재 경로: 12분 · 안전도 38"
        detour.text = "우회 경로: 15분(+3) · 안전도 75"

        btnStay.setOnClickListener {
            dismiss() // 닫기
        }

        btnDetour.setOnClickListener {
            // TODO: 우회 네비게이션 로직 연결
            dismiss()
        }

        return view
    }
}
