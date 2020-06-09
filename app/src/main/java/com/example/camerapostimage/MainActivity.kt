package com.example.camerapostimage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import java.util.Base64
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import java.io.ByteArrayOutputStream


class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private val CAMERA_REQUEST = 0x42
    private val GALLERY_REQUEST = 0x41
    private val PERMISSION_CODE = 0x40

    private val httpChoices = arrayOf("HTTP", "HTTPS")
    private var selectedImage: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create an ArrayAdapter using the string array and a default spinner layout
        val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, httpChoices)
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        httpSpinner.adapter = arrayAdapter
        httpSpinner.onItemSelectedListener = this

        urlTextField.editText?.setText(getURL())

        cameraButton.setOnClickListener {
            openCamera()
        }

        galleryButton.setOnClickListener {
            checkPermissionForGallery()
        }

        sendButton.setOnClickListener {
            sendImage()
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

    private fun createURL() : String{
        val url = httpSpinner.selectedItem.toString().toLowerCase()+ "://" + urlTextField.editText?.text
        Log.e("URL",url)
        return url
    }

    private fun sendImage(){
        if(selectedImage != null) {
            if(urlTextField.editText?.text?.isNotEmpty()!!) {
                progressBar.visibility = View.VISIBLE
                uploadImageToKasco(selectedImage!!)
            }else{
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Error")
                builder.setMessage("Coloque uma URL para enviar a imagem")
                builder.setPositiveButton("Ok", null)
                builder.show()
            }
        }else{
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Error")
            builder.setMessage("Escolha uma imagem para enviar")
            builder.setPositiveButton("Ok", null)
            builder.show()
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
            val body = "{ \"image\":\"" + base64Image + "\"}"
//                Log.d("BASE 64 IMAGE", body)
            val url = createURL()
            saveURL(url)
            Fuel.post(url)
                .jsonBody(body)
                .responseString { result ->
                    progressBar.visibility = View.INVISIBLE
                    when (result) {
                        is Result.Failure -> {
                            val er = result.getException()
                            Log.e("Error", er.toString())
                            runOnUiThread { Toast.makeText(this@MainActivity, "Falhou", Toast.LENGTH_LONG).show() }
                        }
                        is Result.Success -> {
                            val data = result.get()
                            println(data)
                            Log.e("Data", data)
                            runOnUiThread { Toast.makeText(this@MainActivity, "Sucesso", Toast.LENGTH_LONG).show() }
                        }
                    }
                }
        })
    }

    private fun getBase64Image(image: Bitmap, complete: (String?) -> Unit){
        val outputStream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG, 100, outputStream )
        val b = outputStream.toByteArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            complete(Base64.getEncoder().encodeToString(b))
        }else{
            complete(null)
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

    private fun saveURL(url: String){
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()){
            putString("url", url)
            apply()
        }
    }

    private fun getURL() : String{
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        return sharedPref.getString("url", "")!!
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
            imageView.setImageBitmap(selectedImage)
        }
    }

    override fun onNothingSelected(p0: AdapterView<*>?) {
        p0?.textView?.text =  httpChoices[0]
    }

    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        p0?.textView?.text =  httpChoices[p2]
    }
}
