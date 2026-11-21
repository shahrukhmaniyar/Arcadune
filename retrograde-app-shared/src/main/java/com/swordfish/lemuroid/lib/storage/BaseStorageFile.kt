package com.swordfish.lemuroid.lib.storage

import android.net.Uri
import com.swordfish.lemuroid.common.files.FileUtils

data class BaseStorageFile(
    val name: String,
    val size: Long,
    val uri: Uri,
    val path: String? = null,
) {
    val extension: String
        get() = FileUtils.extractExtension(name)

    val extensionlessName: String
        get() = name.substringBeforeLast('.', "")
}
