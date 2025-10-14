package com.SICV.plurry.ranking

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class RankingAdapter(
    private val context: Context,
    private var rankingList: List<RankingRecord>
) : RecyclerView.Adapter<RankingAdapter.RankingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_ranking_record, parent, false)
        return RankingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RankingViewHolder, position: Int) {
        val item = rankingList[position]

        holder.tvRank.text = item.rank.toString()
        holder.tvNickname.text = item.nickname
        holder.tvRecord.text = item.record

        if (!item.profileImageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(item.profileImageUrl)
                .placeholder(R.drawable.basicprofile)
                .error(R.drawable.basicprofile)
                .into(holder.ivProfile)
        } else {
            holder.ivProfile.setImageResource(R.drawable.basicprofile)
        }
    }

    override fun getItemCount(): Int = rankingList.size

    fun updateData(newRankingList: List<RankingRecord>) {
        this.rankingList = newRankingList
        notifyDataSetChanged()
    }

    class RankingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.rankRanking)
        val tvNickname: TextView = itemView.findViewById(R.id.rankName)
        val tvRecord: TextView = itemView.findViewById(R.id.rankRecord)
        val ivProfile: CircleImageView = itemView.findViewById(R.id.rankProfile)
    }
}