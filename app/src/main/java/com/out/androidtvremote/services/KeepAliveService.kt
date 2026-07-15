package com.out.androidtvremote.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.out.androidtvremote.R
import com.out.androidtvremote.ui.activities.MainActivity
import com.out.androidtvremote.utils.Constant

/**
 * A lightweight foreground service that keeps the app alive during phone sleep.
 *
 * Features:
 * - Holds a PARTIAL_WAKE_LOCK (CPU stays on, screen can turn off) so the Android TV
 *   remote connection thread is not killed when the screen sleeps.
 * - Holds a WIFI_MODE_FULL WifiLock to prevent the Wi-Fi adapter from going into
 *   power-save mode, which would drop the TCP connection to the TV.
 * - Registers a BroadcastReceiver for ACTION_SCREEN_ON so that when the user wakes
 *   the device, the app immediately attempts to re-verify / reconnect to the TV.
 */
class KeepAliveService : Service() {

    companion object {
        const val TAG = "KeepAliveService"
        const val CHANNEL_ID = "keep_alive_channel"
        const val NOTIFICATION_ID = 5001
        const val ACTION_STOP = "com.out.androidtvremote.keepalive.stop"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /** Listens for screen-on events while in the background. */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                Log.d(TAG, "Screen turned ON — triggering connection check")
                onScreenOn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        acquireWifiLock()
        registerScreenReceiver()
        Log.d(TAG, "KeepAliveService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Stop action received — stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (com.out.androidtvremote.utils.VersionUtils.hasQ()) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start KeepAliveService in foreground: ${e.message}", e)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        releaseWifiLock()
        releaseWakeLock()
        Log.d(TAG, "KeepAliveService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AndroidTVRemote::KeepAliveWakeLock"
        ).also { lock ->
            lock.setReferenceCounted(false)
            if (!lock.isHeld) lock.acquire()
        }
        Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock: ${e.message}")
        }
        wakeLock = null
    }

    private fun acquireWifiLock() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "AndroidTVRemote::KeepAliveWifiLock"
        ).also { lock ->
            lock.setReferenceCounted(false)
            if (!lock.isHeld) lock.acquire()
        }
        Log.d(TAG, "WifiLock acquired")
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WifiLock: ${e.message}")
        }
        wifiLock = null
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenOnReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering screen receiver: ${e.message}")
        }
    }

    /**
     * Called when the screen turns back on.
     * Signals MainActivity to attempt a reconnect by posting to remoteConnected.
     * If the remote is already connected this is a no-op because MainActivity
     * checks for an existing connection before reconnecting.
     */
    private fun onScreenOn() {
        if (Constant.isConnected.value != true) {
            Log.d(TAG, "Not connected after screen-on — requesting auto-reconnect")
            // Post true to signal MainActivity/RemoteFragment to trigger reconnect
            Constant.remoteConnected.postValue(false)  // reset first
            // Small delay then post true so observers fire
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Constant.remoteConnected.postValue(true)
            }, 300)
        } else {
            Log.d(TAG, "Already connected — refreshing volume after screen-on")
            // Refresh volume level in case it got stale
            Constant.connectableDevice?.volumeControl?.getVolume(
                object : com.connectsdk.service.capability.VolumeControl.VolumeListener {
                    override fun onSuccess(volume: Float?) {
                        // Volume refresh is handled by the existing observeRealVolumeFromTv() callback
                        Log.d(TAG, "Volume refreshed after screen-on: $volume")
                    }
                    override fun onError(error: com.connectsdk.service.command.ServiceCommandError?) {
                        Log.w(TAG, "Could not refresh volume after screen-on")
                    }
                }
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Keep-Alive",
                NotificationManager.IMPORTANCE_MIN          // silent, no heads-up
            ).apply {
                description = "Keeps the TV remote connection alive in the background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, KeepAliveService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Remote Active")
            .setContentText("Connection is maintained in the background")
            .setSmallIcon(R.drawable.ic_tv_connected)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)
            .addAction(0, "Disconnect", stopPending)
            .build()
    }
}
