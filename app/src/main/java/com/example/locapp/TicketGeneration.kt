package com.example.locapp

import android.content.Intent
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
import androidx.activity.result.contract.ActivityResultContracts
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

class TicketGeneration: AppCompatActivity() {
    companion object {
        init {
            // Initialize Bouncy Castle provider early
            Security.addProvider(BouncyCastleProvider())
        }
    }
    private val PICK_FILE_REQUEST = 1
    private val KEYSTORE_ALIAS = "my_private_key"
    private val PREFS_NAME = "secure_prefs"
    private val PUBLIC_KEY_PREF = "public_key"

    // File picker launcher
    private val pickJsonFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { processSelectedJsonFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket)
        val provButton = findViewById<Button>(R.id.prov)
        val verifyButton = findViewById<Button>(R.id.verify)

        provButton.setOnClickListener{
            val privateKey = getPrivateKeyFromKeystore()
            val publicKey = getPublicKeyFromPrefs()

            if (privateKey != null && publicKey != null) {
                // Keys found, proceed with JSON signing
                processJsonWithKeys(privateKey, publicKey)
            }
        }

        // Pick and parse JSON file
        verifyButton.setOnClickListener {
            openJsonFilePicker()
        }
    }

    private fun openJsonFilePicker() {
        // Launch file picker for JSON files
        pickJsonFileLauncher.launch(arrayOf("application/json"))
    }

    private fun processSelectedJsonFile(uri: android.net.Uri) { //========verify button======
        try {
            // Read file contents and parse as JSONObject
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(content)
                val payload = json.getJSONObject("payload")
                Log.d("JSONPicker", "Parsed JSON: $payload")
            } ?: run {
                Log.e("JSONPicker", "Failed to open input stream for URI: $uri")
            }
        } catch (e: Exception) {
            Log.e("JSONPicker", "Error processing file: ${e.message}", e)
        }
    }

    private fun getPrivateKeyFromKeystore(): PrivateKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
//                keyStore.getKey(KEYSTORE_ALIAS, null) as ECPrivateKey
                val key = keyStore.getKey(KEYSTORE_ALIAS, null)
                Log.d("PrivateKeyApp", "Retrieved key type: ${key.javaClass.name}")
                if (key is PrivateKey) {
                    key
                } else {
                    Log.e("PrivateKeyApp", "Key is not a PrivateKey: ${key.javaClass.name}")
                    null
                }
            } else {
                Log.d("PrivateKeyApp", "Private key not found in Keystore")
                null
            }
        } catch (e: Exception) {
            Log.e("PrivateKeyApp", "Error retrieving private key: ${e.message}")
            null
        }
    }

    private fun getPublicKeyFromPrefs(): ECPublicKey? {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPrefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val encodedKey = sharedPrefs.getString(PUBLIC_KEY_PREF, null)
            if (encodedKey != null) {
                val keyBytes = Base64.decode(encodedKey, Base64.NO_WRAP)
                val keyFactory = KeyFactory.getInstance("EC")
                val PubK = keyFactory.generatePublic(X509EncodedKeySpec(keyBytes)) as ECPublicKey
                PubK
            } else {
                Log.d("PrivateKeyApp", "Public key not found in SharedPreferences")
                null
            }
        } catch (e: Exception) {
            Log.e("PrivateKeyApp", "Error retrieving public key: ${e.message}")
            null
        }
    }

    private fun processJsonWithKeys(privateKey: PrivateKey, publicKey: ECPublicKey) { //================prover==============
        try {
            // Create the original JSON object
            val originalJson = JSONObject()
            originalJson.put("timestamp", "LocationData.timestamp")
            originalJson.put("nvalue","LocationData.n")

            if(LocationData.timestamp==null || LocationData.n==null){
                Log.e("PrivateKeyAppNullErr", "time and N Null")

            }else

            // Sign the JSON (using its canonical string representation)
            {
                val jsonToSign = originalJson.toString()
                val signature = signData(jsonToSign.toByteArray(Charsets.UTF_8), privateKey)

                // Create a new JSON object with the original and the signature
                val signedJson = JSONObject()
                signedJson.put("payload", originalJson)
                signedJson.put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
                signedJson.put(
                    "publicKey",
                    Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
                )

                Log.d("SignedJSON", signedJson.toString(2))

                val jsonString = signedJson.toString(2)

                val fileName = "${System.currentTimeMillis()}_prov.json"
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
            }


        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PrivateKeyApp", "Error processing JSON: ${e.message}")
        }
    }

    private fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

}