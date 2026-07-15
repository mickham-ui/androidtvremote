package com.out.androidtvremote.utils

import androidx.lifecycle.MutableLiveData
import com.connectsdk.device.ConnectableDevice
import com.out.androidtvremote.ui.frags.cast.data.MediaItem
import java.util.ArrayList

object Constant {
    var deviceIP: String? = ""
    var connectableDevice: ConnectableDevice? = null
    var isConnected = MutableLiveData<Boolean>(false)
    val remoteConnected = MutableLiveData<Boolean>(false)
    var connectedDeviceName = MutableLiveData<String>("FireTV Remote")
    const val FT_CONNECTED_DEVICE_IP = "FT_CONNECTED_DEVICE_IP"
    const val FT_CONNECTED_DEVICE_TOKEN = "FT_CONNECTED_DEVICE_TOKEN"
    const val PORT = 8016
    var castMediaList : ArrayList<MediaItem> = arrayListOf()
    var currentCastMediaItem  = MutableLiveData<MediaItem>()
    var castDescription = "Casting from ${getDeviceName()}"
    var castItemTitle = MutableLiveData<String>()
    var isCasting = MutableLiveData<Boolean>(false)
    var isPlaying = MutableLiveData<Boolean>(false)
}
