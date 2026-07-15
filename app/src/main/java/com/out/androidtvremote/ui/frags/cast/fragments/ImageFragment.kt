package com.out.androidtvremote.ui.frags.cast.fragments

import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.out.androidtvremote.App
import com.out.androidtvremote.R
import com.out.androidtvremote.interfaces.IFTDeviceAppListener
import com.out.androidtvremote.ui.activities.SearchActivity
import com.out.androidtvremote.ui.frags.cast.adapters.ImageAdapter
import com.out.androidtvremote.ui.frags.cast.data.MediaItem
import com.out.androidtvremote.ui.activities.player.CastPlayerActivity
import com.out.androidtvremote.utils.Constant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ImageFragment : Fragment(), IFTDeviceAppListener.onCastItemClickListener<MediaItem> {
    private var TAG = ImageFragment::class.java.simpleName
    private lateinit var recyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var folderId: String
    private val mediaItems = mutableListOf<MediaItem>()
    private var isLoading = false
    private val pageSize = 50 // Load images in batches

    lateinit var app: App
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = requireActivity().application as App
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_image_folder, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewImages)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve folder ID from arguments
        folderId = requireArguments().getString("folderId", "") ?: ""
        Log.d(TAG, "onViewCreated: folderId $folderId")
        
        // Initialize RecyclerView with optimizations
        val gridLayoutManager = GridLayoutManager(requireContext(), 3)
        recyclerView.apply {
            layoutManager = gridLayoutManager
            setHasFixedSize(true) // Performance optimization
            setItemViewCacheSize(20) // Cache more items
        }
        
        imageAdapter = ImageAdapter(this, false)
        recyclerView.adapter = imageAdapter

        // Load images from the specified folder
        lifecycleScope.launch(Dispatchers.IO) {
            loadImagesFromFolder(folderId)
        }
    }


    private fun loadImagesFromFolder(folderId: String) {
        if (isLoading) return
        isLoading = true

        val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        
        // Optimized projection - only request needed columns
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.TITLE,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(folderId)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor: Cursor? = requireActivity().contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.TITLE)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                val tempList = mutableListOf<MediaItem>()
                var batchCount = 0

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val mediaItem = MediaItem(
                        id = id,
                        title = cursor.getString(titleColumn) ?: "Unknown Title",
                        displayName = cursor.getString(displayNameColumn) ?: "Unknown",
                        path = cursor.getString(dataColumn),
                        uri = contentUri,
                        mimeType = cursor.getString(mimeTypeColumn) ?: "image/jpeg",
                        size = cursor.getLong(sizeColumn),
                        duration = 0L,
                        resolution = null,
                        dateTaken = 0L,
                        dateAdded = cursor.getLong(dateAddedColumn),
                        dateModified = 0L,
                        bucketId = cursor.getString(bucketIdColumn),
                        bucketName = "",
                        orientation = 0,
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        thumbnailUri = null
                    )

                    tempList.add(mediaItem)
                    batchCount++

                    // Update UI in batches for smoother loading
                    if (batchCount >= pageSize) {
                        val batch = tempList.toList()
                        lifecycleScope.launch(Dispatchers.Main) {
                            mediaItems.addAll(batch)
                            imageAdapter.submitList(mediaItems.toList())
                        }
                        tempList.clear()
                        batchCount = 0
                    }
                }

                // Add remaining items
                if (tempList.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        mediaItems.addAll(tempList)
                        imageAdapter.submitList(mediaItems.toList())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading images: ${e.message}", e)
        } finally {
            isLoading = false
        }
    }


    companion object {
        fun newInstance(folderId: String): ImageFragment {
            val fragment = ImageFragment()
            val args = Bundle()
            args.putString("folderId", folderId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCastItemClick(item: MediaItem) {
        if (Constant.connectableDevice?.isConnected == true && Constant.connectableDevice != null) {
            Constant.currentCastMediaItem.value = item
            openCastPlayerActivity()
        } else {
            openSearchActivity()
        }

    }

    fun openCastPlayerActivity() {  startActivity(Intent(requireContext(), CastPlayerActivity::class.java))
        requireActivity().overridePendingTransition(
            R.anim.slide_in_bottom,
            R.anim.slide_out_bottom
        )


    }

    fun openSearchActivity() {  startActivity(Intent(requireContext(), SearchActivity::class.java))
        requireActivity().overridePendingTransition(
            R.anim.slide_in_bottom,
            R.anim.slide_out_bottom
        )
    }

}
