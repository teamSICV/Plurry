package com.SICV.plurry.crewstep

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R


class WalkRecordAdapter(private val recordList: List<WalkRecord>) :
    RecyclerView.Adapter<WalkRecordAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userId: TextView = view.findViewById(R.id.textUserId)
        val time: TextView = view.findViewById(R.id.textTime)
        val distance: TextView = view.findViewById(R.id.textDistance)
        val duration: TextView = view.findViewById(R.id.textDuration)
        val calories: TextView = view.findViewById(R.id.textCalories)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_crew_line_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = recordList[position]
        holder.userId.text = record.name
        holder.time.text = record.time
        holder.distance.text = record.distance
        holder.duration.text = record.duration
        holder.calories.text = record.calories
    }

    override fun getItemCount(): Int = recordList.size
}
