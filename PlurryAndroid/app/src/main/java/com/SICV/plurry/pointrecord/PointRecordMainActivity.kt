package com.SICV.plurry.pointrecord

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R

class PointRecordMainActivity : AppCompatActivity() {

    private lateinit var adapter: CrewPointBottomAdapter
    private lateinit var recyclerView: RecyclerView

    private val imageList = listOf(R.drawable.test1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_point_record_main)

        val bottomSheetFragment = CrewPointBottomFragment()
        bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
    }
}
