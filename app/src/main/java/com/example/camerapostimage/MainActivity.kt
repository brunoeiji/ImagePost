package com.example.camerapostimage

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import java.util.Base64
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream


class MainActivity : AppCompatActivity() {

    private val CAMERA_REQUEST = 0x42
    private val GALLERY_REQUEST = 0x41
    private val PERMISSION_CODE = 0x40
    private val POST_URL = "http://192.168.0." //COMPLETAR AQUI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraButton.setOnClickListener {
            openCamera()
        }

        galleryButton.setOnClickListener {
            checkPermissionForGallery()
        }
    }

    private fun checkPermissionForGallery(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){
                //permission denied
                val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE);
                //show popup to request runtime permission
                requestPermissions(permissions, PERMISSION_CODE);
            }
            else{ //permission already granted
                openGallery();
            }
        }
        else{ //system OS is < Marshmallow
            openGallery();
        }
    }

    private fun openCamera(){
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(takePictureIntent, CAMERA_REQUEST)
            }
        }
    }

    private fun openGallery(){
        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { pickPhoto ->
            pickPhoto.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pickPhoto.resolveActivity(packageManager)?.also {
                startActivityForResult(pickPhoto, GALLERY_REQUEST)
            }
        }
    }

    private fun uploadImageToKasco(image:Bitmap){
        getBase64Image(image, complete = { base64Image ->
            GlobalScope.launch {
                val body = "{ \"image\":\"" + base64Image + "\"}"
                Log.d("BASE 64 IMAGE", body)
//                Fuel.post(POST_URL)
//                    .jsonBody(body)
//                    .also { println(it) }
//                    .response { result ->
//                        print(result)
//                    }
            }
        })
    }

    private fun getBase64Image(image: Bitmap, complete: (String?) -> Unit){
        GlobalScope.launch {
            val outputStream = ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.PNG, 100, outputStream )
            val b = outputStream.toByteArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                complete(Base64.getEncoder().encodeToString(b))
            }else{
                complete(null)
            }
        }
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return when {
            Build.VERSION.SDK_INT < 28 -> MediaStore.Images.Media.getBitmap(
                contentResolver,
                uri
            )
            else -> {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            openGallery()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        var selectedImage: Bitmap? = null
        if(requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK){
            selectedImage = data?.extras?.get("data") as Bitmap
        }else if(requestCode == GALLERY_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            Log.e("Galeria selected", "")
            if (uri != null) {
                selectedImage = uriToBitmap(uri)
                Log.e("Galeria selected", selectedImage.toString())
            }
        }else {
            super.onActivityResult(requestCode, resultCode, data)
        }
        if(selectedImage != null) {
            uploadImageToKasco(selectedImage)
            imageView.setImageBitmap(selectedImage)
        }else{
            val alertDialog: AlertDialog? = this?.let {
                val builder = AlertDialog.Builder(it)
                builder.setTitle("Error")
                builder.setMessage("Bitmap null, não foi possível fazer o upload")
                builder.apply {
                    setPositiveButton("Ok", null)
                }

                builder.create()
            }
        }
    }
}