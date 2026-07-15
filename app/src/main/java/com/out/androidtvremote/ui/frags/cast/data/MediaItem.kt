package com.out.androidtvremote.ui.frags.cast.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class MediaItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val path: String,
    val uri: Uri,
    val mimeType: String,
    val size: Long,
    val duration: Long,
    val resolution: String? = null,
    val dateTaken: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val bucketId: String,
    val bucketName: String,
    val orientation: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val artist: String? = null,
    val album: String? = null,
    val thumbnailUri: Uri? = null,
    val isSelected: Boolean = false
) : Parcelable {
    val fileExtension: String
        get() = File(path).extension

    val fileSizeInMB: Double
        get() = size.toDouble() / (1024 * 1024)

    val thumbnailPath: String?
        get() = thumbnailUri?.path
}
