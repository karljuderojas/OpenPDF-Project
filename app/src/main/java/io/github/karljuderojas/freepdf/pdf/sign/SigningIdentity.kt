package io.github.karljuderojas.freepdf.pdf.sign

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/** A private key plus its certificate chain, used to apply a PKCS#7 signature. */
class SigningIdentity(val privateKey: PrivateKey, val chain: List<X509Certificate>) {

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TEN_YEARS_MS = 10L * 365 * 24 * 60 * 60 * 1000

        /**
         * The on-device identity: an RSA key that never leaves the Android Keystore, with a
         * self-signed certificate naming [commonName]. Readers will show it as "validity unknown"
         * until the user imports a certificate from a trusted provider (see docs/signing-design.md).
         */
        fun deviceIdentity(commonName: String, alias: String = "freepdf-signing"): SigningIdentity {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(alias)) {
                val now = System.currentTimeMillis()
                val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .setCertificateSubject(X500Principal("CN=$commonName, O=FreePDF self-signed"))
                    .setCertificateSerialNumber(BigInteger.valueOf(now))
                    .setCertificateNotBefore(Date(now))
                    .setCertificateNotAfter(Date(now + TEN_YEARS_MS))
                    .build()
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE).run {
                    initialize(spec)
                    generateKeyPair()
                }
            }
            val key = keyStore.getKey(alias, null) as PrivateKey
            val chain = keyStore.getCertificateChain(alias).map { it as X509Certificate }
            return SigningIdentity(key, chain)
        }

        /** An identity from a user-supplied .p12/.pfx file, e.g. one issued by a trusted CA. */
        fun fromPkcs12(bytes: ByteArray, password: CharArray): SigningIdentity {
            val keyStore = KeyStore.getInstance("PKCS12").apply { load(bytes.inputStream(), password) }
            val alias = keyStore.aliases().toList().first { keyStore.isKeyEntry(it) }
            val key = keyStore.getKey(alias, password) as PrivateKey
            val chain = keyStore.getCertificateChain(alias).map { it as X509Certificate }
            return SigningIdentity(key, chain)
        }
    }
}
