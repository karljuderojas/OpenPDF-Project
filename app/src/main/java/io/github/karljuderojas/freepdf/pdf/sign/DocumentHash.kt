package io.github.karljuderojas.freepdf.pdf.sign

import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 fingerprints, printed on the audit page so anyone can check a file is unchanged. */
object DocumentHash {

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun sha256(bytes: ByteArray): String = sha256(bytes.inputStream())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
