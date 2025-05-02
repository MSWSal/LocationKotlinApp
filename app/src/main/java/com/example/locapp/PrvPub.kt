package com.example.locapp

import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.security.*
import java.security.spec.X509EncodedKeySpec

class PrvPub : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Create the original JSON object
        val originalJson = JSONObject()
        originalJson.put("name", "Alice")
        originalJson.put("timestamp", System.currentTimeMillis())

        // 2. Generate ECDSA key pair
        val keyPair = generateECKeyPair()
//        keyPair.public

        // 3. Sign the JSON (using its canonical string representation)
        val jsonToSign = originalJson.toString()
        val signature = signData(jsonToSign.toByteArray(Charsets.UTF_8), keyPair.private)

        // 4. Create a new JSON object with the original and the signature
        val signedJson = JSONObject()
        signedJson.put("payload", originalJson)
        signedJson.put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
        signedJson.put("publicKey", Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))

        Log.d("SignedJSON", signedJson.toString(2))

        // 5. Verify the signature
        val isValid = verifySignature(
            signedJson.getJSONObject("payload").toString().toByteArray(Charsets.UTF_8),
            Base64.decode(signedJson.getString("signature"), Base64.NO_WRAP),
            Base64.decode(signedJson.getString("publicKey"), Base64.NO_WRAP)
        )
        Log.d("SignatureValid", isValid.toString())
    }

    // Generate EC key pair (secp256r1)
    private fun generateECKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256)
        return keyGen.generateKeyPair()
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
