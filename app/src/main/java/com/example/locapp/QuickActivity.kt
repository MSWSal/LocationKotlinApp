package com.example.locapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class QuickActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quickshare)

        statusText = findViewById(R.id.statusText)
        val shareButton = findViewById<Button>(R.id.shareButton)

        shareButton.setOnClickListener {
            shareJsonFile()
        }
    }

    private fun shareJsonFile() {
        try {
            // Create JSON object
            val jsonObject = JSONObject().apply {
                put("id", 1)
                put("name", "Sample User")
                put("email", "user@example.com")
                put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()))
            }
            val jsonString = jsonObject.toString(4) // Pretty print with indentation

            // Save JSON to a temporary file in cache
            val fileName = "shared_data_${System.currentTimeMillis()}.json"
            val jsonFile = File(cacheDir, fileName)
            jsonFile.writeText(jsonString)

            // Get content URI using FileProvider
            val fileUri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                jsonFile
            )

            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant permission to receiving app
            }

            // Launch Sharesheet with Quick Share option
            val chooserIntent = Intent.createChooser(shareIntent, "Share JSON via")
            startActivity(chooserIntent)

            statusText.text = "Status: Sharing initiated"
        } catch (e: Exception) {
            e.printStackTrace()
            statusText.text = "Status: Error - ${e.message}"
        }
    }
}