package com.SICV.plurry.pointrecord

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resumeWithException

class CrewPointBottomFragment : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnDateArray: TextView
    private lateinit var btnDisArray: TextView

    private var myLatitude: Double? = null
    private var myLongitude: Double? = null
    private var crewId: String? = null
    private var allPlaces = mutableListOf<PlaceData>()
    private lateinit var adapter: CrewPointBottomAdapter
    private val handler = Handler(Looper.getMainLooper())

    // 페이징 관련 변수
    private val pageSize = 3 // 한 번에 로드할 개수

    enum class SortType {
        DATE_DESC,
        DATE_ASC,
        DISTANCE_ASC,
        DISTANCE_DESC
    }

    private var currentSortType = SortType.DATE_DESC

    companion object {
        private const val ARG_CREW_ID = "crew_id"

        fun newInstance(crewId: String): CrewPointBottomFragment {
            val fragment = CrewPointBottomFragment()
            val args = Bundle()
            args.putString(ARG_CREW_ID, crewId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
        arguments?.let {
            crewId = it.getString(ARG_CREW_ID)
        }
        Log.d("CrewPointBottom", "Fragment 생성 - crewId: $crewId")
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

        initViews(view)
        setupSortButtons()
        initLocationService()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.crewPointRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

        btnDateArray = view.findViewById(R.id.BtnDateArray)
        btnDisArray = view.findViewById(R.id.BtnDisArray)

        adapter = CrewPointBottomAdapter(requireContext(), allPlaces)
        recyclerView.adapter = adapter
    }

    private fun setupSortButtons() {
        btnDateArray.setOnClickListener {
            sortPlacesByDate()
        }

        btnDisArray.setOnClickListener {
            sortPlacesByDistance()
        }
        updateSortButtonUI()
    }

    private fun initLocationService() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    myLatitude = location.latitude
                    myLongitude = location.longitude
                    Log.d("CrewPointBottom", "위치 정보 획득: $myLatitude, $myLongitude")
                } else {
                    Log.d("CrewPointBottom", "위치 정보를 가져올 수 없음")
                }
                fetchCrewMembersAndPlaces()
            }.addOnFailureListener { exception ->
                Log.e("CrewPointBottom", "위치 정보 가져오기 실패", exception)
                fetchCrewMembersAndPlaces()
            }
        } else {
            Log.d("CrewPointBottom", "위치 권한 없음")
            fetchCrewMembersAndPlaces()
        }
    }

    private fun sortPlacesByDate() {
        currentSortType = when (currentSortType) {
            SortType.DATE_DESC -> SortType.DATE_ASC
            SortType.DATE_ASC -> SortType.DATE_DESC
            else -> SortType.DATE_DESC
        }

        updateSortButtonUI()
        applySorting()
        Log.d("CrewPointBottom", "날짜순 정렬 완료 - ${if (currentSortType == SortType.DATE_DESC) "최신순" else "오래된순"}")
    }

    private fun sortPlacesByDistance() {
        if (myLatitude == null || myLongitude == null) {
            Toast.makeText(requireContext(), "위치 정보가 없어 거리순 정렬을 할 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        currentSortType = when (currentSortType) {
            SortType.DISTANCE_ASC -> SortType.DISTANCE_DESC
            SortType.DISTANCE_DESC -> SortType.DISTANCE_ASC
            else -> SortType.DISTANCE_ASC
        }

        updateSortButtonUI()
        applySorting()
        Log.d("CrewPointBottom", "거리순 정렬 완료 - ${if (currentSortType == SortType.DISTANCE_ASC) "가까운 순" else "먼 순"}")
    }

    private fun updateSortButtonUI() {
        when (currentSortType) {
            SortType.DATE_DESC -> {
                btnDateArray.isSelected = true
                btnDisArray.isSelected = false
                btnDateArray.text = "최신순"
                btnDateArray.setTextColor(resources.getColor(R.color.black, null))
                btnDisArray.setTextColor(resources.getColor(R.color.gray, null))
            }
            SortType.DATE_ASC -> {
                btnDateArray.isSelected = true
                btnDisArray.isSelected = false
                btnDateArray.text = "오래된순"
                btnDateArray.setTextColor(resources.getColor(R.color.black, null))
                btnDisArray.setTextColor(resources.getColor(R.color.gray, null))
            }
            SortType.DISTANCE_ASC, SortType.DISTANCE_DESC -> {
                btnDateArray.isSelected = false
                btnDisArray.isSelected = true
                btnDateArray.text = "최신순"
                btnDateArray.setTextColor(resources.getColor(R.color.gray, null))
                btnDisArray.setTextColor(resources.getColor(R.color.black, null))
            }
        }
    }

    private fun updateRecyclerView(newList: List<PlaceData>) {
        allPlaces.clear()
        allPlaces.addAll(newList)
        adapter.notifyDataSetChanged()
    }

    private fun fetchCrewMembersAndPlaces() {
        Log.d("CrewPointBottom", "크루 멤버와 장소 정보 가져오기 시작 - crewId: $crewId")

        crewId?.let { id ->
            val db = FirebaseFirestore.getInstance()

            db.collection("Crew").document(id).collection("member").document("members")
                .get()
                .addOnSuccessListener { memberDocument ->
                    Log.d("CrewPointBottom", "크루 멤버 문서 가져오기 성공")

                    if (memberDocument.exists()) {
                        val memberUids = mutableListOf<String>()
                        val memberData = memberDocument.data

                        Log.d("CrewPointBottom", "크루 멤버 데이터: $memberData")

                        memberData?.keys?.forEach { uid ->
                            memberUids.add(uid)
                        }

                        Log.d("CrewPointBottom", "크루 멤버 UID 목록: $memberUids")

                        if (memberUids.isNotEmpty()) {
                            fetchPlacesByMembers(memberUids)
                        } else {
                            Log.d("CrewPointBottom", "크루 멤버가 없음")
                            updateRecyclerView(emptyList())
                        }
                    } else {
                        Log.d("CrewPointBottom", "크루 멤버 문서가 존재하지 않음")
                        updateRecyclerView(emptyList())
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("CrewPointBottom", "크루 멤버 정보 불러오기 실패", exception)
                    updateRecyclerView(emptyList())
                }
        } ?: run {
            Log.e("CrewPointBottom", "crewId가 null입니다")
            updateRecyclerView(emptyList())
        }
    }

    private fun fetchPlacesByMembers(memberUids: List<String>) {
        Log.d("CrewPointBottom", "멤버들의 장소 정보 가져오기 시작 - 멤버 수: ${memberUids.size}")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = FirebaseFirestore.getInstance()
                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()

                // whereIn 쿼리는 최대 10개까지만 지원하므로 청크로 나누어 처리
                val chunks = memberUids.chunked(10)
                val allPlacesData = mutableListOf<PlaceData>()

                Log.d("CrewPointBottom", "청크 개수: ${chunks.size}")

                for ((index, chunk) in chunks.withIndex()) {
                    Log.d("CrewPointBottom", "청크 $index 처리 중 - 멤버들: $chunk")

                    try {
                        val documents = db.collection("Places")
                            .whereIn("addedBy", chunk)
                            .get()
                            .await()
                        val documentList = documents.documents
                        val pages = documentList.chunked(pageSize)

                        for ((pageIndex, page) in pages.withIndex()) {
                            Log.d("CrewPointBottom", "페이지 $pageIndex 처리 중 - 문서 수: ${page.size}")

                            val pageData = mutableListOf<PlaceData>()

                            for (doc in page) {
                                try {
                                    Log.d("CrewPointBottom", "장소 데이터: ${doc.data}")

                                    val imageUrl = if (doc.getBoolean("myImg") == true)
                                        doc.getString("myImgUrl") ?: ""
                                    else
                                        doc.getString("baseImgUrl") ?: ""

                                    val name = doc.getString("name") ?: "이름 없음"
                                    val addedBy = doc.getString("addedBy") ?: ""
                                    val geoPoint = doc.getGeoPoint("geo")
                                    val placeId = doc.id
                                    val lat = geoPoint?.latitude ?: 0.0
                                    val lng = geoPoint?.longitude ?: 0.0
                                    val imageTime = doc.getLong("imageTime")

                                    val distanceText = if (geoPoint != null && myLatitude != null && myLongitude != null) {
                                        val distance = calculateDistance(
                                            myLatitude!!, myLongitude!!,
                                            geoPoint.latitude, geoPoint.longitude
                                        )
                                        "${distance}km"
                                    } else {
                                        "거리 정보 없음"
                                    }

                                    val description = "추가한 유저: $addedBy\n거리: $distanceText"

                                    val finalImageUrl = if (imageUrl.startsWith("gs://")) {
                                        try {
                                            val ref = storage.getReferenceFromUrl(imageUrl)
                                            ref.downloadUrl.await().toString()
                                        } catch (e: Exception) {
                                            Log.e("CrewPointBottom", "이미지 URL 변환 실패: $imageUrl", e)
                                            imageUrl
                                        }
                                    } else {
                                        imageUrl
                                    }

                                    pageData.add(PlaceData(finalImageUrl, name, description, placeId, lat, lng, imageTime))

                                    Log.d("CrewPointBottom", "장소 추가: $name (by: $addedBy, placeId: $placeId)")
                                } catch (e: Exception) {
                                    Log.e("CrewPointBottom", "개별 장소 처리 중 오류", e)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                val startIndex = allPlaces.size
                                allPlaces.addAll(pageData)
                                adapter.notifyItemRangeInserted(startIndex, pageData.size)
                                Log.d("CrewPointBottom", "페이지 $pageIndex 완료 - 추가된 장소: ${pageData.size}개")
                            }

                            delay(50)
                        }
                    } catch (e: Exception) {
                        Log.e("CrewPointBottom", "청크 $index 처리 중 오류", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    Log.d("CrewPointBottom", "모든 데이터 로드 완료 - 총 장소: ${allPlaces.size}개")
                    applySorting()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("CrewPointBottom", "데이터 로드 중 전체 오류", e)
                    updateRecyclerView(emptyList())
                }
            }
        }
    }

    private fun applySorting() {
        val sortedList = when (currentSortType) {
            SortType.DATE_DESC -> allPlaces.sortedByDescending { it.imageTime ?: 0L }
            SortType.DATE_ASC -> allPlaces.sortedBy { it.imageTime ?: Long.MAX_VALUE }
            SortType.DISTANCE_ASC -> {
                if (myLatitude != null && myLongitude != null) {
                    allPlaces.sortedBy { place ->
                        calculateDistance(myLatitude!!, myLongitude!!, place.lat, place.lng)
                    }
                } else {
                    allPlaces.sortedByDescending { it.imageTime ?: 0L }
                }
            }
            SortType.DISTANCE_DESC -> {
                if (myLatitude != null && myLongitude != null) {
                    allPlaces.sortedByDescending { place ->
                        calculateDistance(myLatitude!!, myLongitude!!, place.lat, place.lng)
                    }
                } else {
                    allPlaces.sortedByDescending { it.imageTime ?: 0L }
                }
            }
        }

        updateRecyclerView(sortedList)
    }

    // Task를 코루틴으로 변환하는 확장 함수
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                if (task.exception != null) {
                    cont.resumeWithException(task.exception!!)
                } else {
                    cont.resume(task.result, null)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheetDialog = dialog as BottomSheetDialog
        bottomSheetDialog.behavior.apply {
            peekHeight = 800
            state = BottomSheetBehavior.STATE_COLLAPSED
            isDraggable = true
        }
    }
}