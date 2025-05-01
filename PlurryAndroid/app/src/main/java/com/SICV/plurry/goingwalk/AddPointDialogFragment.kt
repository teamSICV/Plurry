package com.SICV.plurry.goingwalk
import com.SICV.plurry.R

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddPointDialogFragment : DialogFragment() {

    private val CAMERA_REQUEST_CODE = 101
    private lateinit var imageUri: Uri

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_point, null)
        val builder = AlertDialog.Builder(requireActivity()).setView(view)

        val imagePreview = view.findViewById<ImageView>(R.id.imagePreview)
        val btnTakePhoto = view.findViewById<Button>(R.id.btnTakePhoto)
        val btnRetake = view.findViewById<Button>(R.id.btnRetake)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirm)
        val btnClose = view.findViewById<Button>(R.id.btnClose)
        val photoActions = view.findViewById<LinearLayout>(R.id.photoActionButtons)

        btnTakePhoto.setOnClickListener {
            openCamera()
        }

        btnRetake.setOnClickListener {
            openCamera()
        }

        btnConfirm.setOnClickListener {
            Toast.makeText(requireContext(), "촬영 완료!", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        btnClose.setOnClickListener {
            dismiss()
        }

        return builder.create()
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                val photoFile = createImageFile()

                if (photoFile == null) {
                    Toast.makeText(requireContext(), "❌ 사진 파일 생성 실패!", Toast.LENGTH_SHORT).show()
                    return
                }

                val authority = "${requireContext().packageName}.fileprovider"

                imageUri = FileProvider.getUriForFile(
                    requireContext(),
                    authority,
                    photoFile
                )

                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                startActivityForResult(intent, CAMERA_REQUEST_CODE)
            } else {
                Toast.makeText(requireContext(), "❌ 카메라 앱 없음", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "🚨오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }




    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        if (storageDir == null) {
            Toast.makeText(requireContext(), "저장소를 찾을 수 없습니다!", Toast.LENGTH_SHORT).show()
            return null
        }

        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            dialog?.findViewById<ImageView>(R.id.imagePreview)?.apply {
                setImageURI(imageUri)
                visibility = View.VISIBLE
            }
            dialog?.findViewById<Button>(R.id.btnTakePhoto)?.visibility = View.GONE
            dialog?.findViewById<LinearLayout>(R.id.photoActionButtons)?.visibility = View.VISIBLE
        }
    }
}
