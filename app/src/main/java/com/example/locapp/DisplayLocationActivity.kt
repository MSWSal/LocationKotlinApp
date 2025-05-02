package com.example.locapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import com.example.locapp.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.locapp.data.LocationData
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.security.*
import java.security.cert.Certificate
import java.security.spec.*
import java.util.*
import java.math.BigInteger
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.jce.spec.ECParameterSpec as BCECParameterSpec
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.math.ec.ECCurve
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import com.google.android.gms.location.*
import java.text.DecimalFormat
import kotlin.math.*

class DisplayLocationActivity : AppCompatActivity() {
    companion object {
        init {
            // Initialize Bouncy Castle provider early
            Security.addProvider(BouncyCastleProvider())
        }
    }
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationTextView: TextView
    private lateinit var getLocationButton: Button
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val decimalFormat = DecimalFormat("#.##########")
    private val PICK_FILE_REQUEST = 1
    private val KEYSTORE_ALIAS = "my_private_key"
    private val PREFS_NAME = "secure_prefs"
    private val PUBLIC_KEY_PREF = "public_key"
    private lateinit var map: MapView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSMdroid
        Configuration.getInstance().load(this, getSharedPreferences("OSM_PREFS", MODE_PRIVATE))

        setContentView(R.layout.activity_display_location)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        val verifyButton = findViewById<Button>(R.id.verify)

        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)


        // Get MapView from layout
        map = findViewById(R.id.mapp)
        map.setMultiTouchControls(true)

        // Set default zoom level and location
        val mapController = map.controller
        mapController.setZoom(15.0)

        val startPoint = GeoPoint(latitude, longitude) // Example: Eiffel Tower, Paris
//        val endPoint = GeoPoint(8.9541, 80.7547)
        mapController.setCenter(startPoint)

        val startMarker = Marker(map)
        startMarker.position = startPoint
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        startMarker.title = "Go Here"
        map.overlays.add(startMarker)

        verifyButton.setOnClickListener(){
            getCurrentLocation(latitude,longitude)
        }

//
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

    private fun getCurrentLocation(lat: Double, lon: Double) {
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

                    Log.d("Locations","${currentLat} and ${currentLon} is the current locations")

                    val isWithin10Meters = isWithin10Meters(currentLat, currentLon, lat, lon)

                    if (isWithin10Meters==true){
                        Toast.makeText(this, "Location is okay", Toast.LENGTH_SHORT).show()
//==========================================intent================
                    }else{}
                    Toast.makeText(this, "Too far...", Toast.LENGTH_SHORT).show()

                } else {
                    Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to calculate distance between two lat/lon points in meters (Haversine formula)
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c // Distance in meters
    }

    // Function to check if current location is within 10 meters of a given lat/lon
    private fun isWithin10Meters(currentLat: Double, currentLon: Double, targetLat: Double, targetLon: Double): Boolean {
        val distance = calculateDistance(currentLat, currentLon, targetLat, targetLon)
        return distance <= 10.0 // Return true if distance is 10 meters or less
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,"came to on req perm",Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume() // needed for compass, my location overlays, etc.
    }

    override fun onPause() {
        super.onPause()
        map.onPause() // needed for compass, my location overlays, etc.
    }
}