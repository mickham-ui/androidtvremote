package com.out.androidtvremote

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.net.wifi.WifiManager
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import com.connectsdk.discovery.DiscoveryManager
import com.connectsdk.discovery.provider.CastDiscoveryProvider
import com.connectsdk.service.CastService
import com.out.androidtvremote.androidLib.AndroidRemoteTv
import com.out.androidtvremote.utils.Constant
import com.out.androidtvremote.utils.WebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale


class App : Application() {
    var TAG = "ATV${App::class.java.simpleName}"
    var discoveryManager: DiscoveryManager? = null
    var androidRemoteTv: AndroidRemoteTv? = null
        get() = field
        set(value) {
            field = value
        }

    // IME state
    var lastImeText: String = ""
    var lastFieldCounter: Int = -1
    var onImeShowCallback: ((String, Int) -> Unit)? = null


    companion object {
        var app: App? = null
        var remainingTaps : Int = 20
        var previousTime: Long = 0
        var currentTime: Long = 0
    }
    private var webServer: WebServer? = null
    private var webServerIp: String? = null

    lateinit var sharedPreferences: SharedPreferences


    override fun onCreate() {
        super.onCreate()
        app = this

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // ── Theme Application ──────────────────────────────────
        val defaultPrefs = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)
        val themePref = defaultPrefs.getString("app_theme", "system")
        when (themePref) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        
        // Initialize DiscoveryManager on background thread to optimize startup
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            DiscoveryManager.init(applicationContext)
            discoveryManagerSetup()
        }
    }



    fun discoveryManagerSetup() {
        Log.d(TAG, "connectSDKInit: ")
        discoveryManager = DiscoveryManager.getInstance()
        discoveryManager?.registerDeviceService(CastService::class.java, CastDiscoveryProvider::class.java)
        discoveryManager?.start()
    }

    fun getOrCreateAndroidRemoteTv(): AndroidRemoteTv {
        if (androidRemoteTv == null) {
            androidRemoteTv = AndroidRemoteTv()
        }
        return androidRemoteTv!!
    }

    fun ensureLocalMediaServer(): String? {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            return null
        }
        val ip = getIPAddress(true)
        if (ip.isNullOrBlank()) {
            return null
        }
        if (webServer != null && webServerIp == ip) {
            return webServerIp
        }
        try {
            webServer?.stop()
        } catch (_: Exception) {
        }
        return try {
            val root = Environment.getExternalStorageDirectory()
            webServer = WebServer(ip, Constant.PORT, root)
            webServer?.start()
            webServerIp = ip
            Log.d(TAG, "Local media server started on $ip:${Constant.PORT}")
            webServerIp
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start local media server: ${e.message}")
            null
        }
    }

    fun stopLocalMediaServer() {
        try {
            webServer?.stop()
        } catch (_: Exception) {
        } finally {
            webServer = null
            webServerIp = null
        }
    }

    private fun getIPAddress(useIPv4: Boolean): String? {
        return try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) return sAddr
                        } else {
                            if (!isIPv4) {
                                val delim = sAddr.indexOf('%')
                                return if (delim < 0) sAddr.uppercase(Locale.getDefault()) else sAddr.substring(
                                    0,
                                    delim
                                ).uppercase(Locale.getDefault())
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
