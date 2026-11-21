package com.swordfish.lemuroid.lib.core

import android.content.Context
import android.net.Uri
import android.os.Build
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import java.io.File
import java.io.RandomAccessFile

/**
 * Authoritative locator and GitHub release URL builder for all Arcadune emulation cores.
 *
 * - Default cores (`CoreID.isBundledByDefault == true`) are packaged inside the Full (`freeBundle`) APK's
 *   `nativeLibraryDir` and also hosted on Arcadune's own GitHub server for Lite (`freeDynamic`) builds.
 * - Alternative cores (`CoreID.isBundledByDefault == false`) are never packaged inside the APK and are
 *   downloaded on demand from Arcadune's GitHub server into `filesDir/cores/<CORES_VERSION>/`.
 */
object CoreLibraryLocator {
    const val CORES_VERSION = "2"
    const val GITHUB_CORES_BASE_URL = "https://github.com/shahrukhmaniyar/Arcadune/"
    const val MIN_VALID_ELF_BYTES = 1024L
    val HOSTED_ABIS: Set<String> = setOf("arm64-v8a", "armeabi-v7a")

    fun isDeviceAbiHosted(supportedAbis: Array<String> = Build.SUPPORTED_ABIS): Boolean =
        supportedAbis.any { it in HOSTED_ABIS }

    fun selectSupportedAbi(supportedAbis: Array<String> = Build.SUPPORTED_ABIS): String =
        supportedAbis.firstOrNull { it in HOSTED_ABIS }
            ?: supportedAbis.firstOrNull()
            ?: "arm64-v8a"

    fun buildDownloadUrlString(
        abi: String,
        coreID: CoreID,
        coresVersion: String = CORES_VERSION,
    ): String = "${GITHUB_CORES_BASE_URL}releases/download/cores-$coresVersion/${abi}__${coreID.libretroFileName}"

    fun buildDownloadUri(
        abi: String,
        coreID: CoreID,
        coresVersion: String = CORES_VERSION,
    ): Uri = Uri.parse(buildDownloadUrlString(abi, coreID, coresVersion))

    fun getVersionedCoresDirectory(directoriesManager: DirectoriesManager): File =
        File(directoriesManager.getCoresDirectory(), CORES_VERSION)

    fun getDownloadedCoreFile(
        directoriesManager: DirectoriesManager,
        coreID: CoreID,
    ): File = File(getVersionedCoresDirectory(directoriesManager), coreID.libretroFileName)

    fun isValidElfSharedLibrary(file: File): Boolean {
        if (!file.isFile || file.length() <= MIN_VALID_ELF_BYTES) {
            return false
        }
        return runCatching {
            val header = ByteArray(4)
            val read = file.inputStream().use { it.read(header) }
            read == 4 &&
                header[0] == 0x7F.toByte() &&
                header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() &&
                header[3] == 'F'.code.toByte()
        }.getOrDefault(false)
    }

    fun <T> withCrossProcessCoreLock(
        directoriesManager: DirectoriesManager,
        block: () -> T,
    ): T {
        val mainDir = directoriesManager.getCoresDirectory().apply { mkdirs() }
        val lockFile = File(mainDir, ".cores_io.lock")
        return RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use {
                block()
            }
        }
    }

    fun deleteOutdatedCores(
        directoriesManager: DirectoriesManager,
        currentCoresVersion: String = CORES_VERSION,
    ) {
        val mainCoresDirectory = directoriesManager.getCoresDirectory()
        mainCoresDirectory.listFiles()
            ?.filter { it.name != currentCoresVersion && !it.name.startsWith(".") }
            ?.forEach { staleDir ->
                staleDir.walkBottomUp().forEach { entry ->
                    runCatching { entry.setWritable(true, true) }
                }
                staleDir.deleteRecursively()
            }
    }

    fun findBundledLibrary(
        context: Context,
        coreID: CoreID,
    ): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir ?: return null)
        val directFile = File(nativeDir, coreID.libretroFileName)
        if (directFile.isFile && directFile.length() > MIN_VALID_ELF_BYTES) {
            return directFile
        }
        return nativeDir
            .walkBottomUp()
            .firstOrNull { it.isFile && it.name == coreID.libretroFileName && it.length() > MIN_VALID_ELF_BYTES }
    }

    fun findDownloadedLibrary(
        directoriesManager: DirectoriesManager,
        coreID: CoreID,
    ): File? {
        val direct = getDownloadedCoreFile(directoriesManager, coreID)
        if (direct.isFile && direct.length() > MIN_VALID_ELF_BYTES) {
            return ensureReadOnlyForDynamicLoad(direct)
        }
        return null
    }

    fun findAvailableLibrary(
        context: Context,
        directoriesManager: DirectoriesManager,
        coreID: CoreID,
    ): File? = findBundledLibrary(context, coreID) ?: findDownloadedLibrary(directoriesManager, coreID)

    /**
     * Android 14+ (API 34+) rejects `dlopen` on dynamically downloaded `.so` files stored in app data
     * unless the file is marked non-writable (`setReadOnly`).
     */
    fun ensureReadOnlyForDynamicLoad(file: File): File {
        runCatching {
            file.setReadable(true, false)
            file.setWritable(false, false)
        }
        return file
    }

    fun deleteDownloadedCore(
        directoriesManager: DirectoriesManager,
        coreID: CoreID,
    ): Boolean =
        withCrossProcessCoreLock(directoriesManager) {
            deleteOutdatedCores(directoriesManager)
            val file = getDownloadedCoreFile(directoriesManager, coreID)
            if (!file.exists()) return@withCrossProcessCoreLock false
            runCatching { file.setWritable(true, true) }
            file.delete()
        }
}
