package com.SICV.plurry.crewstep

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.SICV.plurry.R
import com.SICV.plurry.crewstep.CrewLineMainActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.*

class CrewLineMakeCrewActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var editTextCrewName: EditText
    private lateinit var findLocationButton: Button
    private lateinit var makeCrewButton: Button

    private var imageUri: Uri? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var currentDistrict: String? = null

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crew_line_make_crew)

        imageView = findViewById(R.id.crewProfile)
        editTextCrewName = findViewById(R.id.editCrewName)
        findLocationButton = findViewById(R.id.findLocation)
        makeCrewButton = findViewById(R.id.btnMakeCrew)

        val crewMakeBackBtn = findViewById<ImageView>(R.id.crewMakeBackBtn)

        imageView.setOnClickListener {
            openImageChooser()
        }

        crewMakeBackBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findLocationButton.setOnClickListener {
            getCurrentDistrict { district ->
                currentDistrict = district
                Toast.makeText(this, "위치: $district", Toast.LENGTH_SHORT).show()
            }
        }

        makeCrewButton.setOnClickListener {
            if (imageUri == null) {
                AlertDialog.Builder(this)
                    .setTitle("기본 이미지 사용")
                    .setMessage("기본 이미지를 사용하시겠습니까?")
                    .setPositiveButton("네") { dialog, _ ->
                        dialog.dismiss()
                        createCrew(useDefaultImage = true)
                    }
                    .setNegativeButton("아니오") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this, "이미지를 선택해주세요.", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            } else {
                createCrew(useDefaultImage = false)
            }
        }
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            imageView.setImageURI(imageUri)
        }
    }

    private fun createCrew(useDefaultImage: Boolean) {
        val crewName = editTextCrewName.text.toString().trim()
        val uid = auth.currentUser?.uid ?: return

        if (crewName.isEmpty()) {
            Toast.makeText(this, "크루명을 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }

        val crewDocRef = firestore.collection("Crew").document()
        val crewId = crewDocRef.id

        val createdTime = Timestamp.now()
        val crewData = hashMapOf(
            "name" to crewName,
            "createdTime" to createdTime,
            "mainField" to currentDistrict.orEmpty()
        )

        crewDocRef.set(crewData).addOnSuccessListener {
            saveMemberData(crewDocRef, uid, createdTime)
            updateCreatorCrewInfo(uid, crewId, createdTime)
            addCreatorPlacesToCrew(crewId, uid)

            CrewGameManager.initializeCrewGameData(
                crewId = crewId,
                onSuccess = {
                    Log.d("CrewLineMake", "크루 게임 데이터 초기화 성공")

                    if (useDefaultImage) {
                        uploadDefaultImage(crewId) {
                        }
                    } else {
                        uploadCrewImage(crewId) {
                        }
                    }

                    Toast.makeText(this, "크루 생성 완료", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, CrewLineMainActivity::class.java)
                    intent.putExtra("crewId", crewId)
                    startActivity(intent)
                    finish()
                },
                onFailure = { e ->
                    Log.e("CrewLineMake", "크루 게임 데이터 초기화 실패", e)

                    crewDocRef.delete()
                        .addOnSuccessListener {
                            Log.d("CrewLineMake", "크루 문서 삭제 완료")
                        }
                        .addOnFailureListener { deleteError ->
                            Log.e("CrewLineMake", "크루 문서 삭제 실패", deleteError)
                        }

                    Toast.makeText(this, "크루 생성 실패: 게임 데이터 초기화 오류", Toast.LENGTH_SHORT).show()
                }
            )

        }.addOnFailureListener {
            Toast.makeText(this, "크루 생성 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveMemberData(crewDocRef: com.google.firebase.firestore.DocumentReference, uid: String, createdTime: Timestamp) {
        val memberCollection = crewDocRef.collection("member")
        memberCollection.document("leader").set(mapOf("leader" to uid))
        memberCollection.document("members").set(mapOf(uid to createdTime))
    }

    private fun updateCreatorCrewInfo(uid: String, crewId: String, createdTime: Timestamp) {
        val updates = hashMapOf<String, Any>(
            "crewAt" to crewId,
            "crewAtTime" to createdTime
        )
        firestore.collection("Users").document(uid).update(updates)
            .addOnSuccessListener {
                Log.d("CrewLineMake", "크루장 crewAt 필드 업데이트 완료")
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMake", "크루장 crewAt 필드 업데이트 실패", e)
            }
    }

    private fun addCreatorPlacesToCrew(crewId: String, uid: String) {
        firestore.collection("Places")
            .whereEqualTo("addedBy", uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = firestore.batch()

                for (document in querySnapshot.documents) {
                    val placeId = document.id
                    val crewPlaceRef = firestore.collection("Crew").document(crewId)
                        .collection("crewPlace").document(placeId)
                    batch.set(crewPlaceRef, mapOf(placeId to true))
                }

                batch.commit()
                    .addOnSuccessListener {
                        Log.d("CrewLineMake", "크루장의 장소들이 크루에 추가되었습니다.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("CrewLineMake", "크루장 장소 추가 실패", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CrewLineMake", "크루장 장소 조회 실패", e)
            }
    }

    private fun uploadCrewImage(crewId: String, onComplete: () -> Unit) {
        val storageRef = storage.reference.child("Crew/$crewId/CrewProfile")

        if (imageUri != null) {
            storageRef.putFile(imageUri!!)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                        firestore.collection("Crew").document(crewId)
                            .update("CrewProfile", uri.toString())
                            .addOnSuccessListener {
                                onComplete()
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
        } else {
            onComplete()
        }
    }

    private fun uploadDefaultImage(crewId: String, onComplete: () -> Unit) {
        val storageRef = storage.reference.child("Crew/$crewId/CrewProfile")

        val defaultDrawable = ContextCompat.getDrawable(this, R.drawable.basiccrewprofile) as BitmapDrawable
        val bitmap = defaultDrawable.bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val data = baos.toByteArray()

        storageRef.putBytes(data)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    firestore.collection("Crew").document(crewId)
                        .update("CrewProfile", uri.toString())
                        .addOnSuccessListener {
                            onComplete()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "기본 이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                onComplete()
            }
    }

    private fun getCurrentDistrict(callback: (String) -> Unit) {
        val fusedLocationProviderClient = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(this)

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        fusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = android.location.Geocoder(this, Locale.KOREA)
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val district = addresses[0].subLocality ?: "알 수 없음"
                        callback(district)
                    } else {
                        callback("알 수 없음")
                    }
                } else {
                    callback("알 수 없음")
                }
            }
            .addOnFailureListener {
                callback("알 수 없음")
            }
    }
}