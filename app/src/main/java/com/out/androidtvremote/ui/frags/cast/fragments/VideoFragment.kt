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
import com.connectsdk.service.capability.MediaControl
import com.connectsdk.service.sessions.LaunchSession
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

class VideoFragment : Fragment(), IFTDeviceAppListener.onCastItemClickListener<MediaItem> {
    private var TAG = VideoFragment::class.java.simpleName
    private lateinit var recyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var folderId: String
    private var mLaunchSession: LaunchSession? = null
    private var mMediaControl: MediaControl? = null
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
        // Initialize RecyclerView and adapter
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        imageAdapter = ImageAdapter(this, true)
        recyclerView.adapter = imageAdapter

        // Load images from the specified folder

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            loadVideosFromFolder(folderId)
        }
    }

//    private fun loadVideosFromFolder(folderId: String) {
//        val uri: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
//        val projection = arrayOf(MediaStore.MediaColumns.DATA)
//        val selection = "${MediaStore.Video.Media.BUCKET_ID} =?"
//        val selectionArgs = arrayOf(folderId)
//        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
//        val cursor: Cursor? = requireActivity().contentResolver.query(
//            uri,
//            projection,
//            selection,
//            selectionArgs,
//            sortOrder
//        )
//
//        cursor?.use {
//            val columnIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
//            val imageList = mutableListOf<String>()
//            while (it.moveToNext()) {
//                val imagePath = it.getString(columnIndex)
//                imageList.add(imagePath)
//            }
//            CoroutineScope(Dispatchers.Main).launch {
//
//                imageAdapter.setImageList(imageList)
//            }
//        }
//    }


    private fun loadVideosFromFolder(folderId: String) {
        val uri: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.ORIENTATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val selection = "${MediaStore.Video.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(folderId)
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val resolver = context?.contentResolver ?: return
        val cursor: Cursor? = resolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { cursor ->
            val mediaItems = mutableListOf<MediaItem>()

            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val resolutionColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ORIENTATION)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val thumbnailUri = getThumbnailUri(id)

                val mediaItem = MediaItem(
                    id = id,
                    title = cursor.getString(titleColumn) ?: "Unknown Title",
                    displayName = cursor.getString(displayNameColumn) ?: "Unknown",
                    path = cursor.getString(dataColumn),
                    uri = contentUri,
                    mimeType = cursor.getString(mimeTypeColumn) ?: "video/mp4",
                    size = cursor.getLong(sizeColumn),
                    duration = cursor.getLong(durationColumn),
                    resolution = cursor.getString(resolutionColumn),
                    dateTaken = cursor.getLong(dateTakenColumn),
                    dateAdded = cursor.getLong(dateAddedColumn),
                    dateModified = cursor.getLong(dateModifiedColumn),
                    bucketId = cursor.getString(bucketIdColumn),
                    bucketName = cursor.getString(bucketNameColumn),
                    orientation = cursor.getInt(orientationColumn),
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn),
                    thumbnailUri = thumbnailUri
                )

                mediaItems.add(mediaItem)
            }

            if (!isAdded) return@use
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                imageAdapter.submitList(mediaItems)
            }
        }
    }


    private fun getThumbnailUri(videoId: Long): Uri? {
        val resolver = context?.contentResolver ?: return null
        val thumbnailCursor = resolver.query(
            MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Thumbnails.DATA),
            "${MediaStore.Video.Thumbnails.VIDEO_ID} = ?",
            arrayOf(videoId.toString()),
            null
        )

        thumbnailCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val thumbnailPath = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Thumbnails.DATA)
                )
                return Uri.parse(thumbnailPath)
            }
        }

        return null
    }

    companion object {
        fun newInstance(folderId: String): VideoFragment {
            val fragment = VideoFragment()
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

    fun openCastPlayerActivity() { startActivity(Intent(requireContext(), CastPlayerActivity::class.java))
        requireActivity().overridePendingTransition(
            R.anim.slide_in_bottom,
            R.anim.slide_out_bottom
        )
    }

    fun openSearchActivity() {
        startActivity(Intent(requireContext(), SearchActivity::class.java))
        requireActivity().overridePendingTransition(
            R.anim.slide_in_bottom,
            R.anim.slide_out_bottom
        )

    }
}
