package com.SICV.plurry.raising

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R

enum class BoxId {
    NORMAL,
    CREW,
}

class RaisingItemActivity : AppCompatActivity() {
    private var currentNormalItemAmount : Int = 0;
    private var currentCrewItemAmount : Int = 0;
    private var totalItemGrowingAmount : Int = 0;
    var randomMin : Int = 0;
    var randomMax : Int = 0;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_raising_item)

        supportActionBar?.hide()
        //window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setFinishOnTouchOutside(true)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM)

        currentNormalItemAmount = intent.getIntExtra("currentNormalItemAmount", 0)
        currentCrewItemAmount = intent.getIntExtra("currentCrewItemAmount", 0)

        setupUIElements()
    }

    private fun setupUIElements() {
        val btnQuit = findViewById<Button>(R.id.b_quit)
        btnQuit.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("totalItemGrowingAmount", totalItemGrowingAmount)
            resultIntent.putExtra("currentNormalItemAmount", currentNormalItemAmount)
            resultIntent.putExtra("currentCrewItemAmount", currentCrewItemAmount)
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        val btnNormalItem = findViewById<Button>(R.id.b_normal)
        btnNormalItem.setOnClickListener { OpenItemBox(BoxId.NORMAL) }

        val btnCrewItem = findViewById<Button>(R.id.b_crew)
        btnCrewItem.setOnClickListener { OpenItemBox(BoxId.CREW) }

        val tvNormalItem = findViewById<TextView>(R.id.tv_normal)
        tvNormalItem.text = currentNormalItemAmount.toString()

        val tvCrewlItem = findViewById<TextView>(R.id.tv_crew)
        tvCrewlItem.text = currentCrewItemAmount.toString()

        val tvGetGrowingAmount = findViewById<TextView>(R.id.tv_openamount)
        tvGetGrowingAmount.text = totalItemGrowingAmount.toString()
    }

    private fun UpdateTextView() {
        runOnUiThread {
            val tvNormalItem = findViewById<TextView>(R.id.tv_normal)
            tvNormalItem.text = currentNormalItemAmount.toString()

            val tvCrewlItem = findViewById<TextView>(R.id.tv_crew)
            tvCrewlItem.text = currentCrewItemAmount.toString()

            val tvGetGrowingAmount = findViewById<TextView>(R.id.tv_openamount)
            tvGetGrowingAmount.text = totalItemGrowingAmount.toString()
        }
    }

    private fun OpenItemBox(inBoxId : BoxId) {
        if(inBoxId == BoxId.NORMAL) {
            if(currentNormalItemAmount>0) {
                randomMin = 0
                randomMax = 100
                currentNormalItemAmount--;
            }
        } else {
            if(currentCrewItemAmount>0) {
                randomMin = 50
                randomMax = 200
                currentCrewItemAmount--;
            }
        }
        totalItemGrowingAmount += (randomMin..randomMax).random()
        UpdateTextView()
    }



}