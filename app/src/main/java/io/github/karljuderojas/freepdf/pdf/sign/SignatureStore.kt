package io.github.karljuderojas.freepdf.pdf.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's saved signature and initials, kept only on this device. Each image is stored as a
 * PNG encrypted with AES-GCM under a key that never leaves the Android Keystore, so copying the
 * app's files off the phone does not copy the signature.
 */
class SignatureStore(private val dir: File) {

    enum class Kind { Signature, Initials }

    fun load(kind: Kind): Bitmap? {
        val file = file(kind)
        if (!file.exists()) return null
        val png = decrypt(file.readBytes())
        return BitmapFactory.decodeByteArray(png, 0, png.size)
    }

    fun save(kind: Kind, image: Bitmap) {
        val png = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        dir.mkdirs()
        file(kind).writeBytes(encrypt(png))
    }

    private fun file(kind: Kind) = File(dir, "${kind.name.lowercase()}.bin")

    // Layout: one byte IV length, the IV, then the ciphertext with its GCM tag.
    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun decrypt(stored: ByteArray): ByteArray {
        val ivLength = stored[0].toInt()
        val iv = stored.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(stored, 1 + ivLength, stored.size - 1 - ivLength)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "freepdf-saved-signatures"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
