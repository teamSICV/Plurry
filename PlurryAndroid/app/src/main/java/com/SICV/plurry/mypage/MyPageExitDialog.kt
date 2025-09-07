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

        scope.launch {
            try {
                // crew place 처리
                handleCrewRelatedData(userId)

                // goWalk 모든 문서 삭제
                deleteUserGoWalkData(userId)

                // visitedPlaces 데이터 및 관련 이미지 삭제
                deleteVisitedPlacesData(userId)

                // 사용자가 생성한 Places 삭제
                deleteUserCreatedPlaces(userId)

                // 프로필 이미지 삭제
                deleteProfileImage(userId)

                // userReward 문서 삭제
                deleteUserRewardData(userId)

                // Users/{uid} 메인 문서 삭제
                deleteUserMainDocument(userId)

                // Storage에서 사용자 폴더 삭제
                deleteUserStorageFolder(userId)

                // Authentication에서 사용자 계정 삭제
                deleteAuthenticationAccount()

            } catch (e: Exception) {
                Log.e(TAG, "사용자 데이터 삭제 중 오류 발생: ${e.message}", e)
                Toast.makeText(context, "회원탈퇴 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                dismiss()
            }
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
                    // 리더인 경우 크루 완전 삭제
                    handleLeaderCrewDeletion(crewId)
                } else {
                    // 일반 멤버인 경우 멤버에서만 제거
                    handleMemberCrewExit(userId, crewId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling crew related data", e)
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

            Log.d(TAG, "Found ${userCreatedPlacesQuery.documents.size} places created by user: $userId")

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
                        Log.d(TAG, "Successfully deleted crewPlace: $placeId from crew: $crewId")
                    } else {
                        Log.d(TAG, "CrewPlace document does not exist: $placeId in crew: $crewId")
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete crewPlace: $placeId from crew: $crewId", e)
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
                                    Log.d(TAG, "Deleted crewPlace: $placeId from other crew: ${crewDoc.id}")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to delete crewPlace: $placeId from crew: ${crewDoc.id}", e)
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query all crews for placeId: $placeId", e)
                }
            }

            Log.d(TAG, "Completed deleteUserCreatedPlacesFromCrewPlace for user: $userId")

        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteUserCreatedPlacesFromCrewPlace", e)
            throw e
        }
    }

    private suspend fun deleteUserGoWalkData(userId: String) {
        try {
            val goWalkRef = firestore.collection("Users")
                .document(userId)
                .collection("goWalk")

            val documents = goWalkRef.get().await()

            documents.documents.forEach { document ->
                document.reference.delete().await()
                Log.d(TAG, "Deleted goWalk document: ${document.id}")
            }

            Log.d(TAG, "Successfully deleted all goWalk data for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting goWalk data", e)
            throw e
        }
    }

    private suspend fun deleteVisitedPlacesData(userId: String) {
        try {
            val visitedPlacesRef = firestore.collection("Users")
                .document(userId)
                .collection("visitedPlaces")

            val documents = visitedPlacesRef.get().await()

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
                        Log.d(TAG, "Deleted place image: $myImgUrl")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete place image for placeId: $placeId", e)
                }

                // visitedPlaces 문서 삭제
                document.reference.delete().await()
                Log.d(TAG, "Deleted visitedPlace: $placeId")
            }

            Log.d(TAG, "Successfully deleted all visitedPlaces data")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting visitedPlaces data", e)
            throw e
        }
    }

    private suspend fun deleteUserCreatedPlaces(userId: String) {
        try {
            val placesQuery = firestore.collection("Places")
                .whereEqualTo("addedBy", userId)
                .get()
                .await()

            placesQuery.documents.forEach { placeDoc ->
                val placeId = placeDoc.id

                try {
                    // Place 이미지 삭제
                    val myImgUrl = placeDoc.getString("myImgUrl")
                    if (!myImgUrl.isNullOrEmpty()) {
                        val imageRef = storage.getReferenceFromUrl(myImgUrl)
                        imageRef.delete().await()
                        Log.d(TAG, "Deleted place image: $myImgUrl for placeId: $placeId")
                    }

                    // Place 문서 삭제
                    placeDoc.reference.delete().await()
                    Log.d(TAG, "Deleted user created place: $placeId")

                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete place: $placeId", e)
                }
            }

            Log.d(TAG, "Successfully deleted all user created places for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user created places", e)
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
                Log.d(TAG, "Deleted profile image: $profileImgUrl")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete profile image", e)
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

                    Log.d(TAG, "Removed crew reward items for user: $userId")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove crew reward items", e)
        }
    }

    private suspend fun handleLeaderCrewDeletion(crewId: String) {
        try {
            // 크루 문서 삭제
            firestore.collection("Crew")
                .document(crewId)
                .delete()
                .await()

            // 모든 멤버의 crewAt 필드 삭제
            clearMembersCrewAt(crewId)

            // crewPlace 문서들 삭제
            deleteCrewPlaceDocuments(crewId)

            // crewReward 문서 삭제
            deleteCrewRewardDocument(crewId)

            // 크루 스토리지 삭제
            deleteCrewStorage(crewId)

            Log.d(TAG, "Successfully deleted crew as leader: $crewId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting crew as leader", e)
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

            Log.d(TAG, "Successfully exited crew as member: $userId from $crewId")
        } catch (e: Exception) {
            Log.e(TAG, "Error exiting crew as member", e)
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
                membersData?.keys?.forEach { memberUid ->
                    try {
                        firestore.collection("Users")
                            .document(memberUid)
                            .update(mapOf(
                                "crewAt" to FieldValue.delete(),
                                "crewAtTime" to FieldValue.delete()
                            ))
                            .await()
                        Log.d(TAG, "Cleared crewAt for member: $memberUid")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear crewAt for member $memberUid", e)
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
                        Log.d(TAG, "Cleared crewAt for leader: $leaderField")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear crewAt for leader $leaderField", e)
                    }
                }

                leaderDoc.reference.delete().await()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing members crewAt", e)
        }
    }

    private suspend fun deleteCrewPlaceDocuments(crewId: String) {
        try {
            val crewPlaceRef = firestore.collection("Crew")
                .document(crewId)
                .collection("crewPlace")

            val documents = crewPlaceRef.get().await()

            documents.documents.forEach { document ->
                document.reference.delete().await()
                Log.d(TAG, "Deleted crewPlace document: ${document.id}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting crewPlace documents", e)
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

            Log.d(TAG, "Successfully deleted crewReward document for crew: $crewId")
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting crewReward document", e)
        }
    }

    private suspend fun deleteCrewStorage(crewId: String) {
        try {
            val crewRef = storage.reference.child("Crew").child(crewId)
            deleteStorageFolder(crewRef)
            Log.d(TAG, "Deleted storage folder: Crew/$crewId")
        } catch (e: Exception) {
            Log.w(TAG, "Storage deletion failed for Crew/$crewId", e)
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

            Log.d(TAG, "Successfully deleted userReward document for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting userReward data", e)
            throw e
        }
    }

    private suspend fun deleteUserMainDocument(userId: String) {
        try {
            firestore.collection("Users")
                .document(userId)
                .delete()
                .await()

            Log.d(TAG, "Successfully deleted Users main document for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Users main document", e)
            throw e
        }
    }

    private suspend fun deleteUserStorageFolder(userId: String) {
        try {
            val userRef = storage.reference.child(userId)
            deleteStorageFolder(userRef)
            Log.d(TAG, "Deleted user storage folder: $userId")
        } catch (e: Exception) {
            Log.w(TAG, "User storage deletion failed for $userId", e)
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
            Log.w(TAG, "Failed to delete storage folder: ${folderRef.path}", e)
        }
    }

    private suspend fun deleteAuthenticationAccount() {
        try {
            val currentUser = auth.currentUser

            // Google 로그아웃을 먼저 실행
            googleSignInClient.signOut().await()

            currentUser?.delete()?.await()

            Log.d(TAG, "사용자 계정 삭제 성공")

            Toast.makeText(context, "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()

            // 로그인 화면으로 이동
            val intent = Intent(context, LoginMainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)

            dismiss()

        } catch (e: Exception) {
            Log.e(TAG, "사용자 계정 삭제 실패: ${e.message}", e)
            Toast.makeText(context, "회원탈퇴에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }
}