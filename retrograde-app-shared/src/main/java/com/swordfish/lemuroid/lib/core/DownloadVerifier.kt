package com.swordfish.lemuroid.lib.core

import com.swordfish.lemuroid.lib.library.CoreID
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** The downloaded file is not the one Arcadune published, so it is never loaded. */
class UntrustedDownloadException(message: String) : IOException(message)

/**
 * Checks downloaded emulator cores and assets against the SHA-256 fingerprints built into the app
 * ([CoreFingerprints]). A core is native code that runs with all of Arcadune's rights, so a file
 * that was changed on the server or on the way is refused instead of loaded.
 */
object DownloadVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun expectedCoreFingerprint(
        abi: String,
        coreID: CoreID,
    ): String? = CoreFingerprints.CORES["${abi}__${coreID.libretroFileName}"]

    fun verifyCore(
        file: File,
        abi: String,
        coreID: CoreID,
    ) {
        val expected =
            expectedCoreFingerprint(abi, coreID)
                ?: throw UntrustedDownloadException("No fingerprint is known for ${coreID.coreName} ($abi)")
        verify(file, expected, coreID.coreName)
    }

    fun verify(
        file: File,
        expectedSha256: String,
        label: String,
    ) {
        val actual = sha256(file)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw UntrustedDownloadException("$label does not match its published fingerprint")
        }
    }
}
