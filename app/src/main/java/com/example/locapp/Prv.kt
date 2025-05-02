package com.example.locapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.locapp.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.security.KeyFactory
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.ECFieldFp
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

class Prv : AppCompatActivity() {
    private val PICK_FILE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Add Bouncy Castle provider for PEM parsing
        Security.addProvider(BouncyCastleProvider())

        // Start file picker
        pickTextFile()
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
                val pubKeyParams = ECPublicKeyParameters(pubKeyPoint, domainParams)

                // Convert to Java ECPublicKey
                val x = normalizedPubKeyPoint.affineXCoord.toBigInteger()
                val y = normalizedPubKeyPoint.affineYCoord.toBigInteger()
                val javaPoint = java.security.spec.ECPoint(x, y)
                val pubKeySpec = ECPublicKeySpec(javaPoint, javaEcParams)
                val publicKey = keyFactory.generatePublic(pubKeySpec) as ECPublicKey
                Log.d("PrivateKeyApp", "Public Key (Base64): ${Base64.encodeToString(publicKey.encoded, Base64.DEFAULT)}")
            } else {
                Log.e("PrivateKeyApp", "PEM does not contain a private key")
            }
        } catch (e: Exception) {
            Log.e("PrivateKeyApp", "Error processing private key: ${e.message}")
        }
    }
}