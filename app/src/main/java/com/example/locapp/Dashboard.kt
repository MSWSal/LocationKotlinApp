package com.example.locapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.locapp.data.LocationData

class Dashboard : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Button to calculate and pass location to DisplayLocationActivity
        findViewById<Button>(R.id.calculateButton).setOnClickListener {
            val (index, selectedLocation) = performCalculation()
            LocationData.setN(index)// Store the modulo value in N
            // Start DisplayLocationActivity with latitude and longitude
            val intent = Intent(this, DisplayLocationActivity::class.java).apply {
                putExtra("latitude", selectedLocation.first)
                putExtra("longitude", selectedLocation.second)
            }
            startActivity(intent)
        }


    }

    private fun performCalculation(): Pair<Int, Pair<Double, Double>> {
        // Get current timestamp in milliseconds
        val timestamp = System.currentTimeMillis()
        LocationData.setTimestamp(timestamp)

        // Get the length of the locations list
        val listSize = LocationData.locations.size

        // Check for empty list to avoid division by zero
        require(listSize > 0) { "Locations list cannot be empty" }

        // Calculate the modulo value (index)
        val index = (timestamp % listSize).toInt()

        // Select the location pair based on the index
        val selectedLocation = LocationData.locations[index]

        // Return both the index (for N) and the selected location
        return Pair(index, selectedLocation)
    }
}