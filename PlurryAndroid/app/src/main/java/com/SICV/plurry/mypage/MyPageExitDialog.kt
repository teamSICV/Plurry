package com.SICV.plurry.mypage

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import com.SICV.plurry.R
import com.SICV.plurry.login.LoginMainActivity
import com.SICV.plurry.ranking.RankingCrewTotal
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.StorageReference

class MyPageExitDialog(context: Context) : Dialog(context) {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var googleSignInClient: GoogleSignInClient
    private val crewTotalManager = RankingCrewTotal()
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "MyPageExitDialog"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mypage_exit_dialog)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Firebase 초기화
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance("gs://plurry-855a9.firebasestorage.app")

        // Google Sign-In 클라이언트 설정
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)

        setupButtons()
    }

    private fun setupButtons() {
        val backButton = findViewById<Button>(R.id.myPageExitBack)
        val okButton = findViewById<Button>(R.id.myPageExitOk)

        // 취소 버튼
        backButton.setOnClickListener {
            dismiss()
        }

        // 확인 버튼 - 회원 탈퇴 실행
        okButton.setOnClickListener {
            deleteUserAccount()
        }
    }

    private fun deleteUserAccount() {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(context, "로그인 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        val userId = currentUser.uid
        Log.w(TAG, "=== 회원탈퇴 시작 - UserID: $userId ===")

        scope.launch {
            var dataDeleteSuccess = false
            var authDeleteSuccess = false

            try {
                // 1. 먼저 모든 데이터를 삭제 (Authentication이 살아있는 상태에서)
                Toast.makeText(context, "사용자 데이터 삭제 중...", Toast.LENGTH_SHORT).show()
                safeDeleteUserData(userId)
                dataDeleteSuccess = true
                Log.w(TAG, "데이터 삭제 완료 - UserID: $userId")

            } catch (dataException: Exception) {
                Log.e(TAG, "데이터 삭제 중 오류: ${dataException.message}", dataException)
                // 데이터 삭제 실패해도 계속 진행
                dataDeleteSuccess = false
            }

            try {
                // 2. 마지막에 Authentication 계정 삭제
                Toast.makeText(context, "계정 삭제 중...", Toast.LENGTH_SHORT).show()
                deleteAuthenticationAccount()
                authDeleteSuccess = true
                Log.w(TAG, "Authentication 삭제 완료 - UserID: $userId")

            } catch (authException: Exception) {
                Log.e(TAG, "Authentication 삭제 실패: ${authException.message}", authException)
                authDeleteSuccess = false

                // 재인증 필요 메시지 확인
                if (authException.message?.contains("recent") == true ||
                    authException.message?.contains("login") == true) {
                    Toast.makeText(context,
                        "계정 삭제를 위해 재로그인이 필요합니다.\n로그아웃 후 다시 로그인하여 탈퇴를 시도해주세요.",
                        Toast.LENGTH_LONG).show()
                }
            }

            // 3. 결과에 따른 처리
            when {
                dataDeleteSuccess && authDeleteSuccess -> {
                    Log.w(TAG, "=== 회원탈퇴 완전 성공 - UserID: $userId ===")
                    Toast.makeText(context, "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                    navigateToLogin()
                }
                dataDeleteSuccess && !authDeleteSuccess -> {
                    Log.w(TAG, "=== 데이터 삭제 완료, Authentication 삭제 실패 - UserID: $userId ===")
                    Toast.makeText(context,
                        "데이터는 삭제되었으나 계정 삭제가 실패했습니다.\n로그아웃 후 재로그인하여 다시 시도해주세요.",
                        Toast.LENGTH_LONG).show()
                }
                !dataDeleteSuccess && authDeleteSuccess -> {
                    Log.w(TAG, "=== 계정 삭제 완료, 데이터 삭제 실패 - UserID: $userId ===")
                    Toast.makeText(context, "계정 삭제는 완료되었습니다.", Toast.LENGTH_SHORT).show()
                    navigateToLogin()
                }
                else -> {
                    Log.w(TAG, "=== 회원탈퇴 실패 - UserID: $userId ===")
                    Toast.makeText(context,
                        "회원탈퇴에 실패했습니다.\n네트워크 상태를 확인하고 다시 시도해주세요.",
                        Toast.LENGTH_LONG).show()
                }
            }

            dismiss()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(context, LoginMainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }

    private suspend fun safeDeleteUserData(userId: String) {
        val deleteTasks = mapOf<String, suspend () -> Unit>(
            "crew 관련 데이터" to { handleCrewRelatedData(userId) },
            "goWalk 데이터" to { deleteUserGoWalkData(userId) },
            "visitedPlaces 데이터" to { deleteVisitedPlacesData(userId) },
            "사용자 생성 Places" to { deleteUserCreatedPlaces(userId) },
            "프로필 이미지" to { deleteProfileImage(userId) },
            "userReward 데이터" to { deleteUserRewardData(userId) },
            "사용자 메인 문서" to { deleteUserMainDocument(userId) },
            "사용자 Storage 폴더" to { deleteUserStorageFolder(userId) }
        )

        var successCount = 0
        val totalTasks = deleteTasks.size

        deleteTasks.forEach { (taskName, deleteTask) ->
            try {
                deleteTask()
                successCount++
                Log.d(TAG, "$taskName 삭제 성공 ($successCount/$totalTasks)")
            } catch (e: Exception) {
                Log.e(TAG, "$taskName 삭제 실패: ${e.message}", e)
            }
        }

        Log.w(TAG, "데이터 삭제 완료: $successCount/$totalTasks 성공")

        if (successCount == 0) {
            throw Exception("모든 데이터 삭제 작업이 실패했습니다.")
        }
    }

    private suspend fun deleteAuthenticationAccount() {
        try {
            val currentUser = auth.currentUser ?: throw Exception("사용자 정보를 찾을 수 없습니다.")

            // 1. Google Sign Out
            try {
                googleSignInClient.signOut().await()
                Log.d(TAG, "Google 로그아웃 성공")
                delay(500)
            } catch (googleSignOutException: Exception) {
                Log.w(TAG, "Google 로그아웃 실패 (무시하고 계속): ${googleSignOutException.message}")
            }

            // 2. Firebase Authentication 계정 삭제
            currentUser.delete().await()
            Log.d(TAG, "Firebase Authentication 계정 삭제 성공")

        } catch (e: Exception) {
            Log.e(TAG, "Authentication 계정 삭제 실패: ${e.message}", e)
            throw e
        }
    }

    private suspend fun handleCrewRelatedData(userId: String) {
        try {
            val userDoc = firestore.collection("Users")
                .document(userId)
                .get()
                .await()

            val crewId = userDoc.getString("crewAt")

            if (crewId != null) {
                Log.d(TAG, "사용자가 크루에 속해있음: $crewId")

                // 사용자가 생성한 Places의 crewPlace 삭제
                deleteUserCreatedPlacesFromCrewPlace(userId, crewId)

                // 크루 리더인지 확인
                val leaderDoc = firestore.collection("Crew")
                    .document(crewId)
                    .collection("member")
                    .document("leader")
                    .get()
                    .await()

                val leaderId = leaderDoc.getString("leader")
                val isLeader = leaderId == userId

                // crewReward에서 아이템 제거
                removeCrewRewardItems(userId, crewId)

                if (isLeader) {
                    Log.d(TAG, "크루 리더로서 크루 전체 삭제")
                    handleLeaderCrewDeletion(crewId)
                } else {
                    Log.d(TAG, "크루 멤버로서 크루 탈퇴")
                    handleMemberCrewExit(userId, crewId)
                }
            } else {
                Log.d(TAG, "사용자가 크루에 속하지 않음")
            }
        } catch (e: Exception) {
            Log.e(TAG, "crew 관련 데이터 처리 중 오류", e)
            throw e
        }
    }

    private suspend fun deleteUserCreatedPlacesFromCrewPlace(userId: String, crewId: String) {
        try {
            // 사용자가 생성한 모든 Places 조회
            val userCreatedPlacesQuery = firestore.collection("Places")
                .whereEqualTo("addedBy", userId)
                .get()
                .await()

            Log.d(TAG, "사용자가 생성한 장소 수: ${userCreatedPlacesQuery.documents.size}")

            // 각 장소에 대해 crewPlace에서 삭제
            userCreatedPlacesQuery.documents.forEach { placeDoc ->
                val placeId = placeDoc.id

                try {
                    val crewPlaceRef = firestore.collection("Crew")
                        .document(crewId)
                        .collection("crewPlace")
                        .document(placeId)

                    val crewPlaceDoc = crewPlaceRef.get().await()
                    if (crewPlaceDoc.exists()) {
                        crewPlaceRef.delete().await()
                        Log.d(TAG, "crewPlace 삭제 성공: $placeId from $crewId")
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "crewPlace 삭제 실패: $placeId from $crewId", e)
                }
            }

            // 다른 모든 크루에서도 삭제
            userCreatedPlacesQuery.documents.forEach { placeDoc ->
                val placeId = placeDoc.id

                try {
                    val allCrewsQuery = firestore.collection("Crew").get().await()

                    allCrewsQuery.documents.forEach { crewDoc ->
                        if (crewDoc.id != crewId) {
                            try {
                                val otherCrewPlaceRef = firestore.collection("Crew")
                                    .document(crewDoc.id)
                                    .collection("crewPlace")
                                    .document(placeId)

                                val otherCrewPlaceDoc = otherCrewPlaceRef.get().await()
                                if (otherCrewPlaceDoc.exists()) {
                                    otherCrewPlaceRef.delete().await()
                                    Log.d(TAG, "다른 크루의 crewPlace 삭제: $placeId from ${crewDoc.id}")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "다른 크루 crewPlace 삭제 실패: $placeId from ${crewDoc.id}", e)
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "모든 크루 조회 실패 for placeId: $placeId", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "사용자 생성 장소의 crewPlace 삭제 중 오류", e)
            throw e
        }
    }

    private suspend fun deleteUserGoWalkData(userId: String) {
        try {
            val goWalkRef = firestore.collection("Users")
                .document(userId)
                .collection("goWalk")

            val documents = goWalkRef.get().await()
            Log.d(TAG, "goWalk 문서 수: ${documents.documents.size}")

            documents.documents.forEach { document ->
                document.reference.delete().await()
                Log.d(TAG, "goWalk 문서 삭제: ${document.id}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "goWalk 데이터 삭제 중 오류", e)
            throw e
        }
    }

    private suspend fun deleteVisitedPlacesData(userId: String) {
        try {
            val visitedPlacesRef = firestore.collection("Users")
                .document(userId)
                .collection("visitedPlaces")

            val documents = visitedPlacesRef.get().await()
            Log.d(TAG, "visitedPlaces 문서 수: ${documents.documents.size}")

            documents.documents.forEach { document ->
                val placeId = document.id

                // Places 이미지 삭제
                try {
                    val placeDoc = firestore.collection("Places")
                        .document(placeId)
                        .get()
                        .await()

                    val myImgUrl = placeDoc.getString("myImgUrl")
                    if (!myImgUrl.isNullOrEmpty()) {
                        val imageRef = storage.getReferenceFromUrl(myImgUrl)
                        imageRef.delete().await()
                        Log.d(TAG, "장소 이미지 삭제: $myImgUrl")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "장소 이미지 삭제 실패 for placeId: $placeId", e)
                }

                // visitedPlaces 문서 삭제
                document.reference.delete().await()
                Log.d(TAG, "visitedPlace 삭제: $placeId")
            }

        } catch (e: Exception) {
            Log.e(TAG, "visitedPlaces 데이터 삭제 중 오류", e)
            throw e
        }
    }

    private suspend fun deleteUserCreatedPlaces(userId: String) {
        try {
            val placesQuery = firestore.collection("Places")
                .whereEqualTo("addedBy", userId)
                .get()
                .await()

            Log.d(TAG, "사용자가 생성한 Places 수: ${placesQuery.documents.size}")

            placesQuery.documents.forEach { placeDoc ->
                val placeId = placeDoc.id

                try {
                    // Place 이미지 삭제
                    val myImgUrl = placeDoc.getString("myImgUrl")
                    if (!myImgUrl.isNullOrEmpty()) {
                        val imageRef = storage.getReferenceFromUrl(myImgUrl)
                        imageRef.delete().await()
                        Log.d(TAG, "Place 이미지 삭제: $myImgUrl for $placeId")
                    }

                    // Place 문서 삭제
                    placeDoc.reference.delete().await()
                    Log.d(TAG, "사용자 생성 Place 삭제: $placeId")

                } catch (e: Exception) {
                    Log.w(TAG, "Place 삭제 실패: $placeId", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "사용자 생성 Places 삭제 중 오류", e)
            throw e
        }
    }

    private suspend fun deleteProfileImage(userId: String) {
        try {
            val userDoc = firestore.collection("Users")
                .document(userId)
                .get()
                .await()

            val profileImgUrl = userDoc.getString("profileImg")
            if (!profileImgUrl.isNullOrEmpty()) {
                val profileImageRef = storage.getReferenceFromUrl(profileImgUrl)
                profileImageRef.delete().await()
                Log.d(TAG, "프로필 이미지 삭제: $profileImgUrl")
            } else {
                Log.d(TAG, "프로필 이미지 없음")
            }
        } catch (e: Exception) {
            Log.w(TAG, "프로필 이미지 삭제 실패", e)
        }
    }

    private suspend fun removeCrewRewardItems(userId: String, crewId: String) {
        try {
            // 사용자의 crewRewardItem 가져오기
            val userRewardDoc = firestore.collection("Game")
                .document("users")
                .collection("userReward")
                .document(userId)
                .get()
                .await()

            val crewRewardItems = userRewardDoc.get("crewRewardItem") as? List<String>

            if (!crewRewardItems.isNullOrEmpty()) {
                val crewRewardRef = firestore.collection("Game")
                    .document("crew")
                    .collection("crewReward")
                    .document(crewId)

                val crewRewardDoc = crewRewardRef.get().await()
                if (crewRewardDoc.exists()) {
                    val currentItems = crewRewardDoc.get("crewRewardItem") as? MutableList<String> ?: mutableListOf()

                    // 사용자의 아이템들 제거
                    crewRewardItems.forEach { item ->
                        currentItems.remove(item)
                    }

                    // 업데이트
                    crewRewardRef.update(mapOf(
                        "crewRewardItem" to currentItems,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )).await()

                    Log.d(TAG, "크루 리워드 아이템 제거 완료 for user: $userId")
                }
            } else {
                Log.d(TAG, "제거할 크루 리워드 아이템 없음")
            }
        } catch (e: Exception) {
            Log.w(TAG, "크루 리워드 아이템 제거 실패", e)
        }
    }

    private suspend fun handleLeaderCrewDeletion(crewId: String) {
        try {
            // 모든 멤버의 crewAt 필드 삭제 (크루 삭제 전에 먼저)
            clearMembersCrewAt(crewId)

            // 크루 문서 삭제
            firestore.collection("Crew")
                .document(crewId)
                .delete()
                .await()

            // crewPlace 문서들 삭제
            deleteCrewPlaceDocuments(crewId)

            // crewReward 문서 삭제
            deleteCrewRewardDocument(crewId)

            // 크루 스토리지 삭제
            deleteCrewStorage(crewId)

            Log.d(TAG, "리더로서 크루 완전 삭제 완료: $crewId")
        } catch (e: Exception) {
            Log.e(TAG, "리더 크루 삭제 중 오류", e)
            throw e
        }
    }

    private suspend fun handleMemberCrewExit(userId: String, crewId: String) {
        try {
            // members 문서에서 해당 사용자 필드 삭제
            firestore.collection("Crew")
                .document(crewId)
                .collection("member")
                .document("members")
                .update(mapOf(userId to FieldValue.delete()))
                .await()

            // 크루 점수 재계산
            delay(500)
            crewTotalManager.manualRecalculateCrewScore(crewId)

            Log.d(TAG, "멤버로서 크루 탈퇴 완료: $userId from $crewId")
        } catch (e: Exception) {
            Log.e(TAG, "멤버 크루 탈퇴 중 오류", e)
            throw e
        }
    }

    private suspend fun clearMembersCrewAt(crewId: String) {
        try {
            // members 문서에서 모든 멤버 가져오기
            val membersDoc = firestore.collection("Crew")
                .document(crewId)
                .collection("member")
                .document("members")
                .get()
                .await()

            if (membersDoc.exists()) {
                val membersData = membersDoc.data
                Log.d(TAG, "크루 멤버 수: ${membersData?.keys?.size}")

                membersData?.keys?.forEach { memberUid ->
                    try {
                        firestore.collection("Users")
                            .document(memberUid)
                            .update(mapOf(
                                "crewAt" to FieldValue.delete(),
                                "crewAtTime" to FieldValue.delete()
                            ))
                            .await()
                        Log.d(TAG, "멤버 crewAt 정리: $memberUid")
                    } catch (e: Exception) {
                        Log.w(TAG, "멤버 crewAt 정리 실패 $memberUid", e)
                    }
                }

                membersDoc.reference.delete().await()
            }

            // leader 문서에서 리더 정보 가져오기
            val leaderDoc = firestore.collection("Crew")
                .document(crewId)
                .collection("member")
                .document("leader")
                .get()
                .await()

            if (leaderDoc.exists()) {
                val leaderField = leaderDoc.getString("leader")

                if (leaderField != null) {
                    try {
                        firestore.collection("Users")
                            .document(leaderField)
                            .update(mapOf(
                                "crewAt" to FieldValue.delete(),
                                "crewAtTime" to FieldValue.delete()
                            ))
                            .await()
                        Log.d(TAG, "리더 crewAt 정리: $leaderField")
                    } catch (e: Exception) {
                        Log.w(TAG, "리더 crewAt 정리 실패 $leaderField", e)
                    }
                }

                leaderDoc.reference.delete().await()
            }

        } catch (e: Exception) {
            Log.e(TAG, "멤버들 crewAt 정리 중 오류", e)
        }
    }

    private suspend fun deleteCrewPlaceDocuments(crewId: String) {
        try {
            val crewPlaceRef = firestore.collection("Crew")
                .document(crewId)
                .collection("crewPlace")

            val documents = crewPlaceRef.get().await()
            Log.d(TAG, "crewPlace 문서 수: ${documents.documents.size}")

            documents.documents.forEach { document ->
                document.reference.delete().await()
                Log.d(TAG, "crewPlace 문서 삭제: ${document.id}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "crewPlace 문서 삭제 중 오류", e)
        }
    }

    private suspend fun deleteCrewRewardDocument(crewId: String) {
        try {
            firestore.collection("Game")
                .document("crew")
                .collection("crewReward")
                .document(crewId)
                .delete()
                .await()

            Log.d(TAG, "crewReward 문서 삭제 완료: $crewId")
        } catch (e: Exception) {
            Log.w(TAG, "crewReward 문서 삭제 중 오류", e)
        }
    }

    private suspend fun deleteCrewStorage(crewId: String) {
        try {
            val crewRef = storage.reference.child("Crew").child(crewId)
            deleteStorageFolder(crewRef)
            Log.d(TAG, "크루 Storage 폴더 삭제: Crew/$crewId")
        } catch (e: Exception) {
            Log.w(TAG, "크루 Storage 삭제 실패: Crew/$crewId", e)
        }
    }

    private suspend fun deleteUserRewardData(userId: String) {
        try {
            firestore.collection("Game")
                .document("users")
                .collection("userReward")
                .document(userId)
                .delete()
                .await()

            Log.d(TAG, "userReward 문서 삭제 완료: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "userReward 데이터 삭제 중 오류", e)
            throw e
        }
    }

    private suspend fun deleteUserMainDocument(userId: String) {
        try {
            firestore.collection("Users")
                .document(userId)
                .delete()
                .await()

            Log.d(TAG, "사용자 메인 문서 삭제 완료: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "사용자 메인 문서 삭제 중 오류", e)
            throw e
        }
    }

    private suspend fun deleteUserStorageFolder(userId: String) {
        try {
            val userRef = storage.reference.child(userId)
            deleteStorageFolder(userRef)
            Log.d(TAG, "사용자 Storage 폴더 삭제: $userId")
        } catch (e: Exception) {
            Log.w(TAG, "사용자 Storage 삭제 실패: $userId", e)
        }
    }

    private suspend fun deleteStorageFolder(folderRef: StorageReference) {
        try {
            val result = folderRef.listAll().await()

            // 파일들 삭제
            result.items.forEach { file ->
                file.delete().await()
            }

            // 하위 폴더들 삭제
            result.prefixes.forEach { subfolder ->
                deleteStorageFolder(subfolder)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Storage 폴더 삭제 실패: ${folderRef.path}", e)
        }
    }
}