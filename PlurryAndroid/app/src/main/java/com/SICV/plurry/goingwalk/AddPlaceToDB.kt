package com.SICV.plurry.goingwalk

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class AddPlaceToDB {
    fun uploadPlaceInfo(
        context: Context,
        placeName: String,
        myImageUri: Uri,
        baseImageUri: Uri,
        latitude: Double,
        longitude: Double,
        distance: Double,
        steps: Int,
        calories: Int,
        username: String
        ){
        val db = FirebaseFirestore.getInstance()
        val storageRef = FirebaseStorage.getInstance().reference

        db.collection("Places")
            .get()
            .addOnSuccessListener { documents ->
                val nextId = "place" + String.format("%03d", documents.size()+1)
                val myImagePath = "$username/myImg.jpg"
                val baseImagePath = "$username/baseImg.jpg"
                val myImageRef = storageRef.child(myImagePath)
                val baseImageRef = storageRef.child(baseImagePath)

                myImageRef.putFile(myImageUri).continueWithTask{task ->
                    if(!task.isSuccessful){
                        throw task.exception ?: Exception("myImg 업로드 실패")
                    }
                    myImageRef.downloadUrl
                }.addOnSuccessListener{ myImgUrl ->
                    baseImageRef.putFile(baseImageUri).continueWithTask{ task ->
                        if(!task.isSuccessful){
                            throw task.exception ?: Exception("baseImg 업로드 실패")
                        }
                        baseImageRef.downloadUrl
                    }.addOnSuccessListener { baseImageUri ->
                        val placeData = hashMapOf(
                            "placeName" to placeName,
                            "latitude" to latitude,
                            "longitude" to longitude,
                            "distance" to distance,
                            "steps" to steps,
                            "calories" to calories,
                            "myImg" to true,
                            "myImgUrl" to myImgUrl.toString(),
                            "baseImgUrl" to baseImageUri.toString()
                        )

                        db.collection("Places").document(nextId)
                            .set(placeData)
                            .addOnSuccessListener {
                                Toast.makeText(context, "장소 업로드 완료", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener{
                                Toast.makeText(context, "장소 업로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }.addOnFailureListener{
                        Toast.makeText(context, "기본 이미지 업로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, "마이 이미지 업로드 실패: ${it.message}",Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(context, "문서 개수 확인 x: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}