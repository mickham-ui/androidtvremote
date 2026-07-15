package com.out.androidtvremote.ui.activities

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import coil.load
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.service.capability.MediaControl
import com.connectsdk.service.capability.MediaControl.DurationListener
import com.connectsdk.service.capability.MediaControl.PlayStateListener
import com.connectsdk.service.capability.MediaControl.PositionListener
import com.connectsdk.service.command.ServiceCommandError
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.out.androidtvremote.App
import com.out.androidtvremote.R
import com.out.androidtvremote.databinding.ActivityMainBinding
import com.out.androidtvremote.ui.activities.player.CastPlayerActivity
import com.out.androidtvremote.ui.frags.cast.data.MediaType
import com.out.androidtvremote.utils.Constant
import com.out.androidtvremote.utils.getAudioImage
import com.out.androidtvremote.utils.getVideoThumbnail
import com.out.androidtvremote.androidLib.remote.Remotemessage
import com.out.androidtvremote.services.CastService
import com.out.androidtvremote.services.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class MainActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        var castFragsNumbers = MutableLiveData<String>("0")
    }

    var TAG = "ATV${MainActivity::class.simpleName}"

    private lateinit var binding: ActivityMainBinding

    private lateinit var sharedPreferences: SharedPreferences
    private var app: App? = null
    /** Cached menu reference so we can tint the premium icon later. */
    private var optionsMenu: Menu? = null

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var navController: NavController
    private var mediaType: MediaType? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()




        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }


        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }



        app = application as App
        // Initialize ConnectivityManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)



        setSupportActionBar(binding.mbToolbar)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment
        // Get the NavController for Main
        navController = navHostFragment.navController
        val navInflater = navController.navInflater
        val navGraph = navInflater.inflate(R.navigation.nav_graph)
        navController.graph = navGraph
        supportActionBar?.setDisplayShowHomeEnabled(false)
        binding.navView.setupWithNavController(navController)
        binding.navView.setOnItemSelectedListener { item ->
            NavigationUI.onNavDestinationSelected(item, navController)
        }


        navController.addOnDestinationChangedListener { _, destination, _ ->
            Timber.tag(TAG).d("Destination %s", destination.label)
            when (destination.id) {
                R.id.videoFragment -> {
                    Timber.tag(TAG).d("Hiding Bottom navigation")
                    showBottomNavigation(false)
                    supportActionBar?.title = "Cast Video"
                    supportActionBar?.setDisplayShowHomeEnabled(true)
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                }

                R.id.audioFragment -> {
                    Timber.tag(TAG).d("Hiding Bottom navigation")
                    showBottomNavigation(false)
                    supportActionBar?.title = "Cast Audio"
                    supportActionBar?.setDisplayShowHomeEnabled(true)
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                }

                R.id.photoFragment -> {
                    Timber.tag(TAG).d("Hiding Bottom navigation")
                    showBottomNavigation(false)
                    supportActionBar?.title = "Cast Photo"
                    supportActionBar?.setDisplayShowHomeEnabled(true)
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                }

                R.id.navigation_home -> {
                    Timber.tag(TAG).d("Showing Bottom navigation")
                    showBottomNavigation(true)
                    if (Constant.isConnected.value == true) {
                        Timber.tag(TAG).d("onCreate: it's connected")
                        supportActionBar?.title = Constant.connectedDeviceName.value
                    } else {
                        Timber.tag(TAG).d("onCreate: it's not connected")
                        supportActionBar?.title = resources.getString(R.string.app_name)
                    }
                    supportActionBar?.setDisplayShowHomeEnabled(false)
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                }


                R.id.navigation_cast -> {
                    Timber.tag(TAG).d("Showing Bottom navigation")
                    showBottomNavigation(true)
                    supportActionBar?.title = "Cast"
                    supportActionBar?.setDisplayShowHomeEnabled(false)
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                }

                R.id.navigation_settings -> {
                    Timber.tag(TAG).d("Showing Bottom navigation")
                    showBottomNavigation(true)
                    supportActionBar?.title = "Settings"
                    supportActionBar?.setDisplayShowHomeEnabled(false)
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)

                }

                else -> {
                    Timber.tag(TAG).d("Showing Bottom navigation")
                    showBottomNavigation(true)
                    supportActionBar?.setDisplayShowHomeEnabled(false)
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                }

            }
            // No ads
        }

        binding.navView.setOnItemReselectedListener {
            // Only do something if there's something in the back stack (only in event details)
            if (navController.currentBackStackEntry != null &&
                (navController.currentDestination?.label == "fragment_apps" && navController.currentDestination?.label == "Settings")
            )
                navController.popBackStack()
        }


        //LiveData
        Constant.isCasting.observe(this@MainActivity) {
            if (it) {
                // Show mini player when casting starts
                binding.llMiniPlayer.visibility = View.VISIBLE
                
                mediaType = MediaType.fromString(
                    Constant.currentCastMediaItem.value?.mimeType?.substringBefore("/")
                )

                Timber.tag(TAG).d("onCreate: miniPlayer mimeType - %s", mediaType)
                binding.miniPlayerTitle.text = Constant.currentCastMediaItem.value?.title

                when (mediaType) {

                    MediaType.VIDEO -> {
                        binding.miniPlayerPlayPauseButton.visibility = View.VISIBLE
                        binding.progressBar.visibility = View.VISIBLE
                        binding.image.load(
                            getVideoThumbnail(
                                Constant.currentCastMediaItem.value?.path ?: ""
                            )
                        ) {
                            crossfade(true)
                            placeholder(R.drawable.ic_cast_video)
                        }
                        updateMediaSliderProgress()
                    }

                    MediaType.IMAGE -> {
                        binding.miniPlayerPlayPauseButton.visibility = View.GONE
                        binding.progressBar.visibility = View.GONE
                        binding.image.load(Constant.currentCastMediaItem.value?.path)

                    }

                    MediaType.AUDIO -> {
                        try {
                            binding.miniPlayerPlayPauseButton.visibility = View.VISIBLE
                            binding.progressBar.visibility = View.VISIBLE
                            val artistImage =
                                getAudioImage(Constant.currentCastMediaItem.value?.path!!)
                            if (artistImage != null) {
                                binding.image.load(artistImage) {
                                    crossfade(true)
                                }
                            } else {
                                binding.image.load(R.drawable.ic_music) {
                                    crossfade(true)
                                }
                            }
                            updateMediaSliderProgress()
                        } catch (e: Exception) {
                            Timber.tag(TAG).e("onBindViewHolder: %s", e.message)
                        }
                    }

                    else -> {
                        Timber.tag(TAG).e("setupUI: Invalid media type ")
                    }
                }


            } else {
                // Hide mini player when casting stops
                binding.llMiniPlayer.visibility = View.GONE
            }
        }


        setupListeners()
        setupObservers()

        // Keep mini-player play/pause icon in sync with the CastService play state.
        Constant.isPlaying.observe(this@MainActivity) { playing ->
            if (Constant.isCasting.value == true) {
                val iconRes = if (playing) R.drawable.ic_pause else R.drawable.ic_play
                binding.miniPlayerPlayPauseButton.setImageResource(iconRes)
            }
        }

    }

    private fun setupObservers() {
        Constant.connectedDeviceName.observe(this) { name ->
            if (navController.currentDestination?.id == R.id.navigation_home) {
                if (Constant.isConnected.value == true) {
                    supportActionBar?.title = name
                }
            }
        }

        Constant.isConnected.observe(this) { isConnected ->
            if (navController.currentDestination?.id == R.id.navigation_home) {
                if (isConnected == true) {
                    supportActionBar?.title = Constant.connectedDeviceName.value
                } else {
                    supportActionBar?.title = resources.getString(R.string.app_name)
                }
            }
            // Refresh menu to update connection icon if needed
            invalidateOptionsMenu()
        }



        // Handle back press with the new API
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Check if the current fragment is the HomeFragment
                if (navController.currentDestination?.id == R.id.navigation_home) { // Replace with your Home fragment ID
                    showExitDialog()
                } else {
                    // Navigate up if not in Home fragment or provide other logic
                    navController.navigateUp()
                }
            }
        })

    }





    private fun setupListeners() {
        binding.llMiniPlayer.setOnClickListener(this)
        binding.miniPlayerPlayPauseButton.setOnClickListener(this)
        binding.actionStop.setOnClickListener(this)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        optionsMenu = menu

        val connectionMenuItem = menu.findItem(R.id.connection)
        Constant.isConnected.observe(this) {
            if (it == true) {
                connectionMenuItem.icon =
                    ContextCompat.getDrawable(this, R.drawable.ic_tv_disconnected)
            } else {
                connectionMenuItem.icon =
                    ContextCompat.getDrawable(this, R.drawable.ic_tv_connected)
            }
            updatePowerMenuIcon(it == true)
        }
        updatePowerMenuIcon(Constant.isConnected.value == true)



        return true
    }

    /** Power icon: green when connected, red when disconnected. */
    private fun updatePowerMenuIcon(isConnected: Boolean) {
        val item = optionsMenu?.findItem(R.id.power) ?: return
        val colorRes = if (isConnected) R.color.power_connected_green else R.color.power_disconnected_red
        item.icon?.mutate()?.setColorFilter(
            ContextCompat.getColor(this, colorRes),
            PorterDuff.Mode.SRC_IN
        )
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.power -> {
                val remote = (application as App).androidRemoteTv
                if (remote != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        remote.sendCommand(
                            Remotemessage.RemoteKeyCode.KEYCODE_POWER,
                            Remotemessage.RemoteDirection.SHORT
                        )
                    }
                } else {
                    android.widget.Toast.makeText(
                        this,
                        "Not connected to TV",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }

            R.id.connection -> {
                val intent = Intent(this, SearchActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_bottom)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun showBottomNavigation(isShow: Boolean) {
        if (isShow) {
            binding.navView.visibility = View.VISIBLE
            if (Constant.isCasting.value == true) {
                binding.llMiniPlayer.visibility = View.VISIBLE
            }
        } else {
            binding.navView.visibility = View.GONE
            binding.llMiniPlayer.visibility = View.GONE

        }
    }

    private var hasAttemptedAutoConnectThisSession = false

    /**
     * Set to true when startKeepAliveService() is called while the app is in the
     * background (lifecycle < STARTED). The service will be started in onStart()
     * instead, which is guaranteed to be called while the app is in the foreground.
     * This prevents ForegroundServiceStartNotAllowedException on Android 12+.
     */
    private var pendingKeepAliveStart = false

    override fun onStart() {
        super.onStart()
        Timber.tag(TAG).d("onStart: ")
        if (!hasAttemptedAutoConnectThisSession) {
            hasAttemptedAutoConnectThisSession = true
            attemptAutoReconnect()
        }
        // If a KeepAlive start was deferred because the app was in the background,
        // start the service now that we are safely in the foreground.
        if (pendingKeepAliveStart && Constant.isConnected.value == true) {
            pendingKeepAliveStart = false
            Timber.tag(TAG).d("onStart: flushing pending KeepAliveService start")
            startKeepAliveServiceNow()
        }
    }

    private var autoConnectDialog: AlertDialog? = null
    private var autoConnectDiscoveryListener: com.connectsdk.discovery.DiscoveryManagerListener? = null
    private var autoConnectTimeoutJob: kotlinx.coroutines.Job? = null
    private var isAutoConnecting = false

    private fun attemptAutoReconnect() {
        Timber.tag(TAG).d("AutoReconnect: Function called")

        if (Constant.isConnected.value == true) {
            Timber.tag(TAG).d("AutoReconnect: Already connected, skipping reconnect")
            return
        }
        
        if (isAutoConnecting) {
            Timber.tag(TAG).d("AutoReconnect: Already in progress, skipping")
            return
        }

        val prefs = getSharedPreferences("android_tv_prefs", Context.MODE_PRIVATE)
        val settingsPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)

        val autoReconnect = settingsPrefs.getBoolean("auto_reconnect", true)
        Timber.tag(TAG).d("AutoReconnect: autoReconnect setting = %s", autoReconnect)

        if (!autoReconnect) {
            Timber.tag(TAG).d("AutoReconnect: Disabled by user in settings")
            return
        }

        val host = prefs.getString("HOST", null)
        val pinVerified = prefs.getBoolean("pin", false)

        Timber.tag(TAG).d("AutoReconnect: Saved host = %s", host)
        Timber.tag(TAG).d("AutoReconnect: Pin verified = %s", pinVerified)

        if (host.isNullOrBlank() || !pinVerified) {
            Timber.tag(TAG).d("AutoReconnect: Host missing or not verified. Aborting.")
            return
        }
        
        isAutoConnecting = true
        
        val deviceName = prefs.getString("DEVICENAME", "Android TV")

        val checkAndConnect: (ConnectableDevice) -> Boolean = { device ->
            if (device.ipAddress == host) {
                Timber.tag(TAG).d("AutoReconnect: Target device found via ConnectSDK!")
                
                // Show connecting dialog ONLY when the device is found in the background
                runOnUiThread {
                    if (!canShowAutoConnectDialog()) return@runOnUiThread
                    autoConnectDialog = MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Auto Connecting")
                        .setMessage("Connecting to $deviceName...")
                        .setCancelable(false)
                        .create()

                    autoConnectDialog?.show()
                }

                connectToDiscoveredDevice(device, host, prefs)
                true
            } else {
                false
            }
        }

        // Check if already discovered
        val discoveryManager = app?.discoveryManager
        var found = false
        discoveryManager?.allDevices?.values?.forEach { device ->
            if (checkAndConnect(device)) {
                found = true
                return@forEach
            }
        }
        
        if (!found) {
            // Need to wait for discovery silently in the background
            autoConnectDiscoveryListener = object : com.connectsdk.discovery.DiscoveryManagerListener {
                override fun onDeviceAdded(manager: com.connectsdk.discovery.DiscoveryManager?, device: ConnectableDevice?) {
                    device?.let {
                        if (checkAndConnect(it)) {
                            cleanupAutoConnectDiscovery()
                        }
                    }
                }
                override fun onDeviceUpdated(manager: com.connectsdk.discovery.DiscoveryManager?, device: ConnectableDevice?) {}
                override fun onDeviceRemoved(manager: com.connectsdk.discovery.DiscoveryManager?, device: ConnectableDevice?) {}
                override fun onDiscoveryFailed(manager: com.connectsdk.discovery.DiscoveryManager?, error: com.connectsdk.service.command.ServiceCommandError?) {}
            }
            discoveryManager?.addListener(autoConnectDiscoveryListener)
            
            // Timeout after 15 seconds silently
            autoConnectTimeoutJob = lifecycleScope.launch {
                kotlinx.coroutines.delay(15000)
                Timber.tag(TAG).d("AutoReconnect: Timeout waiting for discovery silently")
                cleanupAutoConnectDiscovery()
                dismissAutoConnectDialog()
                isAutoConnecting = false
            }
        }
    }

    private fun cleanupAutoConnectDiscovery() {
        autoConnectDiscoveryListener?.let {
            app?.discoveryManager?.removeListener(it)
        }
        autoConnectDiscoveryListener = null
        autoConnectTimeoutJob?.cancel()
        autoConnectTimeoutJob = null
    }

    private fun dismissAutoConnectDialog() {
        if (autoConnectDialog?.isShowing == true) {
            try {
                autoConnectDialog?.dismiss()
            } catch (_: Exception) {
                // Ignore dialog dismiss errors when activity is not running.
            }
        }
        autoConnectDialog = null
    }

    private fun canShowAutoConnectDialog(): Boolean {
        return !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun connectToDiscoveredDevice(device: ConnectableDevice, host: String, prefs: SharedPreferences) {
        val remote = app?.getOrCreateAndroidRemoteTv() ?: return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                remote.reconnect(host, object : com.out.androidtvremote.androidLib.AndroidTvListener {
                    override fun onSessionCreated() { Timber.tag(TAG).d("AutoReconnect: onSessionCreated") }
                    override fun onSecretRequested() {
                        Timber.tag(TAG).d("AutoReconnect: onSecretRequested - pairing lost")
                        lifecycleScope.launch {
                            dismissAutoConnectDialog()
                            isAutoConnecting = false
                            Toast.makeText(this@MainActivity, "Auto-connect failed: Re-pairing required", Toast.LENGTH_SHORT).show()
                        }
                        remote.abort()
                    }
                    override fun onPaired() { Timber.tag(TAG).d("AutoReconnect: onPaired") }
                    override fun onConnectingToRemote() { Timber.tag(TAG).d("AutoReconnect: onConnectingToRemote") }
                    override fun onConnected() {
                        Timber.tag(TAG).d("AutoReconnect: onConnected SUCCESS")
                        app?.androidRemoteTv = remote




                        device.addListener(object : com.connectsdk.device.ConnectableDeviceListener {
                            override fun onDeviceReady(d: ConnectableDevice) {
                                Timber.tag(TAG).d("AutoReconnect: ConnectSDK onDeviceReady")
                                lifecycleScope.launch {
                                    Constant.connectableDevice = d
                                    Constant.isConnected.value = true
                                    val name = prefs.getString("DEVICENAME", null) ?: d.friendlyName
                                    Constant.connectedDeviceName.value = name
                                    dismissAutoConnectDialog()
                                    isAutoConnecting = false
                                    Constant.remoteConnected.value = true
                                    // Start KeepAlive service to maintain connection through sleep
                                    startKeepAliveService()
                                }
                            }
                            override fun onDeviceDisconnected(d: ConnectableDevice) {
                                lifecycleScope.launch { Constant.isConnected.value = false }
                            }
                            override fun onPairingRequired(d: ConnectableDevice, s: com.connectsdk.service.DeviceService, p: com.connectsdk.service.DeviceService.PairingType) {}
                            override fun onCapabilityUpdated(d: ConnectableDevice, a: List<String>, r: List<String>) {}
                            override fun onConnectionFailed(d: ConnectableDevice, e: com.connectsdk.service.command.ServiceCommandError) {
                                lifecycleScope.launch { 
                                    dismissAutoConnectDialog()
                                    isAutoConnecting = false
                                    Toast.makeText(this@MainActivity, "ConnectSDK connection failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                        device.connect()
                    }
                    override fun onDisconnect() {
                        Timber.tag(TAG).d("AutoReconnect: onDisconnect")
                        lifecycleScope.launch {
                            Constant.isConnected.value = false
                            dismissAutoConnectDialog()
                            isAutoConnecting = false
                            stopKeepAliveService()
                        }
                    }
                    override fun onImeShow(text: String, fieldCounter: Int) {}
                    override fun onError(error: String) {
                        Timber.tag(TAG).e("AutoReconnect: onError = %s", error)
                        lifecycleScope.launch {
                            dismissAutoConnectDialog()
                            isAutoConnecting = false
                            Toast.makeText(this@MainActivity, "Auto-connect failed", Toast.LENGTH_SHORT).show()
                        }
                        remote.abort()
                    }
                })
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "AutoReconnect: Exception during reconnect")
                lifecycleScope.launch {
                    dismissAutoConnectDialog()
                    isAutoConnecting = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register network callback to listen to Wi-Fi changes
        Timber.tag(TAG).d("onResume: ")
        

    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("onDestroy: ")
        stopKeepAliveService()

    }

    fun showInterstitialIfEligible(onComplete: () -> Unit = {}) {
        onComplete()
    }

    /**
     * Starts the KeepAliveService only if the app is currently in the foreground
     * (lifecycle state >= STARTED). On Android 12+, startForegroundService() throws
     * ForegroundServiceStartNotAllowedException when called from the background.
     *
     * If the app is not in the foreground yet, we set a flag and defer the start
     * to onStart(), which is always called while the app is visible.
     */
    private fun startKeepAliveService() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startKeepAliveServiceNow()
        } else {
            Timber.tag(TAG).d("KeepAlive pending — app in background, will start on next onStart")
            pendingKeepAliveStart = true
        }
    }

    /** Actually issues the startForegroundService / startService call. */
    private fun startKeepAliveServiceNow() {
        val intent = Intent(this, KeepAliveService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Timber.tag(TAG).d("KeepAliveService started")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start KeepAliveService (foreground start not allowed)")
            // Defer it so it can start when next visible
            pendingKeepAliveStart = true
        }
    }

    /** Stops the KeepAliveService (e.g. on disconnect or app close). */
    private fun stopKeepAliveService() {
        pendingKeepAliveStart = false   // cancel any deferred start too
        stopService(Intent(this, KeepAliveService::class.java))
        Timber.tag(TAG).d("KeepAliveService stopped")
    }



    override fun onClick(view: View?) {
        when (view?.id) {

            R.id.ll_mini_player -> {
                startActivity(Intent(this@MainActivity, CastPlayerActivity::class.java))
            }

            R.id.actionStop -> {
                // Tell the CastService to stop — it will update isCasting, isPlaying,
                // stop the TV media, and remove the notification.
                stopCastingSession()
            }

            R.id.miniPlayerPlayPauseButton -> {
                // Route through CastService so notification icon stays in sync too.
                if (Constant.connectableDevice?.isConnected == true && Constant.connectableDevice != null) {
                    val toggleIntent = Intent(this, CastService::class.java).apply {
                        action = CastService.ACTION_TOGGLE_PAUSE
                    }
                    startService(toggleIntent)
                }
            }
        }
    }

    private fun stopCastingSession() {
        val stopIntent = Intent(this, CastService::class.java).apply {
            action = CastService.ACTION_QUIT
        }
        startService(stopIntent)
        binding.llMiniPlayer.visibility = View.GONE
    }

    private fun updateMediaSliderProgress() {
        lifecycleScope.launch {
            Constant.connectableDevice?.mediaControl?.getPosition(object :
                PositionListener {
                override fun onError(error: ServiceCommandError?) {
                    Timber.tag(TAG).e("onError: ")
                }

                override fun onSuccess(position: Long?) {
                    Constant.connectableDevice?.mediaControl?.getDuration(object :
                        DurationListener {
                        override fun onError(error: ServiceCommandError?) {
                            Timber.tag(TAG).e("onError: getDuration ")
                        }

                        override fun onSuccess(duration: Long?) {

                            binding.progressBar.progress = position?.toInt()!!
                            // Update play/pause button state
                            updatePlayPauseButtonState()
                        }

                    })


                }

            })
        }
    }



    private fun updatePlayPauseButtonState() {
        lifecycleScope.launch {

            try {
                Constant.connectableDevice?.mediaControl?.getPlayState(object :
                    PlayStateListener {
                    override fun onError(error: ServiceCommandError?) {
                        Timber.tag(TAG).e("onError: ")
                    }

                    override fun onSuccess(playState: MediaControl.PlayStateStatus?) {
                        when (playState) {
                            MediaControl.PlayStateStatus.Playing -> {
                                binding.miniPlayerPlayPauseButton.setImageResource(R.drawable.ic_pause)
                            }


                            MediaControl.PlayStateStatus.Paused -> {
                                binding.miniPlayerPlayPauseButton.setImageResource(R.drawable.ic_play)
                            }

                            else -> {
                                Timber.tag(TAG).d("onSuccess: ")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Timber.tag(TAG).e("updatePlayPauseButtonState: %s", e.message)
            }

        }
    }

    private fun showExitDialog() {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { dialog, _ ->
                dialog.dismiss()
                finishAffinity()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)

        val dialog = builder.create()

        // Ensure dialog is shown and focused for Fire TV remote
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
        }

        dialog.show()
    }


}
