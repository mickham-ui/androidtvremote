package com.out.androidtvremote.ui.frags.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.out.androidtvremote.App
import com.out.androidtvremote.androidLib.remote.Remotemessage
import com.out.androidtvremote.R
import com.out.androidtvremote.databinding.FragmentRemoteBinding
import com.out.androidtvremote.ui.activities.SearchActivity
import com.out.androidtvremote.views.DpadView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.compareTo
import kotlin.math.abs
import androidx.core.graphics.toColorInt
import com.out.androidtvremote.utils.Constant
import com.out.androidtvremote.utils.QuickAccessApps



class RemoteFragment : Fragment(), View.OnClickListener {

    private val TAG = RemoteFragment::class.java.simpleName

    private var _binding: FragmentRemoteBinding? = null

    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences
    private var isHapticEnabled = true
    private val remoteViewModel: RemoteViewModel by viewModels()

    private var controlMode = 0
    private var isPlaying = true
    private var isMuted = false

    // Added for Preferred Layout Cycle tracking
    private var showingNumpad = false
    private var defaultLayoutPrefs = "touchpad"

    private var tvKeyboard: TvImeKeyboard? = null
    private var keyboardDialog: androidx.appcompat.app.AlertDialog? = null




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize SharedPreferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())




    }

    override fun onResume() {
        super.onResume()
        remoteViewModel.refreshConnection()
        
        // Load preferences
        isHapticEnabled = sharedPreferences.getBoolean("haptic_feedback", true)
        defaultLayoutPrefs = sharedPreferences.getString("default_remote_layout", "touchpad") ?: "touchpad"

        // Setup the initial layout view correctly
        showingNumpad = false
        if (defaultLayoutPrefs == "dpad") {
            binding.touchpadCard.visibility = View.GONE
            binding.dpad.visibility = View.VISIBLE
            binding.mcvKeypad.visibility = View.GONE
            applyDpadMode(true)
        } else {
            binding.touchpadCard.visibility = View.VISIBLE
            binding.dpad.visibility = View.GONE
            binding.mcvKeypad.visibility = View.GONE
            applyDpadMode(false)
        }

        // Wire real TV volume to LiveData as soon as fragment resumes
        remoteViewModel.observeRealVolumeFromTv()   // ← ADD THIS LINE

        setupAppChips()

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentRemoteBinding.inflate(inflater, container, false)
        val root: View = binding.root


        return root
    }


    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppChips()
        setupButtonListeners()
        setupVolumeVisualizer()
        setupKeyboard()          // ← ADD THIS LINE

        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var isSwipeInProgress = false


        Constant.remoteConnected.observe(viewLifecycleOwner) { connected ->
            if (connected) {
                remoteViewModel.observeRealVolumeFromTv()
                Constant.remoteConnected.value = false  // reset so it can fire again next connect
            }
        }


        binding.btnKeypad.setOnClickListener(this)


        binding.touchpad.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    binding.scrollView.requestDisallowInterceptTouchEvent(true)
//                    touchpadTouchListener?.onTouchpadTouched(true)
                    isSwipeInProgress = false // Reset swipe flag
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - startX
                    val deltaY = event.y - startY
                    // Check if the movement is beyond the touch slop
                    if (!isSwipeInProgress && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        isSwipeInProgress = true // Set swipe flag
                        // Detect swipe direction
                        if (abs(deltaX) > abs(deltaY)) {
                            // Horizontal swipe
                            if (deltaX > 0) {
                                // Right swipe
                                performHaptic(binding.touchpad)
                                checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_RIGHT) }

                            } else {
                                // Left swipe
                                performHaptic(binding.touchpad)
                                checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_LEFT) }
                            }
                        } else {
                            // Vertical swipe
                            if (deltaY > 0) {
                                // Down swipe
                                performHaptic(binding.touchpad)
                                checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_DOWN) }
                            } else {
                                // Up swipe
                                performHaptic(binding.touchpad)
                                checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_UP) }
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaX = event.x - startX
                    val deltaY = event.y - startY
                    // Check if the movement is beyond the touch slop and no swipe detected
                    if (!isSwipeInProgress && abs(deltaX) <= touchSlop && abs(deltaY) <= touchSlop) {
                        // Tap detected - send D-pad center/OK button
                        performHaptic(binding.touchpad)
                        checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_CENTER) }
                    }
                    // Reset swipe flag
                    isSwipeInProgress = false
                    // Handle touch up or cancel
                    binding.scrollView.requestDisallowInterceptTouchEvent(false)
                }
            }
            true
        }



        binding.dpad.listener = object : DpadView.DpadListener {
            override fun onUp() {
                performHaptic(binding.dpad)
                checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_UP) }
            }
            override fun onDown() {
                performHaptic(binding.dpad)
                checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_DOWN) }
            }
            override fun onLeft() {
                performHaptic(binding.dpad)
                checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_LEFT) }
            }
            override fun onRight() {
                performHaptic(binding.dpad)
                checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_RIGHT) }
            }
            override fun onCenter() {
                performHaptic(binding.dpad)
                checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_CENTER) }
            }
        }




    }


    private fun setupButtonListeners() {
        binding.btnVolUp.setOnClickListener {
            performHaptic(it)
            checkConnectionAndExecute {

                remoteViewModel.volumeUp()
            }
        }
        binding.btnVolDown.setOnClickListener {
            performHaptic(it)
            checkConnectionAndExecute {

                remoteViewModel.volumeDown()
            }
        }

        
        // Channel controls
        binding.btnChUp.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_CHANNEL_UP) } 
        }
        binding.btnChDown.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_CHANNEL_DOWN) } 
        }

        
        // Center controls
        binding.btnHome.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_HOME) } 
        }
        binding.btnMute.setOnClickListener {
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_MUTE) }
            isMuted = !isMuted
            updateMuteButtonState()
        }
        binding.btnSettings.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_BACK) }
        }

        binding.btnVoice.setOnClickListener {
            performHaptic(it)
            checkConnectionAndExecute {
                if (isVoiceActive) {
                    stopVoiceSession()
                } else {
                    ensureMicPermissionAndStartVoice()
                }
            }
        }



        binding.btnKeyboard.setOnClickListener { 
            performHaptic(it)
//            if (!ensurePremiumAccess("Keyboard input")) {
//                return@setOnClickListener
//            }
            checkConnectionAndExecute {
                showKeyboardDialog() 
            }
        }
        
        // Number pad buttons
        binding.btn0.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_0) } 
        }
        binding.btn1.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_1) } 
        }
        binding.btn2.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_2) } 
        }
        binding.btn3.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_3) } 
        }
        binding.btn4.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_4) } 
        }
        binding.btn5.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_5) } 
        }
        binding.btn6.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_6) } 
        }
        binding.btn7.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_7) } 
        }
        binding.btn8.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_8) } 
        }
        binding.btn9.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_9) } 
        }
        binding.btnTv.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_TV) } 
        }
        binding.btnMenuNumber.setOnClickListener { 
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_MENU) } 
        }

        binding.btnFasRewind.setOnClickListener {
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_REWIND) }
        }
        binding.btnPlaypause.setOnClickListener {
            performHaptic(it)
            if (isPlaying) {
                // Pause media
                checkConnectionAndExecute {
                    keyPress(Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_PAUSE)
                }
                binding.btnPlaypause.setIconResource(R.drawable.ic_play)
                isPlaying = false
            } else {
                // Play media
                checkConnectionAndExecute {
                    keyPress(Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_PLAY)
                }
                binding.btnPlaypause.setIconResource(R.drawable.ic_pause)
                isPlaying = true
            }
            updatePlayPauseButtonState()
        }
        binding.btnFastForward.setOnClickListener {
            performHaptic(it)
            checkConnectionAndExecute { keyPress(Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD) }
        }
    }

    private fun performHaptic(view: View) {
        if (isHapticEnabled) {
            view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
    }

    private fun checkConnectionAndExecute(action: () -> Unit) {
        if (remoteViewModel.hasRemote()) {
            action()
        } else {
            openSearchActivity()
        }
    }

    private fun keyPress(key: Remotemessage.RemoteKeyCode) {
        if (remoteViewModel.hasRemote()) {
            remoteViewModel.sendKey(key)
            Log.d(TAG, "Key pressed: $key")
        } else {
            Log.d(TAG, "androidRemoteTv is null")
        }
    }




    // Track voice state
    private var isVoiceActive = false

    @SuppressLint("MissingPermission")
    private val requestMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceSession()
        } else {
            Toast.makeText(requireContext(), "Microphone permission required for voice", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureMicPermissionAndStartVoice() {
        val hasPerm = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            startVoiceSession()
        } else {
            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startVoiceSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (remoteViewModel.hasRemote()) {
                withContext(Dispatchers.IO) {
                    remoteViewModel.getAndroidVTV()?.startVoice()
                }
                isVoiceActive = true
                VoiceButtonAnimator.setActive(
                    button    = binding.btnVoice,
                    rippleView = binding.ivVoiceRipple,
                    active    = true
                )
            } else {
                Toast.makeText(requireContext(), "Could not start voice session", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun stopVoiceSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                remoteViewModel.getAndroidVTV()?.stopVoice()
            }
            isVoiceActive = false
            VoiceButtonAnimator.setActive(
                button     = binding.btnVoice,
                rippleView = binding.ivVoiceRipple,
                active     = false
            )
        }
    }


    private fun setupVolumeVisualizer() {
        // Volume animation bar is hidden in layout — only the numeric TextView is shown.
        // Observer updates only the text label; no animated visualizer calls.
        remoteViewModel.volumeLevel.observe(viewLifecycleOwner) { level ->
            binding.tvVolumeLevel.text = level.toString()
        }
    }

    /** Apply red tint when muted, default grey when unmuted */
    private fun updateMuteButtonState() {
        val color = if (isMuted)
            requireContext().getColor(R.color.btn_state_mute_red)
        else
            requireContext().getColor(R.color.btn_state_default_grey)
        binding.btnMute.iconTint = android.content.res.ColorStateList.valueOf(color)
    }

    /** Green when playing (isPlaying==true), grey when paused */
    private fun updatePlayPauseButtonState() {
        val color = if (isPlaying)
            requireContext().getColor(R.color.btn_state_active_green)
        else
            requireContext().getColor(R.color.btn_state_default_grey)
        binding.btnPlaypause.iconTint = android.content.res.ColorStateList.valueOf(color)
    }

    /** Green when numpad is visible, grey when hidden */
    private fun updateKeypadButtonState() {
        val color = if (showingNumpad)
            requireContext().getColor(R.color.btn_state_active_green)
        else
            requireContext().getColor(R.color.btn_state_default_grey)
        binding.btnKeypad.iconTint = android.content.res.ColorStateList.valueOf(color)
    }

    private fun setupAppChips() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val order = QuickAccessApps.parseOrder(prefs.getString(QuickAccessApps.PREF_ORDER, null))
        val enabled = prefs.getStringSet(QuickAccessApps.PREF_ENABLED, null)
            ?: QuickAccessApps.defaultEnabled()

        val apps = order.filter { enabled.contains(it) }
        binding.appsContainer.removeAllViews()

        if (apps.isEmpty()) {
            binding.appsContainer.visibility = View.GONE
            return
        } else {
            binding.appsContainer.visibility = View.VISIBLE
        }

        apps.forEach { appName ->
            val chip = Chip(requireContext()).apply {
                text = appName
                isClickable = true
                isCheckable = false

                chipBackgroundColor =
                    context.getColorStateList(R.color.chip_background_selector)
                setTextColor(context.getColor(R.color.chip_text_color))

                chipStrokeWidth = 1f.dpToPx()
                chipStrokeColor =
                    context.getColorStateList(R.color.chip_stroke_selector)

                chipCornerRadius = 20f.dpToPx()
                textSize = 12f

                // Material-recommended padding
                chipStartPadding = 12f.dpToPx()
                chipEndPadding = 12f.dpToPx()
                textStartPadding = 6f.dpToPx()
                textEndPadding = 6f.dpToPx()

                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setOnClickListener {
                    performHaptic(it)
                    launchApp(appName)
                }
            }

            binding.appsContainer.addView(chip)
        }
    }


    private fun Float.dpToPx(): Float =
        this * resources.displayMetrics.density


    private fun launchApp(appName: String) {
        if (!remoteViewModel.hasRemote()) {
            openSearchActivity()
            return
        }

        val appUrl = remoteViewModel.mapAppToUrl(appName) ?: return

        Log.d(TAG, "Launching app: $appUrl")
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                remoteViewModel.sendAppLink(appUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error launching app: $appName", e)
            }
        }
    }

    private fun openSearchActivity() {
        startActivity(Intent(requireContext(), SearchActivity::class.java))
        requireActivity().overridePendingTransition(
            R.anim.slide_in_bottom,
            R.anim.slide_out_bottom
        )
    }


    // ── ADD this new method ───────────────────────────────────────
    private fun setupKeyboard() {
        val remoteTv = remoteViewModel.getAndroidVTV() ?: return

        // Create keyboard manager once — reuse it
        tvKeyboard = TvImeKeyboard(remoteTv, viewLifecycleOwner.lifecycleScope)

        val app = requireActivity().application as App

        // Restore already-synced field if available
        if (app.lastFieldCounter != -1) {
            tvKeyboard?.onImeShow(app.lastImeText, app.lastFieldCounter)
        }

        // Register for live IME updates from TV
        app.onImeShowCallback = { text, fieldCounter ->
            Log.d(TAG, "Fragment got onImeShow: text='$text' field=$fieldCounter")
            tvKeyboard?.onImeShow(text, fieldCounter)

            // Auto-open keyboard when TV has a text field focused
            activity?.runOnUiThread {
                if (keyboardDialog?.isShowing != true) {
                    keyboardDialog = KeyboardDialog.show(requireActivity(), tvKeyboard!!)
                }
            }
        }
    }

    // ── REPLACE your existing showKeyboardDialog() ────────────────
    private fun showKeyboardDialog() {
        val keyboard = tvKeyboard
        if (keyboard == null) {
            // Not connected yet — try to init
            val remoteTv = remoteViewModel.getAndroidVTV()
            if (remoteTv == null) {
                openSearchActivity()
                return
            }
            tvKeyboard = TvImeKeyboard(remoteTv, viewLifecycleOwner.lifecycleScope)
            val app = requireActivity().application as App
            if (app.lastFieldCounter != -1) {
                tvKeyboard?.onImeShow(app.lastImeText, app.lastFieldCounter)
            }
        }
        keyboardDialog = KeyboardDialog.show(requireActivity(), tvKeyboard!!)
    }






    // ── REPLACE onDestroyView() — make sure callbacks are cleared ────────────
    override fun onDestroyView() {
        super.onDestroyView()


        keyboardDialog?.dismiss()
        keyboardDialog = null

        val app = activity?.application as? App
        app?.onImeShowCallback = null

        _binding = null
    }

    override fun onDestroy() {

        super.onDestroy()
    }

    override fun onClick(view: View?) {
        when (view?.id) {

            binding.btnKeypad.id -> {
                view.let { performHaptic(it) }

                showingNumpad = !showingNumpad

                if (showingNumpad) {
                    // Show NumPad — card is visible for numpad
                    binding.touchpadCard.visibility = View.GONE
                    binding.dpad.visibility = View.GONE
                    binding.mcvKeypad.visibility = View.VISIBLE
                    applyDpadMode(false)
                } else {
                    // Revert to user's preferred native layout
                    binding.mcvKeypad.visibility = View.GONE
                    if (defaultLayoutPrefs == "dpad") {
                        binding.touchpadCard.visibility = View.GONE
                        binding.dpad.visibility = View.VISIBLE
                        applyDpadMode(true)
                    } else {
                        binding.touchpadCard.visibility = View.VISIBLE
                        binding.dpad.visibility = View.GONE
                        applyDpadMode(false)
                    }
                }
                updateKeypadButtonState()
            }
        }
    }







    /**
     * Toggles the hero card's visual appearance.
     * When D-pad is active: transparent background, no stroke, no elevation — card is invisible.
     * When touchpad/numpad is active: restore normal card surface styling.
     */
    private fun applyDpadMode(isDpad: Boolean) {
        val card = binding.heroCard
        if (isDpad) {
            // Strip all card chrome so nothing is visible around the D-pad
            card.setCardBackgroundColor(
                android.graphics.Color.TRANSPARENT
            )
            card.strokeWidth = 0
            card.cardElevation = 0f
        } else {
            // Restore normal surface card styling
            val surfaceColor = com.google.android.material.color.MaterialColors.getColor(
                card,
                com.google.android.material.R.attr.colorSurfaceContainerLow
            )
            card.setCardBackgroundColor(surfaceColor)
            card.strokeWidth = resources.displayMetrics.density.toInt() // 1 dp
            card.cardElevation = 0f
        }
    }
}
