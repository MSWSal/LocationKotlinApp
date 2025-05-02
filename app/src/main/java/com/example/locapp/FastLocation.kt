package com.example.locapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.text.DecimalFormat
import kotlin.math.*

class FastLocation : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationTextView: TextView
    private lateinit var getLocationButton: Button
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val decimalFormat = DecimalFormat("#.##########") // 10 decimal places

    private val locations = listOf(
        Pair(6.902281, 79.8613898), // Sample location
        Pair(6.902283, -122.4167),  // Nearby in SF
        Pair(6.902281, 79.8613899), // Another nearby point
        Pair(6.902280, 79.8613896), // Farther away
        Pair(6.902282, 79.8613895)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // UI elements
        locationTextView = findViewById(R.id.locationTextView)
        getLocationButton = findViewById(R.id.getLocationButton)

        // Button click listener
        getLocationButton.setOnClickListener {
            getCurrentLocation()
        }
    }

    private fun checkLocationPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return false
        }
        return true
    }

    private fun getCurrentLocation() {
        if (!checkLocationPermission()) {
            return
        }

        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val currentLat = location.latitude
                    val currentLon = location.longitude
                    val formattedLat = decimalFormat.format(currentLat)
                    val formattedLon = decimalFormat.format(currentLon)

                    // Find locations within 0.3km radius
                    val nearbyLocations = findNearbyLocations(currentLat, currentLon, locations, 0.3)

                    if (nearbyLocations.isNotEmpty()) {
                        locationTextView.text = "Current: Lat: $formattedLat, Lon: $formattedLon\n" +
                                "Nearby locations found: ${nearbyLocations.size}"
                    } else {
                        locationTextView.text = "Current: Lat: $formattedLat, Lon: $formattedLon\n" +
                                "No locations found within 0.3km"
                    }
                } else {
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