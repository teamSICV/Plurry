package com.SICV.plurry.pointrecord

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.bumptech.glide.Glide

class CrewPointBottomAdapter(
    private val context: Context,
    private val imageList: List<PointRecordMainActivity.PlaceData>,
    private val crewId: String = ""
) : RecyclerView.Adapter<CrewPointBottomAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.crewPointImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.activity_point_record_bottom_image, parent, false)
        return ImageViewHolder(itemView)
    }

    override fun getItemCount(): Int = imageList.size

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val place = imageList[position]

        Glide.with(context)
            .load(place.imageUrl)
            .into(holder.imageView)

        holder.imageView.setOnClickListener {
            val dialog = PointRecordDialog.newInstance(
                place.imageUrl,
                place.name,
                place.description,
                place.placeId,
                place.lat,
                place.lng,
                crewId,
                place.isVisited // 실제 방문 여부 전달
            )
            dialog.show((context as AppCompatActivity).supportFragmentManager, "CrewPointDialog")
        }
    }
}