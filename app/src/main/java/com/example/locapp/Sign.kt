package com.example.locapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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

class Sign : AppCompatActivity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign)
        val processButton = findViewById<Button>(R.id.processButton)
        // Add Bouncy Castle provider for PEM parsing
//        Security.addProvider(BouncyCastleProvider())
        processButton.setOnClickListener{
            val privateKey = getPrivateKeyFromKeystore()
            val publicKey = getPublicKeyFromPrefs()

            if (privateKey != null && publicKey != null) {
                // Keys found, proceed with JSON signing
                processJsonWithKeys(privateKey, publicKey)
            } else {
                // No keys found, prompt for file picker to process a new key
                Log.d("PrivateKeyApp", "No stored keys found, prompting for file input")
                pickTextFile()
            }
        }
        // Try to retrieve stored keys and process JSON

    }

    private fun pickTextFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "text/plain"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select a text file"), PICK_FILE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val keyContent = readTextFile(uri)
                    processPrivateKey(keyContent)
                } catch (e: Exception) {
                    Log.e("PrivateKeyApp", "Error reading file: ${e.message}")
                }
            }
        }
    }

    private fun readTextFile(uri: android.net.Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val content = StringBuilder()
        reader.useLines { lines ->
            lines.forEach { content.append(it).append("\n") }
        }
        inputStream?.close()
        return content.toString()
    }

    private fun processPrivateKey(keyContent: String) {
        try {
            val reader = StringReader(keyContent)
            val pemParser = PEMParser(reader)
            val objectRead = pemParser.readObject()

            if (objectRead is PrivateKeyInfo) {
                val privInfo = objectRead
                val encoded = privInfo.encoded
                val keyFactory = KeyFactory.getInstance("EC")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(encoded)) as ECPrivateKey
                Log.d("PrivateKeyApp", "Valid ECDSA Private Key detected")

                // Convert Java ECParameterSpec to Bouncy Castle ECDomainParameters
                val javaEcParams = privateKey.params
                val fieldFp = javaEcParams.curve.field as ECFieldFp
                val p = fieldFp.p
                val a = javaEcParams.curve.a
                val b = javaEcParams.curve.b
                val bcCurve = ECCurve.Fp(p, a, b)
                val gX = javaEcParams.generator.affineX
                val gY = javaEcParams.generator.affineY
                val bcG = bcCurve.createPoint(gX, gY)
                val n = javaEcParams.order
                val h = BigInteger.valueOf(javaEcParams.cofactor.toLong())
                val seed = javaEcParams.curve.seed
                val domainParams = ECDomainParameters(bcCurve, bcG, n, h, seed)

                // Generate public key using Bouncy Castle EC arithmetic
                val privKeyParams = ECPrivateKeyParameters(privateKey.s, domainParams)
                val pubKeyPoint = domainParams.g.multiply(privKeyParams.d)
                val normalizedPubKeyPoint = pubKeyPoint.normalize()
                val pubKeyParams = ECPublicKeyParameters(normalizedPubKeyPoint, domainParams)

                // Convert to Java ECPublicKey
                val x = normalizedPubKeyPoint.getXCoord().toBigInteger()
                val y = normalizedPubKeyPoint.getYCoord().toBigInteger()
                val javaPoint = java.security.spec.ECPoint(x, y)
                val pubKeySpec = ECPublicKeySpec(javaPoint, javaEcParams)
                val publicKey = keyFactory.generatePublic(pubKeySpec) as ECPublicKey
                Log.d("PrivateKeyApp", "Public Key (Base64): ${Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)}")

                // Store keys persistently
                storePrivateKeyInKeystore(privateKey,publicKey)
                storePublicKeyInPrefs(publicKey)

                // Process JSON with the new keys
                processJsonWithKeys(privateKey, publicKey)
            } else {
                Log.e("PrivateKeyApp", "PEM does not contain a private key")
            }
        } catch (e: Exception) {
            Log.e("PrivateKeyApp", "Error processing private key: ${e.message}")
        }
    }

    private fun generateSelfSignedCertificate(
        privateKey: ECPrivateKey,
        publicKey: ECPublicKey
    ): Certificate {
        try {
            val issuer = X500Name("CN=PrivateKeyApp")
            val subject = issuer // Self-signed, so issuer = subject
            val serial = BigInteger.valueOf(System.currentTimeMillis())
            val notBefore = Date()
            val notAfter = Date(notBefore.time + 365L * 24 * 60 * 60 * 1000) // 1 year validity

            val certBuilder = X509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                subject,
                org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(publicKey.encoded)
            )

            val signer = JcaContentSignerBuilder("SHA256withECDSA")
                .build(privateKey)

            val certHolder = certBuilder.build(signer)
            Log.d("holaa", "Pp")
            return JcaX509CertificateConverter().getCertificate(certHolder)
        } catch (e: Exception) {
            throw RuntimeException("Error generating self-signed certificate: ${e.message}", e)
        }
    }

    private fun storePrivateKeyInKeystore(privateKey: ECPrivateKey,publicKey: ECPublicKey) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }

            // Check if key already exists and delete it to avoid conflicts
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }

            val certificate = generateSelfSignedCertificate(privateKey, publicKey)
            val chain = arrayOf(certificate)

            // Store the private key
            keyStore.setKeyEntry(
                KEYSTORE_ALIAS,
                privateKey,
                null, // No password protection for Keystore-managed keys
                chain  // No certificate chain needed for private key
            )
            Log.d("PrivateKeyApp", "Private key stored in Android Keystore with alias: $KEYSTORE_ALIAS")
        } catch (e: Exception) {
            Log.e("PrivateKeyApp", "Error storing private key: ${e.message}")
        }
    }

    private fun storePublicKeyInPrefs(publicKey: ECPublicKey) {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val sharedPrefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                this,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            with(sharedPrefs.edit()) {
                putString(PUBLIC_KEY_PREF, Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP))
                apply()
            }
            Log.d("PrivateKeyApp", "Public key stored in EncryptedSharedPreferences")
        } catch (e: Exception) {
            Log.e("PrivateKeyApp", "Error storing public key: ${e.message}")
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
                Log.d("PrivateKeyApp", "Retrieved Public Key Bytes (Hex): ${byteArrayToHex(PubK.encoded)}")
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

    private fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun processJsonWithKeys(privateKey: PrivateKey, publicKey: ECPublicKey) {
        try {
            // Create the original JSON object
            val originalJson = JSONObject()
            originalJson.put("name", "Alice")
            originalJson.put("timestamp", System.currentTimeMillis())

            // Sign the JSON (using its canonical string representation)
            val jsonToSign = originalJson.toString()
            val signature = signData(jsonToSign.toByteArray(Charsets.UTF_8), privateKey)

            // Create a new JSON object with the original and the signature
            val signedJson = JSONObject()
            signedJson.put("payload", originalJson)
            signedJson.put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
            signedJson.put("publicKey", Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP))

            Log.d("SignedJSON", signedJson.toString(2))

            // Verify the signature
            val isValid = verifySignature(
                signedJson.getJSONObject("payload").toString().toByteArray(Charsets.UTF_8),
                Base64.decode(signedJson.getString("signature"), Base64.NO_WRAP),
                Base64.decode(signedJson.getString("publicKey"), Base64.NO_WRAP)
            )
            Log.d("SignatureValid", isValid.toString())
        } catch (e: Exception) {
            Log.e("PrivateKeyApp", "Error processing JSON: ${e.message}")
        }
    }

    // Sign data using ECDSA
    private fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    // Verify ECDSA signature
    private fun verifySignature(data: ByteArray, signatureBytes: ByteArray, publicKeyBytes: ByteArray): Boolean {
        val keyFactory = KeyFactory.getInstance("EC")
        val pubKeySpec = X509EncodedKeySpec(publicKeyBytes)
        val publicKey = keyFactory.generatePublic(pubKeySpec)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(publicKey)
        signature.update(data)
        return signature.verify(signatureBytes)
    }
}