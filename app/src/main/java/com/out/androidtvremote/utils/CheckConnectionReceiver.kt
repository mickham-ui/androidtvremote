package com.out.androidtvremote.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

class CheckConnectionReceiver(private val callback: (Boolean) -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            updateUI(context)
        }
    }

    private fun updateUI(context: Context?) {
        if (context!=null){
            val isConnected = isConnected(context)
            callback.invoke(isConnected)
        }
    }

    fun register(context: Context) {
        context.registerReceiver(this, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    fun unregister(context: Context) {
        context.unregisterReceiver(this)
    }

    companion object {
        fun isConnected(context: Context?): Boolean {
            // Check if context is null
            if (context == null) {
                // Handle the case where context is null, maybe log an error
                Log.e("DeviceBottomSheetFragment", "Context is null in isConnected")
                return false // or return default value based on your application logic
            }

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                // Handle the case where getSystemService returns null
                Log.e("DeviceBottomSheetFragment", "ConnectivityManager is null in isConnected")
                return false // or return default value
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val network = connectivityManager.activeNetwork
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                return networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                val networkInfo = connectivityManager.activeNetworkInfo
                return networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI
            }
        }
    }
}