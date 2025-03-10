package com.example.locapp

import android.content.pm.PackageManager
import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import com.example.locapp.ui.theme.LocAppTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationTextView: TextView
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private val locations = listOf(
        Pair(37.7749, -122.4194), // San Francisco
        Pair(37.7833, -122.4167), // Nearby in SF
        Pair(37.7950, -122.4020), // Another nearby point
        Pair(38.0000, -122.0000)  // Farther away
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // UI elements
        locationTextView = findViewById(R.id.locationTextView)
        val getLocationButton: Button = findViewById(R.id.getLocationButton)

        // Button click listener
        getLocationButton.setOnClickListener {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        // Check for location permissions
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission if not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        // Get last known location
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val currentLat = location.latitude
                    val currentLon = location.longitude
// Find locations within 1km radius
                    val nearbyLocations = findNearbyLocations(currentLat, currentLon, locations, 1.0)

                    if (nearbyLocations.isNotEmpty()) {
                        // Pick a random location from the nearby ones
                        val randomLocation = nearbyLocations[Random.nextInt(nearbyLocations.size)]
                        locationTextView.text = "Random nearby location:\n" +
                                "Latitude: ${randomLocation.first}\n" +
                                "Longitude: ${randomLocation.second}"
                    } else {
                        locationTextView.text = "Current: Lat: $currentLat, Lon: $currentLon\n" +
                                "No locations found within 1km"
                    }                } else {
                    Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to calculate distance between two lat/lon points in kilometers (Haversine formula)
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    // Function to find locations within a given radius
    private fun findNearbyLocations(
        centerLat: Double,
        centerLon: Double,
        locations: List<Pair<Double, Double>>,
        radiusKm: Double
    ): List<Pair<Double, Double>> {
        return locations.filter { (lat, lon) ->
            val distance = calculateDistance(centerLat, centerLon, lat, lon)
            distance <= radiusKm
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

}

//@Composable
//fun Greeting(name: String, modifier: Modifier = Modifier) {
//    Text(
//        text = "Hello $name!",
//        modifier = modifier
//    )
//}
//
//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    LocAppTheme {
//        Greeting("Android")
//    }
//}