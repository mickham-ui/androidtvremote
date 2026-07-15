package com.out.androidtvremote.services

import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.os.PowerManager.WakeLock
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.out.androidtvremote.services.notification.PlayingNotification
import com.out.androidtvremote.services.notification.PlayingNotificationImpl24
import com.out.androidtvremote.utils.Constant
import com.out.androidtvremote.utils.Constant.isPlaying
import com.out.androidtvremote.utils.VersionUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main

class CastService : Service() {

    @JvmField
    var pendingQuit = false
    private val serviceScope = CoroutineScope(Job() + Main)

    private val mediaSession by lazy { MediaSessionCompat(this, TAG) }
    private var playingNotification: PlayingNotification? = null
    private var uiThreadHandler: Handler? = null
    private var wakeLock: WakeLock? = null
    private var notificationManager: NotificationManager? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
        )
        uiThreadHandler = Handler(Looper.getMainLooper())
        notificationManager = getSystemService()
        initNotification()
    }

    override fun onDestroy() {
        quit()
        serviceScope.cancel()
        wakeLock?.release()
    }

    override fun onBind(p0: Intent?): IBinder? = null

    private fun initNotification() {
        Log.d(TAG, "initNotification: ")
        playingNotification = PlayingNotificationImpl24.from(this, notificationManager!!, mediaSession)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action != null) {
            val action = intent.action!!
            serviceScope.launch {
                when (action) {
                    ACTION_START_CASTING -> {
                        Log.d(TAG, "onStartCommand: $ACTION_START_CASTING")
                        isPlaying.postValue(true)
                        val mediaItem = Constant.currentCastMediaItem.value
                        if (mediaItem != null) {
                            playingNotification?.updateMetadata(mediaItem) {
                                startForegroundOrNotify()
                            }
                        } else {
                            startForegroundOrNotify()
                        }
                    }

                    ACTION_TOGGLE_PAUSE -> {
                        if (isPlaying.value == true) {
                            Log.d(TAG, "onStartCommand: toggle → pause")
                            pause()
                        } else {
                            Log.d(TAG, "onStartCommand: toggle → play")
                            play()
                        }
                    }

                    ACTION_PAUSE -> {
                        Log.d(TAG, "onStartCommand: $ACTION_PAUSE")
                        pause()
                    }

                    ACTION_PLAY -> {
                        Log.d(TAG, "onStartCommand: $ACTION_PLAY")
                        play()
                    }

                    ACTION_STOP, ACTION_QUIT -> {
                        Log.d(TAG, "onStartCommand: $ACTION_STOP")
                        // Stop casting on TV as well
                        Constant.connectableDevice?.mediaControl?.stop(null)
                        pendingQuit = false
                        quit()
                    }
                }
            }
        }
        return START_STICKY
    }

    fun pause(force: Boolean = false) {
        Log.d(TAG, "pause: ")
        isPlaying.postValue(false)
        Constant.connectableDevice?.mediaControl?.pause(null)
        handleChangeInternal(PLAY_STATE_CHANGED)
    }

    fun play() {
        Log.d(TAG, "play: ")
        isPlaying.postValue(true)
        Constant.connectableDevice?.mediaControl?.play(null)
        handleChangeInternal(PLAY_STATE_CHANGED)
    }

    private fun handleChangeInternal(what: String) {
        when (what) {
            PLAY_STATE_CHANGED -> {
                Log.d(TAG, "handleChangeInternal: $PLAY_STATE_CHANGED")
                val nowPlaying = isPlaying.value == true
                playingNotification?.setPlaying(nowPlaying)
                val mediaItem = Constant.currentCastMediaItem.value
                if (mediaItem != null) {
                    playingNotification?.updateMetadata(mediaItem) {
                        startForegroundOrNotify()
                    }
                } else {
                    startForegroundOrNotify()
                }
            }

            META_CHANGED -> {
                val mediaItem = Constant.currentCastMediaItem.value
                if (mediaItem != null) {
                    playingNotification?.updateMetadata(mediaItem) {
                        startForegroundOrNotify()
                    }
                }
            }
        }
    }

    fun quit() {
        Constant.isCasting.postValue(false)
        isPlaying.postValue(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForeground = false
        notificationManager?.cancel(PlayingNotification.NOTIFICATION_ID)
        stopForegroundAndNotification()
        stopSelf()
    }

    private fun startForegroundOrNotify() {
        if (playingNotification != null) {
            if (isForeground) {
                if (!VersionUtils.hasS()) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    isForeground = false
                }
            }
            if (!isForeground) {
                try {
                    if (VersionUtils.hasQ()) {
                        startForeground(
                            PlayingNotification.NOTIFICATION_ID,
                            playingNotification!!.build(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(
                            PlayingNotification.NOTIFICATION_ID,
                            playingNotification!!.build()
                        )
                    }
                    isForeground = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start CastService in foreground: ${e.message}", e)
                    stopSelf()
                }
            } else {
                notificationManager?.notify(
                    PlayingNotification.NOTIFICATION_ID,
                    playingNotification!!.build()
                )
            }
        }
    }

    private fun stopForegroundAndNotification() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager?.cancel(PlayingNotification.NOTIFICATION_ID)
        isForeground = false
    }

    companion object {
        val TAG: String = CastService::class.java.simpleName
        const val FIRE_PACKAGE_NAME = "com.out.androidtvremote"
        const val ACTION_START_CASTING = "$FIRE_PACKAGE_NAME.startcasting"
        const val ACTION_TOGGLE_PAUSE = "$FIRE_PACKAGE_NAME.togglepause"
        const val ACTION_PLAY = "$FIRE_PACKAGE_NAME.play"
        const val ACTION_PAUSE = "$FIRE_PACKAGE_NAME.pause"
        const val ACTION_STOP = "$FIRE_PACKAGE_NAME.stop"
        const val ACTION_QUIT = "$FIRE_PACKAGE_NAME.quitservice"
        const val PLAY_STATE_CHANGED = "$FIRE_PACKAGE_NAME.playstatechanged"
        const val META_CHANGED = "$FIRE_PACKAGE_NAME.metachanged"
    }
}
