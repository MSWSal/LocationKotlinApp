package com.example.locapp

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date

class StoreActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val saveButton = findViewById<Button>(R.id.saveButton)
        saveButton.setOnClickListener {
            // Launch the document creation dialog
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            createDocument.launch("data_$timestamp.json")
        }

    }

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { documentUri ->
            try {
                // Create JSON object
                val jsonObject = JSONObject().apply {
                    put("id", 1)
                    put("name", "Sample User")
                    put("email", "user@example.com")
                    put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()))
                    put("details", JSONObject().apply {
                        put("age", 25)
                        put("city", "New York")
                    })
                }

                // Write JSON to the selected location
                contentResolver.openOutputStream(documentUri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonObject.toString(4)) // 4 is for pretty printing
                        writer.flush()
                    }
                }

                Toast.makeText(this, "JSON file saved successfully", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }





}