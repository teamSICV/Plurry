//package com.SICV.plurry.raising
//
//import android.app.Dialog
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.view.Window
//import android.view.WindowManager
//import android.widget.Button
//import android.widget.TextView
//import androidx.fragment.app.DialogFragment
//import com.SICV.plurry.R
//
//enum class BoxId {
//    NORMAL,
//    CREW,
//}
//
//interface RaisingItemDialogListener {
//    fun onItemDialogResult(totalItemGrowingAmount: Int, currentNormalItemAmount: Int, currentCrewItemAmount: Int)
//}
//
//class RaisingItemActivity : DialogFragment() {
//    private var currentNormalItemAmount: Int = 0
//    private var currentCrewItemAmount: Int = 0
//    private var totalItemGrowingAmount: Int = 0
//    private var randomMin: Int = 0
//    private var randomMax: Int = 0
//
//    var listener: RaisingItemDialogListener? = null
//
//    companion object {
//        fun newInstance(normalItemAmount: Int, crewItemAmount: Int): RaisingItemActivity {
//            val fragment = RaisingItemActivity()
//            val args = Bundle()
//            args.putInt("currentNormalItemAmount", normalItemAmount)
//            args.putInt("currentCrewItemAmount", crewItemAmount)
//            fragment.arguments = args
//            return fragment
//        }
//    }
//
//    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
//        val dialog = super.onCreateDialog(savedInstanceState)
//
//        // Unity가 백그라운드에서 계속 실행되도록 설정
//        dialog.window?.let { window ->
//            // 투명 배경 설정
//            window.setBackgroundDrawableResource(android.R.color.transparent)
//
//            // Unity 렌더링을 방해하지 않는 플래그들 설정
//            window.setFlags(
//                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
//                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
//                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
//                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
//                        WindowManager.LayoutParams.FLAG_DIM_BEHIND
//            )
//
//            // Dim 효과 제거 (Unity가 보이도록)
//            window.setDimAmount(0.0f)
//        }
//
//        return dialog
//    }
//
//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        return inflater.inflate(R.layout.activity_raising_item, container, false)
//    }
//
//    override fun onStart() {
//        super.onStart()
//        dialog?.window?.let { window ->
//            window.setLayout(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT
//            )
//            window.setGravity(android.view.Gravity.BOTTOM)
//        }
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        currentNormalItemAmount = arguments?.getInt("currentNormalItemAmount", 0) ?: 0
//        currentCrewItemAmount = arguments?.getInt("currentCrewItemAmount", 0) ?: 0
//
//        setupUIElements(view)
//    }
//
//    private fun setupUIElements(view: View) {
//        val btnQuit = view.findViewById<Button>(R.id.b_quit)
//        btnQuit.setOnClickListener {
//            listener?.onItemDialogResult(totalItemGrowingAmount, currentNormalItemAmount, currentCrewItemAmount)
//            dismiss()
//        }
//
//        val btnNormalItem = view.findViewById<Button>(R.id.b_normal)
//        btnNormalItem.setOnClickListener { OpenItemBox(BoxId.NORMAL, view) }
//
//        val btnCrewItem = view.findViewById<Button>(R.id.b_crew)
//        btnCrewItem.setOnClickListener { OpenItemBox(BoxId.CREW, view) }
//
//        updateTextViews(view)
//    }
//
//    private fun updateTextViews(view: View) {
//        val tvNormalItem = view.findViewById<TextView>(R.id.tv_normal)
//        tvNormalItem.text = currentNormalItemAmount.toString()
//
//        val tvCrewlItem = view.findViewById<TextView>(R.id.tv_crew)
//        tvCrewlItem.text = currentCrewItemAmount.toString()
//
//        val tvGetGrowingAmount = view.findViewById<TextView>(R.id.tv_openamount)
//        tvGetGrowingAmount.text = totalItemGrowingAmount.toString()
//    }
//
//    private fun OpenItemBox(inBoxId: BoxId, view: View) {
//        if (inBoxId == BoxId.NORMAL) {
//            if (currentNormalItemAmount > 0) {
//                randomMin = 0
//                randomMax = 100
//                currentNormalItemAmount--
//            }
//        } else {
//            if (currentCrewItemAmount > 0) {
//                randomMin = 50
//                randomMax = 200
//                currentCrewItemAmount--
//            }
//        }
//        totalItemGrowingAmount += (randomMin..randomMax).random()
//        updateTextViews(view)
//    }
//
//    override fun onResume() {
//        super.onResume()
//        // Unity가 계속 실행되도록 보장
//        activity?.let { activity ->
//            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
//        }
//    }
//}