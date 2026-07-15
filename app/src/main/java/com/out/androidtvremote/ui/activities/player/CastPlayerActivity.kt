package com.out.androidtvremote.ui.activities.player
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import android.net.Uri
import android.os.Environment
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import coil.load
import com.connectsdk.core.MediaInfo
import com.connectsdk.device.ConnectableDevice
import com.connectsdk.service.capability.MediaControl
import com.connectsdk.service.capability.MediaControl.DurationListener
import com.connectsdk.service.capability.MediaControl.PlayStateListener
import com.connectsdk.service.capability.MediaControl.PositionListener
import com.connectsdk.service.capability.MediaPlayer
import com.connectsdk.service.capability.MediaPlayer.LaunchListener
import com.connectsdk.service.capability.MediaPlayer.MediaLaunchObject
import com.connectsdk.service.capability.listeners.ResponseListener
import com.connectsdk.service.command.ServiceCommandError
import com.connectsdk.service.sessions.LaunchSession
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.out.androidtvremote.App
import com.out.androidtvremote.R
import com.out.androidtvremote.databinding.ActivityCastPlayerBinding
import com.out.androidtvremote.services.CastService
import com.out.androidtvremote.ui.frags.cast.adapters.ImageCastAdapter
import com.out.androidtvremote.ui.frags.cast.data.MediaItem
import com.out.androidtvremote.ui.frags.cast.data.MediaType
import com.out.androidtvremote.ui.frags.cast.manager.MediaCastingManager
import com.out.androidtvremote.utils.Constant
import com.out.androidtvremote.utils.formatTime
import com.out.androidtvremote.utils.getAudioImage
import com.out.androidtvremote.utils.getVideoThumbnail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class CastPlayerActivity : AppCompatActivity() {
    val TAG = CastPlayerActivity::class.java.simpleName
    lateinit var binding: ActivityCastPlayerBinding
    private lateinit var connectableDevice: ConnectableDevice
    private lateinit var castingProgressDialog: MaterialAlertDialogBuilder
    private lateinit var castingDialog: AlertDialog
    private lateinit var castingProgressIndicator: LinearProgressIndicator
    private lateinit var imageCastAdapter: ImageCastAdapter
    private lateinit var mediaCastingManager: MediaCastingManager

    private val handler = Handler(Looper.getMainLooper())
    private var currentPosition = 0
    private var totalDuration = 0
    private var mediaItem: MediaItem? = null
    private var mediaType: MediaType? = null
    private var currentMode = Mode.VIDEO
    var castTitle: String? = null
    var castPath: String? = null
    var mLaunchSession: LaunchSession? = null
    var mMediaControl: MediaControl? = null

    // Index of the current item in Constant.castMediaList for sequential play
    private var currentMediaIndex: Int = 0
    // Subscription token for play-state listener
    private var playStateSubscription: com.connectsdk.service.command.ServiceSubscription<MediaControl.PlayStateListener>? = null

    enum class Mode { VIDEO, IMAGE }

    private val updateSliderRunnable = object : Runnable {
        override fun run() {
            updateMediaSliderProgress()
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val MIN_SLIDER_VALUE_TO = 1f
        private const val NOTIFICATION_CHANNEL_ID = "casting_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_STOP_CASTING = "action_stop_casting"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityCastPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge window insets
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

        if (Constant.connectableDevice != null) {
            connectableDevice = Constant.connectableDevice!!
            mediaCastingManager = MediaCastingManager(connectableDevice)
        }
        Log.d(TAG, "${Constant.currentCastMediaItem.value?.title}")

        setSupportActionBar(binding.mbCastPlayer)
        supportActionBar?.title = Constant.currentCastMediaItem.value?.title
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupUI()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupUI() {
        mediaItem = Constant.currentCastMediaItem.value
        mediaType = MediaType.fromString(
            Constant.currentCastMediaItem.value?.mimeType?.substringBefore("/")
        )
        castPath = Constant.currentCastMediaItem.value?.path
        castTitle = Constant.currentCastMediaItem.value?.title

        Log.i(TAG, "setupUI: ${Constant.currentCastMediaItem.value?.mimeType}")
        Log.i(TAG, "setupUI castTitle : ${Constant.currentCastMediaItem.value?.title}")
        Log.i(TAG, "setupUI castPath : ${Constant.currentCastMediaItem.value?.path}")

        // Determine current index in the media list for sequential play
        currentMediaIndex = Constant.castMediaList.indexOfFirst {
            it.path == Constant.currentCastMediaItem.value?.path
        }.coerceAtLeast(0)

        // If casting is already active (user re-opened from notification / mini-player),
        // just restore controls — do NOT start a new cast session on the TV.
        val alreadyCasting = Constant.isCasting.value == true

        when (mediaType) {
            MediaType.VIDEO -> {
                currentMode = Mode.VIDEO
                if (alreadyCasting) restoreVideoControls() else setupVideoUI()
            }
            MediaType.IMAGE -> {
                currentMode = Mode.IMAGE
                setupImageUI() // Images are stateless – always safe to show
            }
            MediaType.AUDIO -> {
                currentMode = Mode.VIDEO
                if (alreadyCasting) restoreAudioControls() else setupAudioUI()
            }
            else -> {
                Log.e(TAG, "setupUI: Invalid media type ")
            }
        }
    }

    /**
     * Restore video UI without re-casting. Called when activity is re-opened
     * while CastService is already running.
     */
    private fun restoreVideoControls() {
        showVideoMode()
        setupAnimations()
        binding.videoTitle.text = castTitle
        binding.videoSubtitle.text = "Video • ${formatFileSize(mediaItem?.size ?: 0)}"
        binding.videoThumbnail.load(getVideoThumbnail(castPath ?: "")) {
            crossfade(true)
            placeholder(R.drawable.ic_cast_video)
            error(R.drawable.ic_cast_video)
        }
        setupListeners()
        startMediaControlUpdates()
    }

    /**
     * Restore audio UI without re-casting.
     */
    private fun restoreAudioControls() {
        showVideoMode()
        setupAnimations()
        binding.videoTitle.text = castTitle
        binding.videoSubtitle.text = "Audio • ${formatFileSize(mediaItem?.size ?: 0)}"
        try {
            val artistImage = getAudioImage(castPath!!)
            if (artistImage != null) {
                binding.videoThumbnail.load(artistImage) { crossfade(true) }
            } else {
                binding.videoThumbnail.load(R.drawable.ic_music) { crossfade(true) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "restoreAudioControls: ${e.message}")
        }
        setupListeners()
        startMediaControlUpdates()
    }

    private fun setupVideoUI() {
        showVideoMode()
        setupAnimations()

        binding.videoTitle.text = castTitle
        binding.videoSubtitle.text = "Video • ${formatFileSize(mediaItem?.size ?: 0)}"

        binding.videoThumbnail.load(getVideoThumbnail(castPath ?: "")) {
            crossfade(true)
            placeholder(R.drawable.ic_cast_video)
            error(R.drawable.ic_cast_video)
        }

        setupVideoPlayer(castPath ?: "", castTitle ?: "", Constant.castDescription, "video/mp4")
    }

    private fun setupImageUI() {
        showImageMode()
        setupImageAnimations()
        setupImageList()
    }

    private fun setupAudioUI() {
        showVideoMode()
        setupAnimations()

        binding.videoTitle.text = castTitle
        binding.videoSubtitle.text = "Audio • ${formatFileSize(mediaItem?.size ?: 0)}"

        try {
            Log.d(TAG, "setupUI: mediaItem Audio  - ${mediaItem?.size}")
            val artistImage = getAudioImage(castPath!!)
            if (artistImage != null) {
                binding.videoThumbnail.load(artistImage) {
                    crossfade(true)
                }
            } else {
                binding.videoThumbnail.load(R.drawable.ic_music) {
                    crossfade(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupAudioUI: ${e.message}")
        }

        setupVideoPlayer(castPath ?: "", castTitle ?: "", Constant.castDescription, "audio/*")
    }

    /**
     * Orchestrates staggered content reveal animation for video mode
     */
    private fun setupAnimations() {
        binding.apply {
            mediaCard.startAnimation(
                AnimationUtils.loadAnimation(this@CastPlayerActivity, R.anim.fade_slide_up_100)
            )
            videoTitle.startAnimation(
                AnimationUtils.loadAnimation(this@CastPlayerActivity, R.anim.fade_slide_up_200)
            )
            videoSubtitle.startAnimation(
                AnimationUtils.loadAnimation(this@CastPlayerActivity, R.anim.fade_slide_up_200)
            )
            seekBarContainer.startAnimation(
                AnimationUtils.loadAnimation(this@CastPlayerActivity, R.anim.fade_slide_up_300)
            )
            mediaControls.startAnimation(
                AnimationUtils.loadAnimation(this@CastPlayerActivity, R.anim.fade_slide_up_400)
            )

        }
    }

    /**
     * Setup animations for image gallery mode
     */
    private fun setupImageAnimations() {
        binding.apply {
            imageCard.startAnimation(
                AnimationUtils.loadAnimation(this@CastPlayerActivity, R.anim.fade_slide_up_100)
            )
            galleryLabel.startAnimation(
                AnimationUtils.loadAnimation(this@CastPlayerActivity, R.anim.fade_slide_up_200)
            )
            imageHorizontalList.startAnimation(
                AnimationUtils.loadAnimation(this@CastPlayerActivity, R.anim.fade_slide_up_300)
            )
        }
    }

    private fun setupImageList() {
        Log.d(TAG, "setupImageList: ${Constant.castMediaList.size}")

        val displayCurrentImageUrl = Constant.castMediaList.firstOrNull {
            it.path == Constant.currentCastMediaItem.value?.path
        }
        Log.d(TAG, "setupImageList: displayCurrentImageURl - $displayCurrentImageUrl")

        binding.castingImageView.load(displayCurrentImageUrl?.path) {
            crossfade(true)
        }

        val linearLayoutManager = LinearLayoutManager(
            this@CastPlayerActivity,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        imageCastAdapter = ImageCastAdapter(Constant.castMediaList) { item ->
            displayImage(item, castTitle ?: "", Constant.castDescription)
        }

        binding.imageHorizontalList.layoutManager = linearLayoutManager
        binding.imageHorizontalList.adapter = imageCastAdapter

        // Add snap helper for smooth scrolling to center
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.imageHorizontalList)

        // Set initial selected position and scroll to it
        displayCurrentImageUrl?.let { currentImage ->
            currentImage.path.let { imageCastAdapter.setInitialSelectedPosition(it) }

            // Find the index of the current image
            val currentIndex = Constant.castMediaList.indexOfFirst { it.path == currentImage.path }
            if (currentIndex != -1) {
                // Scroll to the selected image with smooth animation
                binding.imageHorizontalList.post {
                    linearLayoutManager.scrollToPositionWithOffset(currentIndex, 0)
                }
            }
        }

        displayCurrentImageUrl?.let {
            displayImage(it, castTitle ?: "", Constant.castDescription)
        }

        // Swipe gestures on the large center photo to navigate prev/next
        setupPhotoSwipeGestures()
    }

    /**
     * Attaches a GestureDetector to the main [castingImageView] so the user can
     * swipe left (next photo) or right (previous photo).
     */
    private fun setupPhotoSwipeGestures() {
        val SWIPE_THRESHOLD = 100
        val gestureDetector = android.view.GestureDetector(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val diffX = e2.x - (e1?.x ?: e2.x)
                    return if (Math.abs(diffX) > SWIPE_THRESHOLD) {
                        if (diffX < 0) navigatePhoto(forward = true)
                        else navigatePhoto(forward = false)
                        true
                    } else false
                }
                override fun onDown(e: android.view.MotionEvent) = true
            }
        )
        binding.castingImageView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.performClick()
            true
        }
        binding.castingImageView.isClickable = true
    }

    /**
     * Navigates to the next ([forward]=true) or previous ([forward]=false) photo.
     * Updates both the on-screen preview and the TV via [displayImage].
     */
    private fun navigatePhoto(forward: Boolean) {
        val list = Constant.castMediaList
        if (list.isEmpty()) return
        val targetIndex = if (forward) currentMediaIndex + 1 else currentMediaIndex - 1
        if (targetIndex < 0 || targetIndex >= list.size) return

        currentMediaIndex = targetIndex
        val targetItem = list[targetIndex]
        Log.d(TAG, "navigatePhoto forward=$forward -> index=$targetIndex title=${targetItem.title}")

        Constant.currentCastMediaItem.value = targetItem

        // Update horizontal list selection
        imageCastAdapter.setInitialSelectedPosition(targetItem.path)
        val llm = binding.imageHorizontalList.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
        llm?.scrollToPositionWithOffset(targetIndex, 0)

        // Update center image with fade
        binding.castingImageView.apply {
            animate().alpha(0f).setDuration(150).withEndAction {
                load(targetItem.path) { crossfade(true) }
                animate().alpha(1f).setDuration(200).start()
            }.start()
        }

        // Cast on TV
        displayImage(targetItem, castTitle ?: "", Constant.castDescription)
    }

    private fun displayImage(item: MediaItem, title: String, description: String) {
        if (!ensureCapability(MediaPlayer.Display_Image)) {
            return
        }

        val mediaUrl = buildLocalMediaUrl(item.path) ?: run {
            Toast.makeText(
                this@CastPlayerActivity,
                "Unable to start local media server. Check Wi‑Fi.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        showCastingProgressDialog()
        Log.d(TAG, "displayImage: media url - $mediaUrl")

        try {
            mediaCastingManager.castImage(
                mediaUrl = mediaUrl,
                title = title,
                description = description,
                onError = { error ->
                    Log.e(TAG, "displayImage error: ${error.toString()}")
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(500L)
                        Toast.makeText(
                            this@CastPlayerActivity,
                            "Casting failed. Make sure your TV can access this media.",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Load the image in the center view with animation
                        binding.castingImageView.apply {
                            alpha = 0f
                            load(item.path) {
                                crossfade(true)
                            }
                            animate()
                                .alpha(1f)
                                .setDuration(300)
                                .start()
                        }

                        if (castingDialog.isShowing) {
                            castingDialog.dismiss()
                        }
                    }
                },
                onSuccess = {
                    // Set casting state to show mini player in MainActivity
                    Constant.isCasting.postValue(true)

                    // Start foreground service for notification
                    startCastService()

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (castingDialog.isShowing) {
                            castingDialog.dismiss()
                        }
                    }, 500)

                    // Load the image with fade animation
                    binding.castingImageView.apply {
                        alpha = 0f
                        load(item.path) {
                            crossfade(true)
                        }
                        animate()
                            .alpha(1f)
                            .setDuration(400)
                            .start()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "displayImage: ${e.message}")
        }
    }

    private fun setupVideoPlayer(
        castPath: String,
        title: String,
        description: String,
        mediaMimeType: String
    ) {
        val requiredCapability =
            if (mediaType == MediaType.AUDIO) MediaPlayer.Play_Audio else MediaPlayer.Play_Video
        if (!ensureCapability(requiredCapability)) {
            return
        }

        val mediaUrl = buildLocalMediaUrl(castPath) ?: run {
            Toast.makeText(
                this@CastPlayerActivity,
                "Unable to start local media server. Check Wi‑Fi.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        showCastingProgressDialog()
        setupListeners()

        val mediaInfo: MediaInfo = MediaInfo.Builder(mediaUrl, mediaItem?.mimeType ?: mediaMimeType)
            .setTitle(title)
            .setDescription(description)
            .build()

        val listener: LaunchListener = object : LaunchListener {
            override fun onSuccess(`object`: MediaLaunchObject) {
                mLaunchSession = `object`.launchSession
                mMediaControl = `object`.mediaControl

                Constant.isCasting.postValue(true)

                // Start foreground service for notification
                startCastService()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (castingDialog.isShowing) {
                        castingDialog.dismiss()
                    }
                }, 1000)

                startMediaControlUpdates()
            }

            override fun onError(error: ServiceCommandError) {
                Log.d(TAG, "Play media failure: $error")
                if (castingDialog.isShowing) {
                    castingDialog.dismiss()
                }
                Toast.makeText(
                    this@CastPlayerActivity,
                    "Casting failed. Make sure your TV can access this media.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        Constant.connectableDevice?.mediaPlayer?.playMedia(mediaInfo, false, listener)
    }

    private fun setupListeners() {
        binding.apply {
            // Enhanced Slider (replaces SeekBar)
            seekBar.addOnChangeListener { slider, value, fromUser ->
                if (fromUser && totalDuration > 0) {
                    lifecycleScope.launch {
                        try {
                            Constant.connectableDevice?.mediaControl?.seek(
                                value.toLong(),
                                object : ResponseListener<Any> {
                                    override fun onSuccess(success: Any?) {
                                        Log.d(TAG, "Seek success: $success")
                                    }

                                    override fun onError(error: ServiceCommandError?) {
                                        Log.d(TAG, "Seek error: ${error?.message}")
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "setupListeners: ${e.message}")
                        }
                    }
                } else if (fromUser) {
                    // Duration unknown/not reported yet. Keep slider stable and skip seek.
                    updateSeekBarSafely(currentPosition.toLong(), totalDuration.toLong())
                }
            }

            // Play/Pause FAB with bounce animation
            playPauseButton.setOnClickListener { view ->
                togglePlayPause()

                // Bounce animation
                view.animate()
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setDuration(100)
                    .withEndAction {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }

            // Rewind with rotation animation
            rewindButton.setOnClickListener { view ->
                view.animate()
                    .rotationBy(-15f)
                    .setDuration(150)
                    .withEndAction {
                        view.animate().rotation(0f).setDuration(150).start()
                    }
                    .start()

                lifecycleScope.launch {
                    try {
                        val newPosition = maxOf(0L, currentPosition - 10_000L)

                        Constant.connectableDevice?.mediaControl?.seek(
                            newPosition.toLong(),
                            object : ResponseListener<Any> {
                                override fun onSuccess(success: Any?) {
                                    Log.d(TAG, "Rewind success")
                                }
                                override fun onError(error: ServiceCommandError?) {
                                    Log.d(TAG, "Rewind error: ${error?.message}")
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "rewindButton: ${e.message}")
                    }
                }
            }

            // Forward with rotation animation
            forwardButton.setOnClickListener { view ->
                view.animate()
                    .rotationBy(15f)
                    .setDuration(150)
                    .withEndAction {
                        view.animate().rotation(0f).setDuration(150).start()
                    }
                    .start()

                lifecycleScope.launch {
                    try {
                        val newPosition = minOf(totalDuration.toLong(), currentPosition + 10000L)
                        Constant.connectableDevice?.mediaControl?.seek(
                            newPosition.toLong(),
                            object : ResponseListener<Any> {
                                override fun onSuccess(success: Any?) {
                                    Log.d(TAG, "Forward success")
                                }
                                override fun onError(error: ServiceCommandError?) {
                                    Log.d(TAG, "Forward error: ${error?.message}")
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "forwardButton: ${e.message}")
                    }
                }
            }

        }
    }

    /**
     * Show video mode with smooth transition
     */
    private fun showVideoMode() {
        binding.apply {
            videoScrollView.isVisible = true
            imageScrollView.isVisible = false
        }
    }

    /**
     * Show image mode with smooth transition
     */
    private fun showImageMode() {
        binding.apply {
            videoScrollView.isVisible = false
            imageScrollView.isVisible = true
        }
    }

    /**
     * Switch between video and image modes with Material Shared Axis transition
     */
    private fun switchMode(newMode: Mode) {
        if (newMode == currentMode) return

        binding.apply {
            when (newMode) {
                Mode.VIDEO -> {
                    // Animate OUT image view
                    imageScrollView.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            imageScrollView.isVisible = false

                            // Animate IN video view
                            videoScrollView.alpha = 0f
                            videoScrollView.isVisible = true
                            videoScrollView.animate()
                                .alpha(1f)
                                .setDuration(300)
                                .start()
                        }
                        .start()
                }
                Mode.IMAGE -> {
                    // Animate OUT video view
                    videoScrollView.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            videoScrollView.isVisible = false

                            // Animate IN image view
                            imageScrollView.alpha = 0f
                            imageScrollView.isVisible = true
                            imageScrollView.animate()
                                .alpha(1f)
                                .setDuration(300)
                                .start()
                        }
                        .start()
                }
            }
        }

        currentMode = newMode
    }

    private fun showCastingProgressDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_casting_progress, null)
        castingProgressIndicator = dialogView.findViewById(R.id.castingProgressIndicator)

        castingProgressDialog = MaterialAlertDialogBuilder(this@CastPlayerActivity)
            .setView(dialogView)
            .setCancelable(true)

        castingDialog = castingProgressDialog.create()
        castingDialog.show()
    }

    private fun updateMediaSliderProgress() {
        lifecycleScope.launch {
            Constant.connectableDevice?.mediaControl?.getPosition(object : PositionListener {
                override fun onError(error: ServiceCommandError?) {
                    Log.e(TAG, "getPosition error")
                }

                override fun onSuccess(position: Long?) {
                    Constant.connectableDevice?.mediaControl?.getDuration(object : DurationListener {
                        override fun onError(error: ServiceCommandError?) {
                            Log.e(TAG, "getDuration error")
                        }

                        override fun onSuccess(duration: Long?) {
                            currentPosition = position?.toInt() ?: 0
                            totalDuration = duration?.toInt() ?: 0

                            // Format the durations
                            val formattedPosition = formatTime(currentPosition.toLong())
                            val formattedDuration = formatTime(totalDuration.toLong())

                            // Update UI
                            binding.currentPositionText.text = formattedPosition
                            binding.duration.text = formattedDuration
                            updateSeekBarSafely(
                                positionMs = currentPosition.toLong(),
                                durationMs = totalDuration.toLong()
                            )

                            // Update play/pause button state
                            updatePlayPauseButtonState()
                        }
                    })
                }
            })
        }
    }

    private fun updateSeekBarSafely(positionMs: Long, durationMs: Long) {
        val hasValidDuration = durationMs > 0L
        val safeValueTo = if (hasValidDuration) durationMs.toFloat() else MIN_SLIDER_VALUE_TO
        val safePosition = if (hasValidDuration) {
            positionMs.coerceIn(0L, durationMs).toFloat()
        } else {
            0f
        }

        binding.seekBar.apply {
            // Material Slider requires valueFrom < valueTo.
            if (valueTo != safeValueTo) {
                valueTo = safeValueTo
            }
            value = safePosition
            isEnabled = hasValidDuration
        }
    }

    private fun updatePlayPauseButtonState() {
        lifecycleScope.launch {
            try {
                Constant.connectableDevice?.mediaControl?.getPlayState(object : PlayStateListener {
                    override fun onError(error: ServiceCommandError?) {
                        Log.e(TAG, "getPlayState error")
                    }

                    override fun onSuccess(playState: MediaControl.PlayStateStatus?) {
                        val iconRes = when (playState) {
                            MediaControl.PlayStateStatus.Playing -> R.drawable.ic_pause
                            MediaControl.PlayStateStatus.Paused -> R.drawable.ic_play
                            else -> return
                        }
                        binding.playPauseButton.setImageResource(iconRes)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "updatePlayPauseButtonState: ${e.message}")
            }
        }
    }

    private fun startMediaControlUpdates() {
        if (!connectableDevice.hasCapability(MediaControl.Any)) {
            return
        }
        handler.post(updateSliderRunnable)
        // Subscribe to play-state changes to enable sequential playback
        subscribeToPlayState()
    }

    /**
     * Subscribes to the TV's play-state changes.
     * When the current media finishes (Finished status), automatically advances
     * to the next item in Constant.castMediaList.
     */
    private fun subscribeToPlayState() {
        try {
            playStateSubscription?.unsubscribe()
            playStateSubscription = mMediaControl
                ?.subscribePlayState(object : MediaControl.PlayStateListener {
                    override fun onError(error: ServiceCommandError?) {
                        Log.e(TAG, "subscribePlayState error: ${error?.message}")
                    }
                    override fun onSuccess(playState: MediaControl.PlayStateStatus?) {
                        Log.d(TAG, "PlayState: $playState")
                        if (playState == MediaControl.PlayStateStatus.Finished) {
                            handler.post { playNextMedia() }
                        }
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "subscribeToPlayState: ${e.message}")
        }
    }

    /**
     * Advances to the next item in the media list and starts casting it.
     * Stops cleanly if we have reached the end of the list.
     */
    private fun playNextMedia() {
        val list = Constant.castMediaList
        if (list.isEmpty()) return
        val nextIndex = currentMediaIndex + 1
        if (nextIndex >= list.size) {
            Log.d(TAG, "playNextMedia: reached end of playlist")
            return
        }
        currentMediaIndex = nextIndex
        val nextItem = list[nextIndex]
        Log.d(TAG, "playNextMedia: advancing to [$nextIndex] ${nextItem.title}")

        Constant.currentCastMediaItem.value = nextItem
        castTitle = nextItem.title
        castPath = nextItem.path
        mediaItem = nextItem
        supportActionBar?.title = nextItem.title

        // Update thumbnail
        when (mediaType) {
            MediaType.VIDEO -> {
                binding.videoTitle.text = castTitle
                binding.videoSubtitle.text = "Video • ${formatFileSize(nextItem.size)}"
                binding.videoThumbnail.load(getVideoThumbnail(castPath ?: "")) {
                    crossfade(true)
                    placeholder(R.drawable.ic_cast_video)
                    error(R.drawable.ic_cast_video)
                }
                setupVideoPlayer(castPath ?: "", castTitle ?: "", Constant.castDescription, "video/mp4")
            }
            MediaType.AUDIO -> {
                binding.videoTitle.text = castTitle
                binding.videoSubtitle.text = "Audio • ${formatFileSize(nextItem.size)}"
                try {
                    val artistImage = getAudioImage(castPath!!)
                    binding.videoThumbnail.load(artistImage ?: R.drawable.ic_music) { crossfade(true) }
                } catch (e: Exception) {
                    binding.videoThumbnail.load(R.drawable.ic_music) { crossfade(true) }
                }
                setupVideoPlayer(castPath ?: "", castTitle ?: "", Constant.castDescription, "audio/*")
            }
            else -> {}
        }
    }

    private fun ensureCapability(capability: String): Boolean {
        if (!connectableDevice.hasCapability(capability)) {
            Toast.makeText(
                this@CastPlayerActivity,
                "This device doesn't support this media type.",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        return true
    }

    private fun buildLocalMediaUrl(path: String): String? {
        val app = application as App
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val isServerEnabled = prefs.getBoolean("local_media_server", true)
        if (!isServerEnabled) {
            return null
        }
        val host = app.ensureLocalMediaServer() ?: return null
        val root = Environment.getExternalStorageDirectory().absolutePath
        val relative = if (path.startsWith(root)) {
            path.substring(root.length)
        } else {
            path
        }
        val normalized = if (relative.startsWith("/")) relative else "/$relative"
        val encoded = Uri.encode(normalized, "/")
        return "http://$host:${Constant.PORT}$encoded"
    }

    private fun togglePlayPause() {
        // Route through CastService — it updates isPlaying LiveData + notification icon.
        val intent = Intent(this, CastService::class.java).apply {
            action = CastService.ACTION_TOGGLE_PAUSE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CastService for toggle-pause: ${e.message}", e)
        }
        // Poll updated play state to refresh the in-activity button icon
        handler.postDelayed({ updatePlayPauseButtonState() }, 400)
    }

    private fun formatFileSize(size: Long): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$size B"
        }
    }

    private fun startService(context: Context, command: String?) {
        val intent = Intent(context, CastService::class.java)
        intent.action = command
        try {
            Log.d(TAG, "startService")
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startService: ${e.message}")
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e2: Exception) {
                Log.e(TAG, "ContextCompat.startForegroundService failed: ${e2.message}", e2)
            }
        }
    }

    /**
     * Starts the CastService as a foreground service to show the persistent notification.
     * The service stays alive even when this activity is closed so the user can control
     * playback from the notification.
     */
    private fun startCastService() {
        val intent = Intent(this, CastService::class.java).apply {
            action = CastService.ACTION_START_CASTING
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CastService: ${e.message}", e)
        }
    }

    private fun stopCastingSession() {
        val intent = Intent(this, CastService::class.java).apply {
            action = CastService.ACTION_QUIT
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the slider update loop to prevent memory leaks.
        // The CastService keeps running so the notification stays visible.
        handler.removeCallbacks(updateSliderRunnable)
        // Unsubscribe play-state listener
        try {
            playStateSubscription?.unsubscribe()
        } catch (e: Exception) {
            Log.e(TAG, "Error unsubscribing playState: ${e.message}")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}
