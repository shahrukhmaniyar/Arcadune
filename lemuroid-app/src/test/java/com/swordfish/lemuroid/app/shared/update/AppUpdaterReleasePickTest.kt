package com.swordfish.lemuroid.app.shared.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdaterReleasePickTest {
    private fun release(
        tag: String,
        vararg assets: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String {
        val assetsJson =
            assets.joinToString(",") {
                "{\"name\":\"$it\",\"size\":10,\"digest\":\"sha256:ab\"," +
                    "\"browser_download_url\":\"https://example.invalid/$tag/$it\"}"
            }
        return "{\"tag_name\":\"$tag\",\"name\":\"$tag\",\"body\":\"notes\"," +
            "\"draft\":$draft,\"prerelease\":$prerelease,\"assets\":[$assetsJson]}"
    }

    @Test
    fun aCoresReleaseMarkedLatestDoesNotHideTheAppRelease() {
        // GitHub lists newest first; the cores release is newer than the app release.
        val json =
            "[" +
                listOf(
                    release("cores-3", "arm64-v8a__libmgba.so"),
                    release("v1.1.1", "Arcadune.apk", "Arcadune-full.apk"),
                    release("v1.1.0", "Arcadune.apk", "Arcadune-full.apk"),
                ).joinToString(",") + "]"

        val picked = AppUpdater.newestAppRelease(json, "Arcadune.apk")
        assertEquals("1.1.1", picked?.version)
        assertEquals("https://example.invalid/v1.1.1/Arcadune.apk", picked?.apkUrl)
        assertEquals("ab", picked?.sha256)
    }

    @Test
    fun draftsPreReleasesAndReleasesWithoutTheApkAreSkipped() {
        val json =
            "[" +
                listOf(
                    release("v2.0.0", "Arcadune.apk", draft = true),
                    release("v1.9.0", "Arcadune.apk", prerelease = true),
                    release("v1.5.0", "Arcadune-full.apk"),
                    release("v1.1.10", "Arcadune.apk"),
                    release("v1.1.9", "Arcadune.apk"),
                ).joinToString(",") + "]"

        assertEquals("1.1.10", AppUpdater.newestAppRelease(json, "Arcadune.apk")?.version)
        assertEquals("1.5.0", AppUpdater.newestAppRelease(json, "Arcadune-full.apk")?.version)
    }

    @Test
    fun noAppReleaseAtAll() {
        assertNull(AppUpdater.newestAppRelease("[" + release("cores-2", "x.so") + "]", "Arcadune.apk"))
    }
}
