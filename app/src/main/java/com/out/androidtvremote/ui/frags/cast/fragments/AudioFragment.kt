package com.out.androidtvremote.ui.frags.cast.fragments

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.connectsdk.service.capability.MediaControl
import com.connectsdk.service.sessions.LaunchSession
import com.out.androidtvremote.App
import com.out.androidtvremote.R
import com.out.androidtvremote.databinding.FragmentAudioBinding
import com.out.androidtvremote.interfaces.BottomNavigationListener
import com.out.androidtvremote.interfaces.IFTDeviceAppListener
import com.out.androidtvremote.ui.activities.SearchActivity
import com.out.androidtvremote.ui.frags.cast.adapters.AudioAdapter
import com.out.androidtvremote.ui.frags.cast.data.MediaItem
import com.out.androidtvremote.ui.activities.player.CastPlayerActivity
import com.out.androidtvremote.utils.Constant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioFragment : Fragment(), IFTDeviceAppListener.onCastItemClickListener<MediaItem> {

    private var _binding: FragmentAudioBinding? = null
    private var bottomNavigationListener: BottomNavigationListener? = null

    private val binding get() = _binding!!
    private var TAG = AudioFragment::class.java.simpleName

    private lateinit var audioAdapter: AudioAdapter
    private lateinit var app: App

    // Job for managing coroutines
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = requireActivity().application as App
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = FragmentAudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onDetach() {
        super.onDetach()
        bottomNavigationListener = null
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            findNavController().popBackStack()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: ")

        setupRecyclerView()
        loadAudioFiles()
    }

    private fun setupRecyclerView() {
        binding.recyclerViewAudio.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

            // Enable RecyclerView optimizations
            setHasFixedSize(true)
            setItemViewCacheSize(20)

            // Enable predictive animations for smoother scrolling
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

            audioAdapter = AudioAdapter(requireContext(), this@AudioFragment)
            adapter = audioAdapter
        }
    }

    private fun loadAudioFiles() {
        // Show loading state
        showLoading()

        // Cancel any existing load job
        loadJob?.cancel()

        // Launch in viewLifecycleOwner scope for automatic cancellation
        loadJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val audioFiles = getAllAudioFromDevice()

                // Update UI on Main thread
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        if (audioFiles.isEmpty()) {
                            showEmptyState()
                        } else {
                            showContent()
                            audioAdapter.setAudioList(audioFiles)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading audio files: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        showEmptyState()
                    }
                }
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewAudio.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
    }

    private fun showEmptyState() {
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewAudio.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE

        Log.d(TAG, "showEmptyState: No audio files found on device")
    }

    private fun showContent() {
        binding.progressBar.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.recyclerViewAudio.visibility = View.VISIBLE
    }

    private suspend fun getAllAudioFromDevice(): List<MediaItem> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getAllAudioFromDevice: Start fetching audio files")

        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val mediaItems = mutableListOf<MediaItem>()

        try {
            requireActivity().contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val mediaItem = MediaItem(
                        id = id,
                        title = cursor.getString(titleColumn) ?: "Unknown Title",
                        displayName = cursor.getString(displayNameColumn) ?: "Unknown",
                        path = cursor.getString(dataColumn),
                        uri = contentUri,
                        mimeType = cursor.getString(mimeTypeColumn) ?: "audio/mpeg",
                        size = cursor.getLong(sizeColumn),
                        duration = cursor.getLong(durationColumn),
                        resolution = null,
                        dateTaken = 0L,
                        dateAdded = cursor.getLong(dateAddedColumn),
                        dateModified = cursor.getLong(dateModifiedColumn),
                        bucketId = "",
                        bucketName = "",
                        orientation = 0,
                        width = 0,
                        height = 0,
                        thumbnailUri = null,
                        album = cursor.getString(albumColumn),
                        artist = cursor.getString(artistColumn)
                    )

                    mediaItems.add(mediaItem)
                }

                Log.d(TAG, "getAllAudioFromDevice: Found ${mediaItems.size} audio files")
            } ?: Log.e(TAG, "getAllAudioFromDevice: Cursor is null")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying audio files: ${e.message}")
        }

        return@withContext mediaItems
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.clear()
    }

    override fun onCastItemClick(item: MediaItem) {
        if (Constant.connectableDevice?.isConnected == true && Constant.connectableDevice != null) {
            Constant.currentCastMediaItem.value = item
            openCastPlayerActivity()
        } else {
            openSearchActivity()
        }
    }

    private fun openCastPlayerActivity() {navigateToCastPlayer()

    }

    private fun navigateToCastPlayer() {
        startActivity(Intent(requireContext(), CastPlayerActivity::class.java))
        requireActivity().overridePendingTransition(
            R.anim.slide_in_bottom,
            R.anim.slide_out_bottom
        )
    }

    private fun openSearchActivity() {navigateToSearch()

    }

    private fun navigateToSearch() {
        startActivity(Intent(requireContext(), SearchActivity::class.java))
        requireActivity().overridePendingTransition(
            R.anim.slide_in_bottom,
            R.anim.slide_out_bottom
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Cancel any ongoing load operations
        loadJob?.cancel()

        // Clear adapter cache to free memory
        if (::audioAdapter.isInitialized) {
            audioAdapter.clearCache()
        }

        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
