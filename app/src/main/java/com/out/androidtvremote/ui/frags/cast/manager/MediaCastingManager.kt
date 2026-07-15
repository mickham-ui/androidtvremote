package com.out.androidtvremote.ui.frags.cast.manager

import android.util.Log
import com.connectsdk.core.MediaInfo
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.service.capability.MediaPlayer
import com.connectsdk.service.command.ServiceCommandError

class MediaCastingManager(
    private val connectableDevice: ConnectableDevice
) {
    val TAG = MediaCastingManager::class.simpleName

    fun castImage(mediaUrl: String, title: String, description: String,onSuccess: (String) -> Unit,onError: (String) -> Unit) {
        Log.d(TAG, "castImage: \n mediaUrl - $mediaUrl \n title - $title \n description - $description ")

        val mediaInfo: MediaInfo = MediaInfo.Builder(mediaUrl, "image/jpeg")
            .setTitle(title)
            .setDescription(description)
            .setIcon(mediaUrl)
            .build()

        val listener: MediaPlayer.LaunchListener = object : MediaPlayer.LaunchListener {
            override fun onSuccess(mediaLaunchObject: MediaPlayer.MediaLaunchObject) {
                 Log.d(TAG, "onSuccess: ")
                onSuccess.invoke(mediaLaunchObject.toString())
            }

            override fun onError(error: ServiceCommandError) {
                Log.d(TAG, "Display photo failure: $error")
                onError.invoke(error.message ?: "Unknown cast error")
            }
        }
        connectableDevice.mediaPlayer?.displayImage(mediaInfo, listener)
    }
}
