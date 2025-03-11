package com.example.locapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date

class SendActivity : AppCompatActivity() {
    private lateinit var ipEditText: EditText
    private lateinit var sendButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)

        ipEditText = findViewById(R.id.ipEditText)
        sendButton = findViewById(R.id.sendButton)

        sendButton.setOnClickListener {
            val serverIp = ipEditText.text.toString().trim()
            if (serverIp.isNotEmpty()) {
                sendFileOverNetwork(serverIp)
            } else {
                Toast.makeText(this, "Please enter server IP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendFileOverNetwork(serverIp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create JSON object
                val jsonObject = JSONObject().apply {
                    put("id", 1)
                    put("name", "Sample User")
                    put("email", "user@example.com")
                    put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()))
                }

                // Convert JSON to string
                val jsonString = jsonObject.toString(4)
                val fileName = "data_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.json"

                // Connect to server
                Socket(serverIp, 8080).use { socket ->
                    val outputStream = socket.getOutputStream()
                    val writer = DataOutputStream(outputStream)

                    // Send file name first
                    writer.writeUTF(fileName)
                    writer.flush()

                    // Send file content
                    writer.writeUTF(jsonString)
                    writer.flush()

                    runOnUiThread {
                        Toast.makeText(this@SendActivity, "File sent successfully", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@SendActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}