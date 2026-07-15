package com.out.androidtvremote.ui.activities.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.discovery.DiscoveryManager
import com.connectsdk.discovery.DiscoveryManagerListener
import com.connectsdk.discovery.CapabilityFilter
import com.connectsdk.service.capability.MediaPlayer
import com.connectsdk.service.command.ServiceCommandError
import java.util.ArrayList
import java.util.LinkedHashMap


class SearchDeviceViewModel : ViewModel() {

    private val _connectableDevices = MutableLiveData<ArrayList<ConnectableDevice>>()
    val connectableDevices: LiveData<ArrayList<ConnectableDevice>> get() = _connectableDevices


    private val _connectionState = MutableLiveData<ConnectableDevice>()
    val connectionState: LiveData<ConnectableDevice>
        get() = _connectionState

    private val _updateProgressbar = MutableLiveData<Boolean>()
    val updateProgressbar: LiveData<Boolean>
        get() = _updateProgressbar

    // Example method for updating connectable devices
    fun updateConnectableDevices(devices: ArrayList<ConnectableDevice>) {
        _connectableDevices.value = devices
    }


    fun updateConnectionState(device: ConnectableDevice) {
        _connectionState.value = device
    }

    fun updateProgressbarState(value: Boolean){
        _updateProgressbar.value = value

    }

}

