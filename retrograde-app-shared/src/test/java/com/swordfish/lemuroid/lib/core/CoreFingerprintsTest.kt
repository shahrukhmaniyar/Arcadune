package com.swordfish.lemuroid.lib.core

import com.swordfish.lemuroid.lib.core.assetsmanager.PPSSPPAssetsManager
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoreFingerprintsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun everyCoreHasAFingerprintForEveryPhoneType() {
        // Some cores only exist for some phone types (3DS cores are 64-bit only).
        val missing =
            GameSystem.all()
                .flatMap { it.systemCoreConfigs }
                .flatMap { config ->
                    val abis = config.supportedOnlyArchitectures ?: CoreLibraryLocator.HOSTED_ABIS
                    abis.filter { it in CoreLibraryLocator.HOSTED_ABIS }.map { abi -> abi to config.coreID }
                }
                .distinct()
                .filter { (abi, core) -> DownloadVerifier.expectedCoreFingerprint(abi, core) == null }
                .map { (abi, core) -> "$abi ${core.coreName}" }
        assertTrue("No core was checked", CoreID.entries.isNotEmpty())
        assertTrue(
            "Cores without a fingerprint (run scripts/update-core-fingerprints.ps1): $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun fingerprintsMatchTheReleasesTheAppDownloadsFrom() {
        assertEquals("cores-${CoreLibraryLocator.CORES_VERSION}", CoreFingerprints.CORES_RELEASE)
        assertEquals("psp-files-${PPSSPPAssetsManager.PPSSPP_ASSETS_VERSION}", CoreFingerprints.PPSSPP_ASSETS_RELEASE)
        CoreFingerprints.CORES.values.forEach { assertTrue(it, Regex("[0-9a-f]{64}").matches(it)) }
    }

    @Test
    fun aChangedFileIsRefused() {
        val file = temp.newFile("core.so").apply { writeText("not the published core") }
        try {
            DownloadVerifier.verify(file, "0".repeat(64), "test core")
            fail("a file that does not match its fingerprint must be refused")
        } catch (expected: UntrustedDownloadException) {
            // expected
        }
    }

    @Test
    fun theRightFileIsAccepted() {
        val file = temp.newFile("core.so").apply { writeText("abc") }
        // SHA-256 of "abc".
        DownloadVerifier.verify(file, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "test core")
    }
}
