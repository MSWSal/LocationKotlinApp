package com.example.locapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.File
import java.util.UUID

class HttpFile : AppCompatActivity() {

    private lateinit var ipEditText: EditText
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_http)

        // Initialize UI elements
        ipEditText = findViewById(R.id.ipEditText)
        sendButton = findViewById(R.id.sendButton)

        // Set click listener for send button
        sendButton.setOnClickListener {
            val serverIp = ipEditText.text.toString().trim()
            if (isValidIpAddress(serverIp)) {
                createAndUploadJsonFile(serverIp)
            } else {
                Toast.makeText(this, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidIpAddress(ip: String): Boolean {
        val ipPattern = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
        return ip.isNotEmpty() && ipPattern.matches(ip)
    }

    private fun createAndUploadJsonFile(serverIp: String) {
        try {
            // Create a sample JSON file
            val jsonFile = File(filesDir, "upload_${UUID.randomUUID()}.json")
            val jsonContent = """
                {
                    "id": "${UUID.randomUUID()}",
                    "timestamp": "${System.currentTimeMillis()}",
                    "data": "Sample file from mobile app"
                }
            """.trimIndent()
            jsonFile.writeText(jsonContent)

            // Create Retrofit instance
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("http://$serverIp:8081/")
                .client(client)
                .build()

            val fileApi = retrofit.create(FileApi::class.java)

            // Prepare the file for upload
            val requestFile = jsonFile.asRequestBody("application/json".toMediaTypeOrNull())
//            val filePart = MultipartBody.Part.createFormData("file", 1 filePart = MultipartBody.Part.createFormData("file", jsonFile.name, requestFile)
            val filePart = MultipartBody.Part.createFormData("file", jsonFile.name, requestFile)
            // Make the upload call
            val call = fileApi.uploadFile(filePart)

            call.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@HttpFile, "File uploaded successfully!", Toast.LENGTH_SHORT).show()
                        // Delete the file after successful upload
                        jsonFile.delete()
                    } else {
                        Toast.makeText(
                            this@HttpFile,
                            "Upload failed: ${response.code()} - ${response.message()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(
                        this@HttpFile,
                        "Upload error: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}