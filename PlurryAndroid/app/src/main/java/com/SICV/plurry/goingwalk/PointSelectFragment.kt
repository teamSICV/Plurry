package com.SICV.plurry.goingwalk

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Tasks // Tasks 클래스를 사용하기 위해 import 추가
import com.google.firebase.firestore.DocumentSnapshot // DocumentSnapshot import 추가

class PointSelectFragment : DialogFragment() {

    private lateinit var spinner: Spinner
    private lateinit var confirmBtn: Button
    private lateinit var crewExploreBtn: Button // 🚀 NEW: 크루 탐색 버튼
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ExploreAdapter
    private val placeList = mutableListOf<PlaceData>()
    // 🚀 수정: visitedPlaceInfo를 VisitedPlaceDetails를 포함하는 Map으로 변경
    private val visitedPlaceInfo = mutableMapOf<String, VisitedPlaceDetails>() // placeId to VisitedPlaceDetails
    // 🚀 NEW: 사용자가 추가한 장소 ID를 저장할 Set
    private val userAddedPlaceIds = mutableSetOf<String>()

    // 🚀 오류 수정: radiusValues 재선언
    private val radiusValues = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0)

    // 🚀 수정: 레벨별 탐색 가능 거리 정의 (1레벨: 1km, 2레벨: 1.5km, ..., 9레벨 이상: 5km)
    private val levelToRadiusMap = mapOf(
        1 to 1.0,
        2 to 1.5,
        3 to 2.0,
        4 to 2.5,
        5 to 3.0,
        6 to 3.5,
        7 to 4.0,
        8 to 4.5,
        9 to 5.0 // 9레벨 이상은 5km
    )

    private var userLat = 0.0
    private var userLng = 0.0
    private var userLevel = 1 // 🚀 NEW: 사용자 레벨을 저장할 변수 (기본값 1)

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.activity_goingwalk_point_select, container, false)

        spinner = view.findViewById(R.id.spinnerRadius)
        confirmBtn = view.findViewById(R.id.btnConfirmRadius)
        crewExploreBtn = view.findViewById(R.id.btnCrewExplore) // 🚀 NEW: 크루 탐색 버튼 초기화
        recyclerView = view.findViewById(R.id.recyclerViewPlaces)

        auth = FirebaseAuth.getInstance()

        // 🚀 수정: 스피너 옵션을 사용자 레벨에 따라 동적으로 설정
        setupRadiusSpinner()

        confirmBtn.setOnClickListener {
            val selectedRadius = radiusValues.getOrNull(spinner.selectedItemPosition) ?: 1.0
            // 🚀 NEW: 선택된 반경이 사용자 레벨이 탐색 가능한 최대 반경을 초과하는지 확인
            val maxAllowedRadius = levelToRadiusMap[userLevel] ?: 1.0
            if (selectedRadius > maxAllowedRadius) {
                Toast.makeText(context, "선택하신 거리는 현재 레벨에서 탐색할 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 거리 탐색 버튼 클릭 시, 방문 장소 로드 후 위치 업데이트 및 주변 장소 로드
            loadVisitedPlacesThenUpdateLocation(selectedRadius)
        }

        // 🚀 NEW: 크루 탐색 버튼 클릭 리스너
        crewExploreBtn.setOnClickListener {
            loadCrewPlaces()
        }

        // 🚀 수정: ExploreAdapter에 PlaceData만 넘기도록 유지 (isVisitedWithImage 필드 추가로 처리)
        adapter = ExploreAdapter(placeList) { place ->
            ExploreConfirmDialog(place, this).show(parentFragmentManager, "ExploreConfirmDialog")
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(context, 3)

        // 🚀 NEW: 사용자 레벨을 로드하는 함수 호출
        loadUserLevel()

        return view
    }

    // 🚀 NEW: 사용자 레벨을 로드하는 함수
    private fun loadUserLevel() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.d("PointSelectFragment", "사용자 ID를 찾을 수 없습니다. 기본 레벨 1로 설정.")
            setupRadiusSpinner() // 사용자 레벨이 없으면 기본 스피너 설정
            return
        }

        Firebase.firestore.collection("Game")
            .document("users")
            .collection("userReward")
            .document(userId) // 사용자 UID로 문서 참조
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    userLevel = documentSnapshot.getLong("level")?.toInt() ?: 1
                    Log.d("PointSelectFragment", "사용자 레벨 로드 성공: $userLevel")
                } else {
                    userLevel = 1 // 문서가 없으면 기본 레벨 1
                    Log.d("PointSelectFragment", "사용자 문서가 없습니다. 기본 레벨 1로 설정.")
                }
                setupRadiusSpinner() // 레벨 로드 후 스피너 설정
            }
            .addOnFailureListener { e ->
                Log.e("PointSelectFragment", "사용자 레벨 로드 오류: ${e.message}", e)
                userLevel = 1 // 오류 발생 시 기본 레벨 1
                setupRadiusSpinner() // 오류 발생 시 스피너 설정
            }
    }

    // 🚀 NEW: 사용자 레벨에 따라 스피너 옵션을 설정하는 함수
    private fun setupRadiusSpinner() {
        val maxAllowedRadius = levelToRadiusMap[userLevel] ?: 1.0 // 현재 레벨의 최대 탐색 거리
        Log.d("PointSelectFragment", "사용자 레벨: $userLevel, 최대 허용 반경: $maxAllowedRadius km")

        val availableRadiusOptions = radiusValues.filter { it <= maxAllowedRadius }.map { "${it}km" }
        Log.d("PointSelectFragment", "스피너에 표시될 옵션: $availableRadiusOptions")

        val spinnerAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, availableRadiusOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.BLACK)
                return view
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        // 스피너가 비어있지 않다면 첫 번째 항목 선택
        if (availableRadiusOptions.isNotEmpty()) {
            spinner.setSelection(0)
        }
    }

    private fun loadVisitedPlacesThenUpdateLocation(radiusKm: Double) {
        val userId = auth.currentUser?.uid
        Log.d("PointSelectFragment", "현재 로그인된 사용자 ID: $userId")

        if (userId == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            updateUserLocationThenLoad(radiusKm)
            return
        }

        Firebase.firestore.collection("Users")
            .document(userId)
            .collection("visitedPlaces")
            .get()
            .addOnSuccessListener { querySnapshot ->
                visitedPlaceInfo.clear() // 🚀 수정: visitedPlaceInfo 클리어
                Log.d("PointSelectFragment", "visitedPlaces 쿼리 결과 문서 수: ${querySnapshot.documents.size}")

                if (querySnapshot.documents.isEmpty()) {
                    Log.d("PointSelectFragment", "방문 기록이 없습니다. (새 경로 쿼리 결과 0개)")
                    Log.d("PointSelectFragment", "쿼리 대상 userId: $userId")
                }

                for (document in querySnapshot.documents) {
                    val placeIdFromDoc = document.id
                    val userIdFromDoc = document.getString("userId")
                    val imageUrlFromDoc = document.getString("imageUrl") // 🚀 추가: imageUrl 필드 가져오기
                    val caloFromDoc = document.getDouble("calo") ?: 0.0 // 🚀 NEW: calo 데이터 가져오기
                    val distanceFromDoc = document.getDouble("distance") ?: 0.0 // 🚀 NEW: distance 데이터 가져오기
                    val stepNumFromDoc = document.getLong("stepNum") ?: 0L // 🚀 NEW: stepNum 데이터 가져오기
                    val docPath = document.reference.path

                    Log.d("PointSelectFragment", "처리 중인 문서: $docPath")
                    Log.d("PointSelectFragment", "  - 문서 ID (placeId): $placeIdFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 userId: $userIdFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 imageUrl: $imageUrlFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 calo: $caloFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 distance: $distanceFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 stepNum: $stepNumFromDoc")
                    Log.d("PointSelectFragment", "  - 현재 앱의 userId: $userId")

                    if (placeIdFromDoc != null && userIdFromDoc == userId) {
                        // 🚀 수정: imageUrl이 null이 아닌 경우에만 true로 저장 및 운동 데이터 함께 저장
                        visitedPlaceInfo[placeIdFromDoc] = VisitedPlaceDetails(
                            hasImageUrl = !imageUrlFromDoc.isNullOrEmpty(),
                            visitedImageUrl = imageUrlFromDoc, // 🚀 NEW: Store the actual image URL
                            calo = caloFromDoc,
                            distance = distanceFromDoc,
                            stepNum = stepNumFromDoc
                        )
                        Log.d("PointSelectFragment", "  -> 방문한 장소 ID 추가됨: $placeIdFromDoc (userId 일치, imageUrl 존재: ${!imageUrlFromDoc.isNullOrEmpty()})")
                    } else {
                        Log.d("PointSelectFragment", "  -> 문서 스킵됨:")
                        if (placeIdFromDoc == null) Log.d("PointSelectFragment", "    - placeId 없음 (문서 ID가 null)")
                        if (userIdFromDoc != userId) Log.d("PointSelectFragment", "    - userId 불일치: 문서 userId($userIdFromDoc) vs 현재 userId($userId)")
                    }
                }
                Log.d("PointSelectFragment", "최종 visitedPlaceInfo: $visitedPlaceInfo")
                updateUserLocationThenLoad(radiusKm)
            }
            .addOnFailureListener { e ->
                Log.e("PointSelectFragment", "방문한 장소 로드 오류: ${e.message}", e)
                Toast.makeText(context, "방문 기록 로드 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                updateUserLocationThenLoad(radiusKm)
            }
    }

    private fun updateUserLocationThenLoad(radiusKm: Double) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLat = location.latitude
                userLng = location.longitude
            }
            loadNearbyPoints(radiusKm)
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "위치 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadNearbyPoints(radiusKm: Double) {
        val currentUserId = auth.currentUser?.uid
        userAddedPlaceIds.clear() // 🚀 NEW: 장소 로드 전에 사용자 추가 장소 ID 목록 초기화

        Firebase.firestore.collection("Places").get().addOnSuccessListener { docs ->
            val userLocation = Location("user").apply {
                latitude = userLat
                longitude = userLng
            }

            placeList.clear()
            Log.d("PointSelectFragment", "주변 장소 로드 시작. 필터링 전 총 장소 수: ${docs.size()}")

            for (doc in docs) {
                val placeId = doc.id
                // 🚀 MODIFIED: 장소를 추가한 사용자 ID를 'addedBy' 필드에서 가져오도록 변경
                val placeCreatorId = doc.getString("addedBy")
                val placeName = doc.getString("name") ?: "알 수 없는 장소" // 🚀 NEW: 장소 이름 가져오기

                // 🚀 NEW: 현재 사용자가 추가한 장소인지 확인
                val isUserAdded = currentUserId != null && placeCreatorId == currentUserId
                if (isUserAdded) {
                    userAddedPlaceIds.add(placeId)
                    Log.d("PointSelectFragment", "사용자가 추가한 장소로 식별됨: $placeId (추가자 ID: $placeCreatorId, 현재 사용자 ID: $currentUserId)")
                }

                // 🚀 NEW: Check if the place has been visited and has an imageUrl, and get exercise data
                val visitedDetails = visitedPlaceInfo[placeId]
                val hasVisitedAndImageUrl = visitedDetails?.hasImageUrl ?: false
                val visitedImageUrl = visitedDetails?.visitedImageUrl // 🚀 NEW: Get visited image URL
                val calo = visitedDetails?.calo ?: 0.0
                val distance = visitedDetails?.distance ?: 0.0
                val stepNum = visitedDetails?.stepNum ?: 0L

                val geo = doc.getGeoPoint("geo") ?: continue
                val placeLocation = Location("place").apply {
                    latitude = geo.latitude
                    longitude = geo.longitude
                }

                if (userLocation.distanceTo(placeLocation) <= radiusKm * 1000) {
                    val imgUrl = doc.getString("myImgUrl") ?: continue
                    // 🚀 MODIFIED: PlaceData 생성 시 hasVisitedAndImageUrl, isUserAdded 상태와 운동 데이터를 함께 전달
                    // 🚀 MODIFIED: PlaceData에 placeName 추가
                    placeList.add(PlaceData(placeId, geo.latitude, geo.longitude, imgUrl, hasVisitedAndImageUrl, calo, distance, stepNum, visitedImageUrl, isUserAdded, placeName))
                    Log.d("PointSelectFragment", "추가된 장소: $placeId (이름: $placeName, 방문+이미지 여부: $hasVisitedAndImageUrl, 방문 이미지: $visitedImageUrl, 칼로리: $calo, 거리: $distance, 걸음수: $stepNum, 사용자 추가 여부: $isUserAdded)")
                } else {
                    Log.d("PointSelectFragment", "거리 초과로 스킵된 장소: $placeId (거리: ${userLocation.distanceTo(placeLocation)}m)")
                }
            }

            // 🚀 REMOVED: placeList.sortByDescending { it.isVisitedWithImage }
            // 이미 방문한 장소 정렬 로직은 제거되었습니다.

            adapter.notifyDataSetChanged()
            Log.d("PointSelectFragment", "필터링 후 최종 표시될 장소 수: ${placeList.size}")

            if (placeList.isEmpty()) {
                Toast.makeText(requireContext(), "주변에 탐색 가능한 장소가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🚀 NEW: 크루 장소를 로드하는 함수
    private fun loadCrewPlaces() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d("PointSelectFragment", "크루 탐색 시작. 현재 사용자 ID: $currentUserId")

        // 1. 모든 크루 문서를 가져옵니다.
        Firebase.firestore.collection("Crew")
            .get()
            .addOnSuccessListener { allCrewsSnapshot ->
                Log.d("PointSelectFragment", "모든 크루 쿼리 결과 문서 수: ${allCrewsSnapshot.documents.size}")

                if (allCrewsSnapshot.isEmpty) {
                    Toast.makeText(context, "등록된 크루가 없습니다.", Toast.LENGTH_SHORT).show()
                    placeList.clear()
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                var crewFoundAndProcessed = false // Flag to ensure we only process the first found crew

                // Collect all member check tasks
                val memberCheckTasks = mutableListOf<com.google.android.gms.tasks.Task<DocumentSnapshot>>()
                val crewIds = mutableListOf<String>() // To map tasks back to crew IDs

                for (crewDoc in allCrewsSnapshot.documents) {
                    val crewId = crewDoc.id
                    crewIds.add(crewId) // Store crewId at the same exact index as its task

                    // 🚀 수정: Firestore 경로를 'member' 서브컬렉션 안의 'members' 문서로 변경
                    val memberDocRef = Firebase.firestore.collection("Crew")
                        .document(crewId)
                        .collection("member") // 'member' 서브컬렉션
                        .document("members") // 'members' 문서

                    memberCheckTasks.add(memberDocRef.get())
                }

                // Wait for all member check tasks to complete
                Tasks.whenAll(memberCheckTasks)
                    .addOnCompleteListener { allTasksResult ->
                        if (allTasksResult.isSuccessful) {
                            for (i in memberCheckTasks.indices) {
                                val task = memberCheckTasks[i]
                                if (task.isSuccessful) {
                                    val memberDocSnapshot = task.result // task.result는 DocumentSnapshot 타입입니다.
                                    // 🚀 수정: 'members' 문서 내에 현재 사용자 UID가 필드로 존재하는지 확인
                                    if (memberDocSnapshot != null && memberDocSnapshot.exists() && memberDocSnapshot.contains(currentUserId)) {
                                        val crewId = crewIds[i] // Get the corresponding crewId
                                        if (!crewFoundAndProcessed) { // Process only the first found crew
                                            Log.d("PointSelectFragment", "크루 발견! ID: $crewId, 멤버 UID 필드 존재: $currentUserId")
                                            crewFoundAndProcessed = true
                                            fetchAndDisplayCrewPlaces(crewId, currentUserId)
                                        } else {
                                            Log.d("PointSelectFragment", "이미 크루를 찾았으므로 크루 $crewId 는 스킵합니다. (멤버 UID 필드 존재: ${memberDocSnapshot.id})")
                                        }
                                    } else {
                                        Log.d("PointSelectFragment", "크루 ${crewIds[i]} 에서 사용자 ($currentUserId)를 찾을 수 없습니다. (members 문서 없음 또는 필드 없음)")
                                    }
                                } else {
                                    Log.e("PointSelectFragment", "크루 ${crewIds[i]} 의 멤버 확인 오류: ${task.exception?.message}", task.exception)
                                }
                            }

                            // If after checking all crews, no crew was found and processed
                            if (!crewFoundAndProcessed) {
                                Toast.makeText(context, "속한 크루를 찾을 수 없습니다. Firestore 'Crew' 컬렉션의 'member' 서브컬렉션 내 'members' 문서 구조를 확인해주세요.", Toast.LENGTH_LONG).show()
                                placeList.clear()
                                adapter.notifyDataSetChanged()
                                Log.d("PointSelectFragment", "모든 크루 확인 결과: 속한 크루를 찾지 못했습니다.")
                            }
                        } else {
                            // At least one task failed in Tasks.whenAll
                            Log.e("PointSelectFragment", "모든 크루 멤버 확인 중 오류 발생: ${allTasksResult.exception?.message}", allTasksResult.exception)
                            Toast.makeText(context, "크루 멤버 확인 중 오류 발생: ${allTasksResult.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
                            placeList.clear()
                            adapter.notifyDataSetChanged()
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("PointSelectFragment", "크루 정보 로드 오류 (초기): ${e.message}", e)
                Toast.makeText(context, "크루 정보 로드 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                placeList.clear()
                adapter.notifyDataSetChanged()
            }
    }

    // 🚀 NEW: 크루 장소를 가져와 화면에 표시하는 함수
    private fun fetchAndDisplayCrewPlaces(crewId: String, currentUserId: String) {
        Firebase.firestore.collection("Crew")
            .document(crewId)
            .collection("crewPlace")
            .get()
            .addOnSuccessListener { crewPlaceQuerySnapshot ->
                val crewPlaceIds = mutableListOf<String>()
                for (doc in crewPlaceQuerySnapshot.documents) {
                    val placeId = doc.id
                    crewPlaceIds.add(placeId)
                }
                Log.d("PointSelectFragment", "크루 장소 ID 목록: $crewPlaceIds")

                if (crewPlaceIds.isEmpty()) {
                    Toast.makeText(context, "크루에 등록된 장소가 없습니다.", Toast.LENGTH_SHORT).show()
                    placeList.clear()
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                Firebase.firestore.collection("Places").get().addOnSuccessListener { allPlacesSnapshot ->
                    placeList.clear()
                    userAddedPlaceIds.clear()

                    val placesMap = allPlacesSnapshot.associateBy { it.id }

                    for (placeId in crewPlaceIds) {
                        val doc = placesMap[placeId]
                        if (doc != null) {
                            val placeCreatorId = doc.getString("addedBy")
                            val placeName = doc.getString("name") ?: "알 수 없는 장소"

                            val isUserAdded = currentUserId != null && placeCreatorId == currentUserId
                            if (isUserAdded) {
                                userAddedPlaceIds.add(placeId)
                                Log.d("PointSelectFragment", "사용자가 추가한 장소로 식별됨 (크루 탐색): $placeId")
                            }

                            val visitedDetails = visitedPlaceInfo[placeId]
                            val hasVisitedAndImageUrl = visitedDetails?.hasImageUrl ?: false
                            val visitedImageUrl = visitedDetails?.visitedImageUrl
                            val calo = visitedDetails?.calo ?: 0.0
                            val distance = visitedDetails?.distance ?: 0.0
                            val stepNum = visitedDetails?.stepNum ?: 0L

                            val geo = doc.getGeoPoint("geo") ?: continue
                            val imgUrl = doc.getString("myImgUrl") ?: continue

                            placeList.add(PlaceData(placeId, geo.latitude, geo.longitude, imgUrl, hasVisitedAndImageUrl, calo, distance, stepNum, visitedImageUrl, isUserAdded, placeName))
                            Log.d("PointSelectFragment", "크루 장소 추가됨: $placeId (이름: $placeName, 방문+이미지 여부: $hasVisitedAndImageUrl, 사용자 추가 여부: $isUserAdded)")
                        } else {
                            Log.d("PointSelectFragment", "Places 컬렉션에서 크루 장소 ID $placeId 에 해당하는 문서를 찾을 수 없습니다.")
                        }
                    }
                    adapter.notifyDataSetChanged()
                    Log.d("PointSelectFragment", "크루 탐색 후 최종 표시될 장소 수: ${placeList.size}")

                    if (placeList.isEmpty()) {
                        Toast.makeText(requireContext(), "크루에 탐색 가능한 장소가 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener { e ->
                    Log.e("PointSelectFragment", "모든 장소 로드 오류 (크루 탐색): ${e.message}", e)
                    Toast.makeText(context, "장소 정보 로드 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("PointSelectFragment", "크루 장소 로드 오류: ${e.message}", e)
                Toast.makeText(context, "크루 장소 로드 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }


    // 🚀 NEW: 방문한 장소의 상세 정보를 담을 데이터 클래스
    data class VisitedPlaceDetails(
        val hasImageUrl: Boolean = false,
        val visitedImageUrl: String? = null, // 🚀 NEW: Field to store the visited image URL
        val calo: Double = 0.0,
        val distance: Double = 0.0,
        val stepNum: Long = 0L
    )

    // 🚀 수정: isVisitedWithImage, calo, distance, stepNum, visitedImageUrl, isUserAdded 필드 추가
    // 🚀 NEW: placeName 필드 추가
    data class PlaceData(
        val placeId: String,
        val lat: Double,
        val lng: Double,
        val imageUrl: String, // This is the original image from the 'Places' collection
        val isVisitedWithImage: Boolean = false,
        val calo: Double = 0.0,
        val distance: Double = 0.0,
        val stepNum: Long = 0L,
        val visitedImageUrl: String? = null, // 🚀 NEW: Field for the image URL from visitedPlaces
        val isUserAdded: Boolean = false, // 🚀 NEW: 사용자가 추가한 장소인지 여부
        val placeName: String // 🚀 NEW: 장소 이름
    )

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
            val dialogVisitedImage = view.findViewById<ImageView>(R.id.dialogVisitedImage) // 🚀 NEW: Get the new ImageView
            val btnStart = view.findViewById<Button>(R.id.btnStartExplore)
            val btnCancel = view.findViewById<Button>(R.id.btnCancelExplore)
            val tvStatusMessage = view.findViewById<TextView>(R.id.tvStatusMessage) // 🚀 NEW: 상태 메시지 TextView
            val tvExerciseData = view.findViewById<TextView>(R.id.tvExerciseData) // 🚀 NEW: 운동 데이터 TextView
            val tvPlaceName = view.findViewById<TextView>(R.id.tvPlaceName) // 🚀 NEW: 장소 이름 TextView

            // 🚀 NEW: 장소 이름 설정
            tvPlaceName.text = place.placeName

            // Load the original place image
            Glide.with(view).load(place.imageUrl).into(imageView)

            // 🚀 NEW: 장소 상태에 따른 UI 로직 (우선순위: 사용자 추가 장소 > 방문 완료 장소 > 탐색 가능 장소)
            if (place.isUserAdded) {
                // 1. 사용자가 추가한 장소인 경우
                btnStart.isEnabled = false
                tvStatusMessage.visibility = View.VISIBLE
                tvStatusMessage.text = "자신이 추가한 장소입니다." // 사용자 요청 메시지
                tvExerciseData.visibility = View.GONE // 운동 데이터 숨김
                dialogVisitedImage.visibility = View.GONE // 방문 이미지 숨김
                Log.d("ExploreConfirmDialog", "사용자가 추가한 장소: ${place.placeId}")
            } else if (place.isVisitedWithImage) {
                // 2. 이미 탐색 완료된 장소인 경우 (사용자가 추가한 장소가 아닐 때만 해당)
                btnStart.isEnabled = false
                tvStatusMessage.visibility = View.VISIBLE
                tvStatusMessage.text = "이미 탐색 완료된 장소입니다." // 기존 메시지

                tvExerciseData.visibility = View.VISIBLE // 운동 데이터 표시
                tvExerciseData.text = "거리: ${String.format("%.2f", place.distance)} km | 걸음: ${place.stepNum} 걸음 | 칼로리: ${place.calo} kcal "

                if (!place.visitedImageUrl.isNullOrEmpty()) {
                    Glide.with(view).load(place.visitedImageUrl).into(dialogVisitedImage)
                    dialogVisitedImage.visibility = View.VISIBLE
                    Log.d("ExploreConfirmDialog", "방문 이미지 로드됨: ${place.visitedImageUrl}")
                } else {
                    dialogVisitedImage.visibility = View.GONE
                    Log.d("ExploreConfirmDialog", "방문 이미지 없음 또는 비어있음.")
                }
                Log.d("ExploreConfirmDialog", "탐색 완료된 장소: ${place.placeId}")
            } else {
                // 3. 탐색 가능한 장소인 경우
                btnStart.isEnabled = true
                tvStatusMessage.visibility = View.GONE // 메시지 숨김
                tvExerciseData.visibility = View.GONE // 운동 데이터 숨김
                dialogVisitedImage.visibility = View.GONE // 방문 이미지 숨김
                Log.d("ExploreConfirmDialog", "탐색 가능한 장소: ${place.placeId}")
            }

            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create()

            btnCancel.setOnClickListener {
                dismiss()
            }

            btnStart.setOnClickListener {
                if (btnStart.isEnabled) { // 🚀 추가: 버튼이 활성화된 경우에만 동작하도록 확인
                    // 🚀 MODIFIED: newInstance 호출 시 place.placeName 전달
                    val fragment = ExploreTrackingFragment.newInstance(place.placeId, place.lat, place.lng, place.imageUrl, place.placeName)
                    val activity = activity as? AppCompatActivity ?: return@setOnClickListener

                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerExplore, fragment)
                        .addToBackStack(null)
                        .commit()

                    parent.dismiss()
                    dismiss()
                } else {
                    // 비활성화된 버튼을 눌렀을 때의 토스트 메시지 (선택 사항)
                    if (place.isUserAdded) {
                        Toast.makeText(context, "자신이 추가한 장소는 탐색할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    } else if (place.isVisitedWithImage) {
                        Toast.makeText(context, "이미 탐색 완료된 장소입니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            return dialog
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
