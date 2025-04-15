package com.SICV.plurry.pointrecord

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CrewPointBottomFragment : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false  // <- 배경 누르더라도 닫히지 않게 함
    }

    override  fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_point_record_bottom_crew, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setDimAmount(0f)
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.crewPointRecyclerView)

        val layoutManager = GridLayoutManager(requireContext(),3)
        recyclerView.layoutManager = layoutManager

        val imageList = listOf(R.drawable.test1, R.drawable.test1, R.drawable.test1, R.drawable.test1, R.drawable.test1)
        val adapter = CrewPointBottomAdapter(requireContext(), imageList)
        recyclerView.adapter = adapter
    }

}