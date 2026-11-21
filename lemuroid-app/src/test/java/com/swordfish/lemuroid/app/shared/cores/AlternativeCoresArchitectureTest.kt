package com.swordfish.lemuroid.app.shared.cores

import com.swordfish.lemuroid.app.shared.achievements.HardcorePolicy
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsConsoleMap
import com.swordfish.lemuroid.lib.core.CoreLibraryLocator
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AlternativeCoresArchitectureTest {
    @Test
    fun defaultCoresStayDefaultAndBundled_whileAll23NewAlternativesAreDownloadOnly() {
        val allCores = CoreID.values().toList()
        assertEquals(52, allCores.size)

        val bundledDefaults = allCores.filter { it.isBundledByDefault }
        val downloadOnlyAlternates = allCores.filter { !it.isBundledByDefault }

        assertEquals(29, bundledDefaults.size)
        assertEquals(23, downloadOnlyAlternates.size)
        assertTrue(CoreID.ARMGBA in downloadOnlyAlternates)

        // Every single GameSystem's default (.first()) core MUST be one of the 29 bundled default cores.
        for (system in GameSystem.all()) {
            val defaultConfig = system.systemCoreConfigs.first()
            assertTrue(
                "Default core ${defaultConfig.coreID} for ${system.id} must be bundled by default",
                defaultConfig.coreID.isBundledByDefault,
            )
            val coreIds = system.systemCoreConfigs.map { it.coreID }
            assertEquals(
                "Duplicate CoreID in ${system.id}",
                coreIds.size,
                coreIds.toSet().size,
            )
        }

        // GBA must keep mGBA as default and offer armGBA + gpSP + VBA-M + Beetle GBA as alternates.
        val gbaCores = GameSystem.findById(SystemID.GBA.dbname).systemCoreConfigs.map { it.coreID }
        assertEquals(
            listOf(CoreID.MGBA, CoreID.ARMGBA, CoreID.GPSP, CoreID.VBAM, CoreID.MEDNAFEN_GBA),
            gbaCores,
        )

        // Every CoreID must belong to at least one GameSystem and declare FOSS license + upstream URL.
        for (core in allCores) {
            assertTrue(
                "Orphan CoreID $core is not attached to any GameSystem",
                GameSystem.findSystemForCore(core).isNotEmpty(),
            )
            assertTrue("Missing license on $core", core.license.isNotBlank())
            assertTrue("Missing HTTPS upstreamUrl on $core", core.upstreamUrl.startsWith("https://"))
        }
    }

    @Test
    fun allCoreDownloadUrlsPointStrictlyToUserGithubServer_zeroLemuroidDependency() {
        for (core in CoreID.values()) {
            for (abi in listOf("arm64-v8a", "armeabi-v7a")) {
                val url = CoreLibraryLocator.buildDownloadUrlString(abi, core)
                val expectedBase = "https://github.com/shahrukhmaniyar/Arcadune/releases/download/cores-2"
                assertEquals(
                    "$expectedBase/${abi}__${core.libretroFileName}",
                    url,
                )
                assertFalse(url.lowercase().contains("swordfish90"))
                assertFalse(url.lowercase().contains("lemuroid"))
            }
            val libName = RetroAchievementsConsoleMap.libretroLibraryName(core)
            assertTrue("Blank library name for $core", libName.isNotBlank())
        }
    }

    @Test
    fun readOnlyEnforcementAndSafeDeletionWorkForDownloadedSharedLibraries() {
        val tempDir = Files.createTempDirectory("arcadune_cores_test").toFile()
        try {
            val fakeElf = tempDir.resolve("libarmgba_libretro_android.so")
            fakeElf.writeBytes(
                byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + ByteArray(2048),
            )
            assertTrue(CoreLibraryLocator.isValidElfSharedLibrary(fakeElf))
            CoreLibraryLocator.ensureReadOnlyForDynamicLoad(fakeElf)
            assertFalse("Downloaded .so must not remain writable on Android 14+", fakeElf.canWrite())

            val htmlErrorPage = tempDir.resolve("libbroken_libretro_android.so")
            htmlErrorPage.writeText("<html><body>502 Bad Gateway</body></html>")
            assertFalse(CoreLibraryLocator.isValidElfSharedLibrary(htmlErrorPage))

            assertTrue(fakeElf.setWritable(true, true))
            assertTrue(fakeElf.delete())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun hardcorePolicyGatesNonCompliantAlternatesAndEnforcesOptionsAcrossAllCompliantAlternates() {
        // 0-RAM and unvetted alternate cores MUST be gated off for Hardcore with clear user guidance:
        val gatedAlternates =
            listOf(
                CoreID.ARMGBA to SystemID.GBA,
                CoreID.GPSP to SystemID.GBA,
                CoreID.BSNES to SystemID.SNES,
                CoreID.RACE to SystemID.NGP,
                CoreID.BLASTEM to SystemID.GENESIS,
                CoreID.PARALLEL_N64 to SystemID.N64,
                CoreID.AZAHAR to SystemID.NINTENDO_3DS,
            )
        for ((core, system) in gatedAlternates) {
            assertFalse(
                "Expected $core on $system to be gated for Hardcore",
                RetroAchievementsConsoleMap.isCoreHardcoreCompliant(core, system),
            )
            assertNotNull(RetroAchievementsConsoleMap.hardcoreUnsupportedReason(core, system))
        }

        // Native C++ mirror still blocks armGBA cheat/underclock options at defense-in-depth layer:
        assertTrue(HardcorePolicy.isCoreOptionAllowedMirror("armGBA", 5, "armgba_cpu_clock", "100"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("armGBA", 5, "armgba_cpu_clock", "50"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("armGBA", 5, "armgba_action_replay_game", "pokemon"))
        assertFalse(HardcorePolicy.isCoreOptionAllowedMirror("armGBA", 4, "armgba_game_genie_code_1", "ABC-123"))

        // Vet Hardcore-enabled alternate cores and their option rules:
        val enabledAlternates =
            listOf(
                CoreID.VBAM to SystemID.GBA,
                CoreID.MEDNAFEN_GBA to SystemID.GBA,
                CoreID.SAMEBOY to SystemID.GBC,
                CoreID.GEARBOY to SystemID.GB,
                CoreID.MESEN to SystemID.NES,
                CoreID.NESTOPIA to SystemID.NES,
                CoreID.QUICKNES to SystemID.NES,
                CoreID.MEDNAFEN_SUPAFAUST to SystemID.SNES,
                CoreID.GEARSYSTEM to SystemID.SMS,
                CoreID.SWANSTATION to SystemID.PSX,
                CoreID.MEDNAFEN_PSX to SystemID.PSX,
                CoreID.DESMUME to SystemID.NDS,
                CoreID.MEDNAFEN_SATURN to SystemID.SATURN,
                CoreID.MEDNAFEN_PCE to SystemID.PC_ENGINE,
                CoreID.GEARLYNX to SystemID.LYNX,
                CoreID.STELLA2014 to SystemID.ATARI2600,
            )
        for ((core, system) in enabledAlternates) {
            assertTrue(
                "Expected $core on $system to support Hardcore",
                RetroAchievementsConsoleMap.isCoreHardcoreCompliant(core, system),
            )
            assertNull(RetroAchievementsConsoleMap.hardcoreUnsupportedReason(core, system))
        }

        assertFalse(
            HardcorePolicy.isCoreOptionValueAllowed(
                CoreID.SWANSTATION,
                SystemID.PSX,
                "swanstation_CPU_Overclock",
                "75",
            ),
        )
        assertFalse(
            HardcorePolicy.isCoreOptionValueAllowed(
                CoreID.SWANSTATION,
                SystemID.PSX,
                "swanstation_Console_Region",
                "PAL",
            ),
        )
        assertFalse(
            HardcorePolicy.isCoreOptionValueAllowed(
                CoreID.SWANSTATION,
                SystemID.PSX,
                "swanstation_Console_Enable8MBRAM",
                "true",
            ),
        )
        assertTrue(
            HardcorePolicy.isCoreOptionValueAllowed(
                CoreID.SWANSTATION,
                SystemID.PSX,
                "swanstation_CPU_Overclock",
                "100",
            ),
        )
        assertFalse(
            HardcorePolicy.isCoreOptionValueAllowed(
                CoreID.MEDNAFEN_SATURN,
                SystemID.SATURN,
                "beetle_saturn_cart",
                "Action Replay ROM",
            ),
        )
        assertFalse(
            HardcorePolicy.isCoreOptionValueAllowed(
                CoreID.MEDNAFEN_PCE,
                SystemID.PC_ENGINE,
                "pce_disable_softreset",
                "disabled",
            ),
        )
        assertFalse(
            HardcorePolicy.isCoreOptionValueAllowed(CoreID.FBNEO, SystemID.FBNEO, "fbneo-cpu-speed-adjust", "75%"),
        )
        assertTrue(
            HardcorePolicy.isCoreOptionValueAllowed(CoreID.FBNEO, SystemID.FBNEO, "fbneo-cpu-speed-adjust", "100%"),
        )
    }
}
