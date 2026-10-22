package com.kundan.smartremote

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * Holds this phone's ADB identity (an RSA key + self-signed certificate) and
 * the connection to the TV. The key is created once and reused, so the TV
 * only has to be paired one time.
 */
class AdbManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        // Version of adbd on the TV side. Lumio Google TV runs Android 14 (API 34).
        setApi(34)
        val keyFile = File(context.filesDir, "adb_key.pk8")
        val certFile = File(context.filesDir, "adb_cert.der")
        if (keyFile.exists() && certFile.exists()) {
            privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(certFile.inputStream())
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val pair = kpg.generateKeyPair()
            val subject = X500Name("CN=SmartRemote")
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                Date(now - 24L * 3600 * 1000),
                Date(now + 30L * 365 * 24 * 3600 * 1000),
                subject,
                pair.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            privateKey = pair.private
            certificate = cert
            keyFile.writeBytes(pair.private.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "SmartRemote"

    companion object {
        @Volatile private var instance: AdbManager? = null
        fun get(context: Context): AdbManager =
            instance ?: synchronized(this) {
                instance ?: AdbManager(context.applicationContext).also { instance = it }
            }
    }
}
