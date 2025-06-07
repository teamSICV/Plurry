package com.SICV.plurry.goingwalk

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.SICV.plurry.R

class PointSelectFragment : DialogFragment() {

    private lateinit var spinner: Spinner
    private lateinit var confirmBtn: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ExploreAdapter
    private val placeList = mutableListOf<PlaceData>()

    private val radiusValues = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0)

    private var userLat = 0.0
    private var userLng = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.activity_goingwalk_point_select, container, false)

        spinner = view.findViewById(R.id.spinnerRadius)
        confirmBtn = view.findViewById(R.id.btnConfirmRadius)
        recyclerView = view.findViewById(R.id.recyclerViewPlaces)

        val spinnerOptions = listOf("1km", "1.5km", "2km", "2.5km", "3km", "3.5km", "4km", "4.5km", "5km")
        val spinnerAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, spinnerOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.BLACK)
                return view
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        confirmBtn.setOnClickListener {
            val selectedRadius = radiusValues.getOrNull(spinner.selectedItemPosition) ?: 1.0
            updateUserLocationThenLoad(selectedRadius)
        }

        adapter = ExploreAdapter(placeList) { place ->
            ExploreConfirmDialog(place, this).show(parentFragmentManager, "ExploreConfirmDialog")
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(context, 3)

        return view
    }

    private fun updateUserLocationThenLoad(radiusKm: Double) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLat = location.latitude
                userLng = location.longitude
            }
            loadNearbyPoints(radiusKm)
        }.addOnFailureListener {
            Toast.makeText(context, "위치 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadNearbyPoints(radiusKm: Double) {
        Firebase.firestore.collection("Places").get().addOnSuccessListener { docs ->
            val userLocation = Location("user").apply {
                latitude = userLat
                longitude = userLng
            }

            placeList.clear()

            for (doc in docs) {
                val geo = doc.getGeoPoint("geo") ?: continue
                val placeLocation = Location("place").apply {
                    latitude = geo.latitude
                    longitude = geo.longitude
                }

                if (userLocation.distanceTo(placeLocation) <= radiusKm * 1000) {
                    val imgUrl = doc.getString("myImgUrl") ?: continue
                    placeList.add(PlaceData(geo.latitude, geo.longitude, imgUrl))
                }
            }

            adapter.notifyDataSetChanged()
        }
    }

    data class PlaceData(val lat: Double, val lng: Double, val imageUrl: String)

    inner class ExploreAdapter(
        private val items: List<PlaceData>,
        private val onClick: (PlaceData) -> Unit
    ) : RecyclerView.Adapter<ExploreAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.imgPlace)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.activity_goingwalk_itemplaceimage, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            Glide.with(holder.imageView).load(item.imageUrl).into(holder.imageView)
            holder.itemView.setOnClickListener { onClick(item) }
        }
    }

    class ExploreConfirmDialog(private val place: PlaceData, private val parent: DialogFragment) : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
            val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.activity_goingwalk_exploreconfirm, null)

            val imageView = view.findViewById<ImageView>(R.id.dialogImage)
            val btnStart = view.findViewById<Button>(R.id.btnStartExplore)
            val btnCancel = view.findViewById<Button>(R.id.btnCancelExplore)

            Glide.with(view).load(place.imageUrl).into(imageView)

            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create()

            btnCancel.setOnClickListener {
                dismiss()
            }

            btnStart.setOnClickListener {
                val fragment = ExploreTrackingFragment.newInstance(place.lat, place.lng, place.imageUrl)
                val activity = activity as? AppCompatActivity ?: return@setOnClickListener

                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainerExplore, fragment)
                    .addToBackStack(null)
                    .commit()

                parent.dismiss() // ✅ 거리 선택 다이얼로그 닫기
                dismiss()        // ✅ 탐색 확인 다이얼로그 닫기
            }

            return dialog
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
