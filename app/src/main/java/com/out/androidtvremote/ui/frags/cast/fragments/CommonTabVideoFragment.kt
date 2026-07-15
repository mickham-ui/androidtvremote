package com.out.androidtvremote.ui.frags.cast.fragments

import android.content.Context
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
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.out.androidtvremote.databinding.FragmentVideoBinding
import com.out.androidtvremote.ui.frags.cast.data.VideoFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class CommonTabVideoFragment : Fragment() {

    private var TAG = CommonTabVideoFragment::class.java.simpleName

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewpagerFolderAdapter: ViewpagerFolderAdapter
    private var videoFolders: List<VideoFolder>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onDetach() {
        super.onDetach()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadVideoFolders()
    }

    private fun loadVideoFolders() {
        // Show loading state
        showLoading()

        CoroutineScope(Dispatchers.IO).launch {
            val folders = getAllVideoFolders(requireContext())

            withContext(Dispatchers.Main) {
                videoFolders = folders

                if (folders.isEmpty()) {
                    // Show empty state
                    showEmptyState()
                } else {
                    // Show content with data
                    showContent(folders)
                }
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
    }

    private fun showEmptyState() {
        binding.progressBar.visibility = View.GONE
        binding.contentLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE

        Log.d(TAG, "showEmptyState: No videos found on device")
    }

    private fun showContent(folders: List<VideoFolder>) {
        binding.progressBar.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE

        // Set up ViewPager2 adapter
        viewpagerFolderAdapter = ViewpagerFolderAdapter(this, folders)
        binding.viewPager.adapter = viewpagerFolderAdapter

        // Set up TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = folders[position].name
        }.attach()

        Log.d(TAG, "showContent: Loaded ${folders.size} video folders")
    }

    private fun getAllVideoFolders(context: Context): List<VideoFolder> {
        Log.d(TAG, "getAllVideoFolders: Starting query")
        val videoFolders = mutableListOf<VideoFolder>()
        val projection = arrayOf(
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )
        val selection = "${MediaStore.Video.Media.MIME_TYPE} IN (?)"
        val selectionArgs = arrayOf("video/mp4")
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val folderMap = mutableMapOf<String, String>()

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                    )
                    val bucketName = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    )
                    val path = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    )

                    if (bucketId != null && bucketName != null) {
                        if (!folderMap.containsKey(bucketId)) {
                            folderMap[bucketId] = bucketName
                            videoFolders.add(VideoFolder(bucketId, bucketName, path))
                            Log.d(TAG, "getAllVideoFolders: Found folder - $bucketName")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllVideoFolders: Error querying media", e)
        }

        Log.d(TAG, "getAllVideoFolders: Total folders found - ${videoFolders.size}")
        return videoFolders
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.clear()
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            findNavController().popBackStack()
        }
        return super.onOptionsItemSelected(item)
    }

    inner class ViewpagerFolderAdapter(
        fragment: Fragment,
        private val videoFolders: List<VideoFolder>
    ) : FragmentStateAdapter(fragment) {

        private val imageLists = mutableListOf<List<Uri>>()

        override fun getItemCount(): Int = videoFolders.size

        override fun createFragment(position: Int): Fragment {
            return VideoFragment.newInstance(videoFolders[position].id)
        }

        fun updateImages(images: List<List<Uri>>) {
            imageLists.clear()
            imageLists.addAll(images)
            notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

