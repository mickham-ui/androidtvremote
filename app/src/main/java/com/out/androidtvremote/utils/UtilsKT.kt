package com.out.androidtvremote.utils
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.net.NetworkInfo
import java.io.File




fun getVideoThumbnail(path: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.getFrameAtTime(0) // Get the first frame as a Bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null // Handle errors
    } finally {
        retriever.release()
    }
}



fun getAudioImage(audio: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(audio)
    val artistImageBytes = retriever.embeddedPicture
    retriever.release()

    return if (artistImageBytes != null) {
        // If artist image is available in metadata, decode and return
        BitmapFactory.decodeByteArray(artistImageBytes, 0, artistImageBytes.size)
    } else {
        // If no artist image is available, return a dummy image
        null
    }
}

fun getDeviceName(): String {
    val manufacturer = Build.MANUFACTURER.capitalize()
    val model = Build.MODEL

    return if (model.startsWith(manufacturer, ignoreCase = true)) {
        model
    } else {
        "$manufacturer $model"
    }
}


fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000 % 60
    val minutes = milliseconds / (1000 * 60) % 60
    val hours = milliseconds / (1000 * 60 * 60)

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

