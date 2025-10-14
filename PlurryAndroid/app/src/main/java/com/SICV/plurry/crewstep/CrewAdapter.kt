package com.SICV.plurry.crewstep

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.bumptech.glide.Glide

class CrewAdapter(
    private val crewList: List<Crew>,
    private val onItemClick: (Crew) -> Unit
) : RecyclerView.Adapter<CrewAdapter.CrewViewHolder>() {

    inner class CrewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val crewImageView: ImageView = itemView.findViewById(R.id.crewImageView)
        val crewNameTextView: TextView = itemView.findViewById(R.id.crewNameTextView)
        val memberCountTextView: TextView = itemView.findViewById(R.id.memberCountTextView)
        val mainFieldTextView: TextView = itemView.findViewById(R.id.mainFieldTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CrewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.activity_crew_line_crews, parent, false)
        return CrewViewHolder(view)
    }

    override fun onBindViewHolder(holder: CrewViewHolder, position: Int) {
        val crew = crewList[position]
        holder.crewNameTextView.text = crew.name
        holder.memberCountTextView.text = "회원수: ${crew.memberCount}명"
        holder.mainFieldTextView.text = "활동지역: ${crew.mainField}"

        Glide.with(holder.crewImageView.context)
            .load(crew.crewProfileUrl)
            .placeholder(R.drawable.basiccrewprofile)
            .into(holder.crewImageView)

        holder.itemView.setOnClickListener {
            onItemClick(crew)
        }
    }

    override fun getItemCount() = crewList.size
}
