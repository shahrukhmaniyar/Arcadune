package com.swordfish.lemuroid.lib.storage.local

import android.net.Uri
import java.io.IOException

/** The ROMs folder (or one of its subfolders) could not be read, so a library scan must not trust its result. */
class StorageAccessLostException(
    val folder: Uri,
    cause: Throwable?,
) : IOException("Cannot read the ROMs folder $folder", cause)
