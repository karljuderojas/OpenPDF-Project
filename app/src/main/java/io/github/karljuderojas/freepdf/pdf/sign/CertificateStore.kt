package io.github.karljuderojas.freepdf.pdf.sign

import android.content.SharedPreferences
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date

/**
 * The user's own signing certificate, imported from a .p12/.pfx file, and whether signatures get
 * a trusted timestamp. The private key goes into the Android Keystore, so it never leaves the
 * phone in readable form; the .p12 file and its password are not kept.
 */
class CertificateStore(private val prefs: SharedPreferences) {

    /** The imported identity, or null to sign with the device certificate. */
    fun imported(): SigningIdentity? = runCatching {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(ALIAS)) return null
        val key = keyStore.getKey(ALIAS, null) as PrivateKey
        val chain = keyStore.getCertificateChain(ALIAS).map { it as X509Certificate }
        SigningIdentity(key, chain)
    }.getOrNull()

    /**
     * Reads [bytes] with [password] and replaces any earlier imported certificate. Throws when
     * the file cannot be read, the password is wrong, or the certificate has expired.
     */
    fun import(bytes: ByteArray, password: CharArray): SigningIdentity {
        val identity = SigningIdentity.fromPkcs12(bytes, password)
        identity.chain.first().checkValidity()
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .apply {
                if (identity.privateKey.algorithm == KeyProperties.KEY_ALGORITHM_RSA) {
                    setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                }
            }.build()
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.setEntry(
            ALIAS,
            KeyStore.PrivateKeyEntry(identity.privateKey, identity.chain.toTypedArray()),
            protection,
        )
        return imported() ?: error("The Keystore did not keep the certificate")
    }

    fun remove() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    /** Off by default: it is the one part of signing that uses the network. */
    var timestampsOn: Boolean
        get() = prefs.getBoolean(KEY_TIMESTAMPS, false)
        set(value) = prefs.edit().putBoolean(KEY_TIMESTAMPS, value).apply()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "freepdf-imported"
        const val KEY_TIMESTAMPS = "timestamps"
    }
}

/** What the certificate screen shows about an imported identity. */
data class CertificateInfo(val name: String, val issuer: String, val expires: Date)

fun SigningIdentity.info() = CertificateInfo(name, issuer ?: name, expires)
