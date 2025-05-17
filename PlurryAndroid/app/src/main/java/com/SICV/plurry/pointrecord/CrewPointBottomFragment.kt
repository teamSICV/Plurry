package com.SICV.plurry.pointrecord

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore

class CrewPointBottomFragment : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView

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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setDimAmount(0f)
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.crewPointRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

        fetchImageUrlsFromFirestore()
    }

    private fun fetchImageUrlsFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
        val imageUrlList = mutableListOf<String>()

        db.collection("Places")
            .get()
            .addOnSuccessListener { result ->
                val gsUrlList = mutableListOf<String>()

                for (document in result) {
                    val isMyImg = document.getBoolean("myImg") ?: false
                    val imageUrl = if (isMyImg)
                        document.getString("myImgUrl")
                    else
                        document.getString("baseImgUrl")

                    imageUrl?.let {
                        gsUrlList.add(it)
                    }
                }

                val downloadUrls = mutableListOf<String>()
                var processedCount = 0

                for (gsUrl in gsUrlList){
                    if(gsUrl.startsWith("gs://")){
                        val ref = storage.getReferenceFromUrl(gsUrl)
                        ref.downloadUrl
                            .addOnSuccessListener { uri ->
                                downloadUrls.add(uri.toString())
                                processedCount++
                                if(processedCount == gsUrlList.size){
                                    recyclerView.adapter = CrewPointBottomAdapter(requireContext(),downloadUrls)
                                }
                            }
                            .addOnFailureListener{
                                processedCount++
                                if(processedCount == gsUrlList.size){
                                    recyclerView.adapter = CrewPointBottomAdapter(requireContext(),downloadUrls)
                                }
                            }
                    }else{
                        downloadUrls.add(gsUrl)
                        processedCount++
                        if(processedCount == gsUrlList.size){
                            recyclerView.adapter = CrewPointBottomAdapter(requireContext(), downloadUrls)
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
