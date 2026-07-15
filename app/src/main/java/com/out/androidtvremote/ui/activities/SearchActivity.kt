package com.out.androidtvremote.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.device.ConnectableDeviceListener
import com.connectsdk.discovery.DiscoveryManager
import com.connectsdk.discovery.DiscoveryManagerListener
import com.connectsdk.service.DeviceService
import com.connectsdk.service.command.ServiceCommandError
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.out.androidtvremote.androidLib.AndroidRemoteTv
import com.out.androidtvremote.androidLib.AndroidTvListener
import com.out.androidtvremote.App
import com.out.androidtvremote.R
import com.out.androidtvremote.databinding.ActivitySearchDeviceBinding
import com.out.androidtvremote.interfaces.IFTDeviceAppListener
import com.out.androidtvremote.ui.activities.adapters.SearchAdapter
import com.out.androidtvremote.ui.activities.viewmodel.SearchDeviceViewModel
import com.out.androidtvremote.utils.CheckConnectionReceiver
import com.out.androidtvremote.utils.Constant


import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient


/**
 * SearchActivity which is use for searching devices and connect to them
 */
class SearchActivity : AppCompatActivity(),
    DiscoveryManagerListener,
    IFTDeviceAppListener.ConnectDeviceListener<ConnectableDevice> {

    private val deviceIds = HashSet<String>()
    private val connectableDeviceList = ArrayList<ConnectableDevice>()
    private val deviceListLock = Any()

    private var app: App? = null
    private val TAG = SearchActivity::class.java.simpleName
    private lateinit var binding: ActivitySearchDeviceBinding
    private var searchDeviceAdapter: SearchAdapter? = null
    private var sharedPreferences: SharedPreferences? = null
    private var myPreferences: SharedPreferences? = null



    private val searchDeviceViewModel: SearchDeviceViewModel by viewModels()
    private var baseOkHttpClient: OkHttpClient? = null
    private var connectivityReceiver: CheckConnectionReceiver? = null



    private val searchScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var pairingDialog: AlertDialog? = null




    /**
     * AndroidRemoteTV object
     */
    private lateinit var androidRemoteTv: AndroidRemoteTv



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySearchDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupInsets()

        setupNetworkReceiver()
        setupSharedPreferences()
        setupRecyclerViewAndAdapter()
        setupButtons()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }


    private fun setupNetworkReceiver() {
        connectivityReceiver = CheckConnectionReceiver(::updateUI)
        connectivityReceiver?.register(this)
    }

    @SuppressLint("UseSupportActionBar")
    private fun setupSharedPreferences() {
        baseOkHttpClient = OkHttpClient()

        sharedPreferences = getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
//        // Initialize myPreferences for Android TV pairing state
        myPreferences = getSharedPreferences("android_tv_prefs", Context.MODE_PRIVATE)
        app = application as App

        app?.discoveryManager?.addListener(this)
        androidRemoteTv = AndroidRemoteTv()

        // Setup toolbar
        setSupportActionBar(binding.toolbar)

        binding.toolbar.inflateMenu(R.menu.device_search_menu)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        initViewModel()
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun setupRecyclerViewAndAdapter() {
        val rvListDevice = binding.recyclerViewSearchDevice
        searchDeviceAdapter = SearchAdapter(this, this, searchDeviceViewModel)
        rvListDevice.layoutManager = LinearLayoutManager(this)
        rvListDevice.adapter = searchDeviceAdapter
    }

    private fun setupButtons() {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        }
        binding.btnTryagain.setOnClickListener {
            val isConnected = CheckConnectionReceiver.isConnected(this)
            if (isConnected) {
                binding.recyclerViewSearchDevice.visibility = View.VISIBLE
                binding.lNoWifi.visibility = View.GONE
            } else {
                binding.recyclerViewSearchDevice.visibility = View.GONE
                binding.lNoWifi.visibility = View.VISIBLE
            }
        }

    }



    private fun updateUI(isConnected: Boolean) {
        if (isConnected) {
            binding.lNoWifi.visibility = View.GONE
            binding.lpProgressBar.visibility = View.VISIBLE
            binding.recyclerViewSearchDevice.visibility = View.VISIBLE
        } else {
            binding.lpProgressBar.visibility = View.GONE
            binding.lNoWifi.visibility = View.VISIBLE
            binding.recyclerViewSearchDevice.visibility = View.GONE
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initViewModel() {
        searchDeviceViewModel.connectableDevices.observe(this, Observer {
            searchDeviceAdapter?.notifyDataSetChanged()
        })
        searchDeviceViewModel.connectionState.observe(this, Observer {
            searchDeviceAdapter?.notifyDataSetChanged()
        })
        searchDeviceViewModel.updateProgressbar.observe(this, Observer {
            searchDeviceAdapter?.notifyDataSetChanged()
        })
        Constant.isConnected.observe(this) {
            searchDeviceAdapter?.notifyDataSetChanged()
        }
    }

    private fun showHelpDialog() {
        val helpBuilder = MaterialAlertDialogBuilder(this)
        val helpLayout: View = layoutInflater.inflate(R.layout.dialog_help, null)
        helpBuilder.setView(helpLayout)

        val helpDialog = helpBuilder.create()

        val btnGotIt = helpLayout.findViewById<MaterialButton>(R.id.btn_got_it)
        btnGotIt.setOnClickListener {
            helpDialog.dismiss()
        }

        helpDialog.show()
    }


    /**
     * Device discovery listener
     */
    override fun onDeviceAdded(manager: DiscoveryManager?, device: ConnectableDevice?) {
        val discoveredDevice = device ?: return
        searchScope.launch {
            try {
                val shouldAdd = withContext(Dispatchers.Default) {
                    synchronized(deviceListLock) { deviceIds.add(discoveredDevice.id) }
                }
                if (!shouldAdd) return@launch

                val snapshot = synchronized(deviceListLock) {
                    connectableDeviceList.add(discoveredDevice)
                    ArrayList(connectableDeviceList)
                }
                searchDeviceViewModel.updateConnectableDevices(snapshot)
            } catch (e: Exception) {
                Log.d(TAG, "onDeviceAdded: ${e.message}")
            }
        }
    }

    override fun onDeviceUpdated(manager: DiscoveryManager?, device: ConnectableDevice?) {
        Log.d(TAG, "onDeviceUpdated:")
    }

    override fun onDeviceRemoved(manager: DiscoveryManager?, device: ConnectableDevice?) {
        val removedDevice = device ?: return
        searchScope.launch {
            val snapshot = withContext(Dispatchers.Default) {
                synchronized(deviceListLock) {
                    deviceIds.remove(removedDevice.id)
                    connectableDeviceList.removeAll { it.id == removedDevice.id }
                    ArrayList(connectableDeviceList)
                }
            }
            searchDeviceViewModel.updateConnectableDevices(snapshot)
        }
    }

    override fun onDiscoveryFailed(manager: DiscoveryManager?, error: ServiceCommandError?) {
        Log.d(TAG, "onDiscoveryFailed:")
    }

    override fun onItemSearchClickListener(item: ConnectableDevice) {
        Log.d(TAG, "onItemSearchClickListener: ${item.friendlyName}")
        Log.d(TAG, "onItemSearchClickListener: Listener size ${item.listeners.size}")
        item.addListener(deviceListener)
        if (item.isConnected) {
            showDisconnectConfirmationDialog(item)
        } else {
            val currentIp = item.ipAddress
            val lastHost = myPreferences?.getString("HOST", "")
            val isPaired = myPreferences?.getBoolean("pin", false) == true

            Log.d(TAG, "Connecting to: $currentIp, Last Host: $lastHost, Paired: $isPaired")

            if (currentIp == lastHost && isPaired) {
                Log.d(TAG, "Attempting to reconnect to: $currentIp")
                reconnectAndroid(currentIp,item)
            } else {
                Log.d(TAG, "Starting fresh connection to: $currentIp")
                connectAndroid(currentIp,item)
            }

        }
    }


    private fun showDisconnectConfirmationDialog(device: ConnectableDevice) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Disconnect Device")
            .setMessage("Are you sure you want to disconnect from ${device.friendlyName}?")
            .setPositiveButton("Disconnect") { _, _ ->
                device.disconnect()
                // Also abort the AndroidTV remote connection
                val app = application as App
                app.androidRemoteTv?.abort()
                app.androidRemoteTv = null
                // Clear casting state and hide mini player
                Constant.isCasting.postValue(false)
                Constant.isPlaying.postValue(false)
                // Stop the cast notification service
                stopService(Intent(this, com.out.androidtvremote.services.CastService::class.java))
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create().show()
    }

    /**
     * ConnectableDeviceListener
     * This methods execute after device is connected via connect sdk
     */
    private var deviceListener: ConnectableDeviceListener = object : ConnectableDeviceListener {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onDeviceReady(device: ConnectableDevice) {
            if (device.isConnected) {
                Constant.connectedDeviceName.value = device.friendlyName
            }
            Constant.isConnected.value = true
            try {
                Constant.connectableDevice = device
                searchDeviceViewModel.updateConnectionState(device)
            } catch (e: Exception) {
                Log.e(TAG, "onDeviceReady: ${e.message}")
            } finally {
                Log.d(TAG, "onDeviceReady: finally dismiss")
                finish()
                Log.d(
                    TAG,
                    "onDeviceReady :  ${device.friendlyName}   ${device.ipAddress}   ${device.connectedServiceNames}"
                )
            }
        }

        override fun onDeviceDisconnected(device: ConnectableDevice) {
            Log.d(TAG, "onDeviceDisconnected:")
            Constant.isConnected.value = false
            try {
                Constant.connectedDeviceName.value = getString(R.string.app_name)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error ${e.message}")
            }
        }

        override fun onPairingRequired(
            device: ConnectableDevice,
            service: DeviceService,
            pairingType: DeviceService.PairingType
        ) {
            Log.d(TAG, "onPairingRequired:")
        }

        override fun onCapabilityUpdated(
            device: ConnectableDevice,
            added: List<String>,
            removed: List<String>
        ) {
            Log.d(TAG, "onCapabilityUpdated:")
        }

        override fun onConnectionFailed(
            device: ConnectableDevice,
            error: ServiceCommandError
        ) {
            if (device != null) Log.d("2ndScreenAPP", "Failed to connect to " + device.ipAddress)
        }
    }


    private fun connectAndroid(host: String?,connectableDevice: ConnectableDevice) {
        if (host.isNullOrBlank()) {
            Toast.makeText(this, "Device IP invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading via ViewModel if adapter observes it, or just rely on dialogs?
        // For fresh connection, we might show pairing dialog.

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                androidRemoteTv.connect(host, object : AndroidTvListener {
                    override fun onSessionCreated() {
                        Log.d(TAG, "onSessionCreated")
                    }

                    override fun onSecretRequested() {
                        Log.d(TAG, "onSecretRequested")
                        searchScope.launch { startPairing() }
                    }

                    override fun onPaired() {
                        Log.d(TAG, "onPaired")
                    }

                    override fun onConnectingToRemote() {
                        Log.d(TAG, "onConnectingToRemote")
                    }

                    override fun onConnected() {
                        Log.d(TAG, "onConnected: Android Remote Connected")
                        saveConnectionState(host, true)
                        app?.androidRemoteTv = androidRemoteTv
                        connectableDevice.connect()
                        searchScope.launch { pairingDialog?.dismiss() }
                    }

                    override fun onDisconnect() {
                        Log.d(TAG, "onDisconnect")
                        searchScope.launch { pairingDialog?.dismiss() }
                    }

                    override fun onImeShow(text: String, fieldCounter: Int) {
                        Log.d(TAG, "onImeShow: fieldCounter=$fieldCounter")
                        val app = application as App
                        app.lastImeText = text
                        app.lastFieldCounter = fieldCounter
                        app.onImeShowCallback?.invoke(text, fieldCounter)
                    }


                    override fun onError(error: String) {
                        Log.e(TAG, "onError: $error")
                        searchScope.launch {
                            pairingDialog?.dismiss()
                            val isWrongPin = error.contains("secret", ignoreCase = true) ||
                                error.contains("pairing", ignoreCase = true) ||
                                error.contains("invalid", ignoreCase = true)
                                
                            val errorMessage = if (isWrongPin) {
                                "Invalid PIN. Requesting new PIN..."
                            } else {
                                "Connection Failed: $error"
                            }
                            Toast.makeText(this@SearchActivity, errorMessage, Toast.LENGTH_SHORT).show()
                            
                            androidRemoteTv.abort()
                            saveConnectionState(null, false)
                            
                            if (isWrongPin) {
                                // Automatically request a new PIN
                                connectAndroid(host, connectableDevice)
                            } else {
                                searchDeviceViewModel.updateProgressbarState(true)
                                Constant.connectableDevice?.disconnect()
                                Constant.connectableDevice = null
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "connectAndroid exception", e)
                searchScope.launch {
                    pairingDialog?.dismiss()
                    
                    val errorMessage = e.message ?: ""
                    val isWrongPin = errorMessage.contains("STATUS_BAD_SECRET", ignoreCase = true) ||
                                     errorMessage.contains("invalid", ignoreCase = true)
                                     
                    val toastMessage = if (isWrongPin) "Wrong PIN. Requesting new PIN..." else "Error: $errorMessage"
                    Toast.makeText(this@SearchActivity, toastMessage, Toast.LENGTH_SHORT).show()
                    
                    androidRemoteTv.abort()
                    saveConnectionState(null, false)
                    
                    if (isWrongPin) {
                        // Automatically request a new PIN
                        connectAndroid(host, connectableDevice)
                    } else {
                        // Hide progress bar
                        searchDeviceViewModel.updateProgressbarState(true)
                        
                        // Cleanup
                        Constant.connectableDevice?.disconnect()
                        Constant.connectableDevice = null
                    }
                }
            }
        }
    }


    private fun reconnectAndroid(host: String?,connectableDevice: ConnectableDevice) {
        if (host.isNullOrBlank()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                androidRemoteTv.reconnect(host, object : AndroidTvListener {
                    override fun onSessionCreated() {}
                    override fun onPaired() {}
                    override fun onConnectingToRemote() {}

                    override fun onSecretRequested() {
                        Log.d(TAG, "Reconnect needs PIN, falling back to fresh connection")
                        searchScope.launch {
                            saveConnectionState(null, false) // Clear state
                            Toast.makeText(
                                this@SearchActivity,
                                "Re-pairing required",
                                Toast.LENGTH_SHORT
                            ).show()
                            connectAndroid(host,connectableDevice) // Fallback
                        }
                    }

                    override fun onConnected() {
                        Log.d(TAG, "Reconnect Successful")
                        app?.androidRemoteTv = androidRemoteTv
                        saveConnectionState(host, true)
                        connectableDevice.connect()
                        searchScope.launch {
//                            Toast.makeText(
//                                this@SearchActivity,
//                                "Reconnected to ${Constant.connectableDevice?.friendlyName}",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                            finish()
                        }
                    }

                    override fun onDisconnect() {}
                    override fun onImeShow(text: String, fieldCounter: Int) {
                        Log.d(TAG, "reconnect onImeShow: fieldCounter=$fieldCounter")
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Reconnect failed: $error. Trying fresh connection")
                        searchScope.launch {
                            saveConnectionState(null, false)
                            connectAndroid(host,connectableDevice)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "reconnect Exception", e)
                searchScope.launch {
                    saveConnectionState(null, false)
                    connectAndroid(host,connectableDevice)
                }
            }
        }
    }

    private fun saveConnectionState(host: String?, isPaired: Boolean) {
        val editor = myPreferences?.edit()
        if (isPaired && host != null) {
            editor?.putString("HOST", host)
            editor?.putBoolean("pin", true)
            editor?.putBoolean("ISCONNECTED", true)
            // Handle Device Name/IP if needed
            if (Constant.connectableDevice != null) {
                editor?.putString("IPADDRESS", Constant.connectableDevice!!.ipAddress)
                editor?.putString("DEVICENAME", Constant.connectableDevice!!.friendlyName)
            }
        } else {
            editor?.putBoolean("pin", false)
            editor?.putBoolean("ISCONNECTED", false)
            editor?.remove("HOST")
            editor?.remove("IPADDRESS")
            editor?.remove("DEVICENAME")
        }
        editor?.apply()
    }

    private fun startPairing() {
        if (!canShowDialogSafely()) {
            Log.w(TAG, "startPairing skipped: activity is not in a valid UI state")
            return
        }

        if (pairingDialog?.isShowing == true) {
            Log.d(TAG, "startPairing ignored: pairing dialog already visible")
            return
        }

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pairingBuilder = MaterialAlertDialogBuilder(this)
        val customLayout: View =
            layoutInflater.inflate(R.layout.custom_alert_pairing_dialog, null)
        pairingBuilder.setView(customLayout)
        pairingBuilder.setCancelable(false)
        pairingDialog = pairingBuilder.create()

        val cancelDialog = customLayout.findViewById<MaterialButton>(R.id.button1)
        val okDialog = customLayout.findViewById<MaterialButton>(R.id.button2)
        val pairingcode = customLayout.findViewById<EditText>(R.id.tv_alert_dialog_edittext)
        val characterCount =
            customLayout.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.tv_character_count)

        pairingcode.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(6))


        okDialog.isEnabled = false
        okDialog.alpha = 0.5f

        // Pairing timeout - 30 seconds
        val pairingTimeoutJob = searchScope.launch {
            kotlinx.coroutines.delay(30000L)
            if (pairingDialog?.isShowing == true) {
                pairingDialog?.dismiss()
                Toast.makeText(
                    this@SearchActivity,
                    "Pairing timeout. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()

                // Hide progress bar
                searchDeviceViewModel.updateProgressbarState(true)

                // Cleanup
                androidRemoteTv.abort()
                Constant.connectableDevice?.disconnect()
                Constant.connectableDevice = null
            }
        }

        pairingcode.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                characterCount.text = "$length/6"

                if (length == 6) {
                    okDialog.isEnabled = true
                    okDialog.alpha = 1.0f
                    characterCount.setTextColor(getColor(R.color.md_theme_primary))
                } else {
                    okDialog.isEnabled = false
                    okDialog.alpha = 0.5f
                    characterCount.setTextColor(getColor(android.R.color.darker_gray))
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        pairingcode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                if (pairingcode.text.toString().length == 6) {
                    okDialog.performClick()
                    true
                } else {
                    Toast.makeText(this, "Please enter all 6 characters", Toast.LENGTH_SHORT)
                        .show()
                    true
                }
            } else {
                false
            }
        }

        okDialog.setOnClickListener {
            val code = pairingcode.text.toString()

            if (code.length != 6) {
                Toast.makeText(
                    applicationContext,
                    "Please enter all 6 characters of the pairing code.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            imm.hideSoftInputFromWindow(pairingcode.windowToken, 0)
            okDialog.isEnabled = false
            okDialog.text = "Pairing..."

            try {
                androidRemoteTv.sendSecret(code)
                Log.d(TAG, "Pairing code sent: $code")
                // Cancel timeout on successful pairing attempt
                pairingTimeoutJob.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send pairing code", e)
                Toast.makeText(
                    applicationContext,
                    "Failed to send pairing code: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                okDialog.isEnabled = true
                okDialog.text = "Pair"
            }
        }

        cancelDialog.setOnClickListener {
            pairingTimeoutJob.cancel()
            pairingDialog?.dismiss()
            imm.hideSoftInputFromWindow(pairingcode.windowToken, 0)
            Toast.makeText(this, "Pairing cancelled", Toast.LENGTH_SHORT).show()

            // Hide progress bar
            searchDeviceViewModel.updateProgressbarState(true)

            // Cleanup on cancel
            androidRemoteTv.abort()
            Constant.connectableDevice?.disconnect()
            Constant.connectableDevice = null
        }

        pairingDialog?.setOnDismissListener {
            pairingTimeoutJob.cancel()
        }

        pairingDialog?.setOnShowListener {
            pairingcode.requestFocus()
            pairingcode.postDelayed({
                imm.showSoftInput(pairingcode, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        if (!canShowDialogSafely()) {
            pairingTimeoutJob.cancel()
            return
        }

        try {
            pairingDialog?.show()
        } catch (e: android.view.WindowManager.BadTokenException) {
            pairingTimeoutJob.cancel()
            Log.e(TAG, "Failed to show pairing dialog: invalid window token", e)
        } catch (e: IllegalStateException) {
            pairingTimeoutJob.cancel()
            Log.e(TAG, "Failed to show pairing dialog: activity state invalid", e)
        }
    }

    private fun canShowDialogSafely(): Boolean {
        return !isFinishing &&
            !isDestroyed &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }


    /**
     * Toolbar Menu operations
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.device_search_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                showHelpDialog()
                true
            }

            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    // region: LifeCycle
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: ")
        connectivityReceiver?.register(this)
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: ")
        connectivityReceiver?.unregister(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Log.d(TAG, "onBackPressed: ")
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_bottom)
    }

    override fun onDestroy() {
        pairingDialog?.dismiss()
        pairingDialog = null
        app?.discoveryManager?.removeListener(this)
        searchScope.cancel()
        super.onDestroy()
        Log.d(TAG, "onDestroy: ")
    }

}
