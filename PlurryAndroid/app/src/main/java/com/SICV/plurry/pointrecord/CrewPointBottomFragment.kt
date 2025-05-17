package com.SICV.plurry.pointrecord

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore

class CrewPointBottomFragment : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myLatitude: Double? = null
    private var myLongitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_point_record_bottom_crew, container, false)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return String.format("%.2f", earthRadius * c).toDouble()
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setDimAmount(0f)
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.crewPointRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                myLatitude = location.latitude
                myLongitude = location.longitude
                fetchImageUrlsFromFirestore()
            }
        }


        fetchImageUrlsFromFirestore()
    }

    private fun fetchImageUrlsFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
        val imageUrlList = mutableListOf<String>()

        db.collection("Places")
            .get()
            .addOnSuccessListener { documents ->
                val tempList = mutableListOf<PlaceData>()
                val finalList = mutableListOf<PlaceData>()
                var processedCount = 0

                for (doc in documents) {
                    val imageUrl = if (doc.getBoolean("myImg")==true)
                        doc.getString("myImgUrl") ?: ""
                    else
                        doc.getString("baseImgUrl") ?:""

                    val name = doc.getString("name") ?: "이름 없음"
                    val addedBy = doc.getString("addedBy") ?: ""
                    val geoPoint = doc.getGeoPoint("geo")
                    val distanceText = if (geoPoint != null && myLatitude != null && myLongitude != null) {
                        val distance = calculateDistance(
                            myLatitude!!, myLongitude!!,
                            geoPoint.latitude, geoPoint.longitude
                        )
                        "$distance km"
                    } else {
                        "거리 정보 없음"
                    }

                    val description = "추가한 유저: $addedBy\n거리: $distanceText"

                    tempList.add(PlaceData(imageUrl,name,description))
                }
                if (tempList.isEmpty()){
                    recyclerView.adapter = CrewPointBottomAdapter(requireContext(),finalList)
                    return@addOnSuccessListener
            }

                for (place in tempList){
                    if(place.imageUrl.startsWith("gs://")){
                        val ref = storage.getReferenceFromUrl(place.imageUrl)
                        ref.downloadUrl
                            .addOnSuccessListener { uri ->
                                finalList.add(PlaceData(uri.toString(),place.name, place.description))
                                processedCount++

                                if(processedCount == tempList.size){
                                    recyclerView.adapter = CrewPointBottomAdapter(requireContext(),finalList)
                                }
                            }
                            .addOnFailureListener{
                                processedCount++
                                if(processedCount == tempList.size){
                                    recyclerView.adapter = CrewPointBottomAdapter(requireContext(),finalList)
                                }
                            }
                    }else{
                        finalList.add(place)
                        processedCount++
                        if(processedCount == tempList.size){
                            recyclerView.adapter = CrewPointBottomAdapter(requireContext(), finalList)
                        }
                    }
                }
            }
            .addOnFailureListener {
                android.util.Log.e("FirestoreImage", "불러오기 실패: ${it.message}")
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                behavior.peekHeight = 800
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                behavior.isDraggable = true
            }
        }
    }
}
