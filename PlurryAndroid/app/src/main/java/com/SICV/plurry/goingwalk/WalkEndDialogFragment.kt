package com.SICV.plurry.goingwalk
import com.SICV.plurry.R
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class WalkEndDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_walk_end, null)

        val distance = arguments?.getString("distance") ?: "0.00"
        val steps = arguments?.getInt("steps") ?: 0
        val calories = arguments?.getString("calories") ?: "0.0"

        val tvDistance = view.findViewById<TextView>(R.id.tvWalkDistance)
        val tvSteps = view.findViewById<TextView>(R.id.tvWalkSteps)
        val tvCalories = view.findViewById<TextView>(R.id.tvWalkCalories)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirmWalkEnd)

        tvDistance.text = "거리: $distance km"
        tvSteps.text = "걸음 수: $steps 걸음"
        tvCalories.text = "칼로리 소모: $calories kcal"

        btnConfirm.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), GoingWalkMainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            requireActivity().finish()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }

    companion object {
        fun newInstance(distance: String, steps: Int, calories: String): WalkEndDialogFragment {
            val fragment = WalkEndDialogFragment()
            val args = Bundle().apply {
                putString("distance", distance)
                putInt("steps", steps)
                putString("calories", calories)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
