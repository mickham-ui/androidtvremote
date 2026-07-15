package com.out.androidtvremote.ui.frags.cast.fragments

import android.content.Context
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
import com.google.android.material.tabs.TabLayoutMediator
import com.out.androidtvremote.databinding.FragmentImagesBinding
import com.out.androidtvremote.ui.frags.cast.adapters.ViewpagerFolderAdapter
import com.out.androidtvremote.ui.frags.cast.data.ImageFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommonTabImageFragment : Fragment() {
    private var TAG = CommonTabImageFragment::class.java.simpleName

    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewpagerFolderAdapter: ViewpagerFolderAdapter
    private var imageFolders: List<ImageFolder>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = FragmentImagesBinding.inflate(inflater, container, false)
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

        loadImageFolders()
    }

    private fun loadImageFolders() {
        // Show loading state
        showLoading()

        CoroutineScope(Dispatchers.IO).launch {
            val folders = getAllImageFolders(requireContext())

            withContext(Dispatchers.Main) {
                imageFolders = folders

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

        Log.d(TAG, "showEmptyState: No images found on device")
    }

    private fun showContent(folders: List<ImageFolder>) {
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

        Log.d(TAG, "showContent: Loaded ${folders.size} image folders")
    }

    private fun getAllImageFolders(context: Context): List<ImageFolder> {
        Log.d(TAG, "getAllImageFolders: Starting query")
        val imageFolders = mutableListOf<ImageFolder>()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        val selection = "${MediaStore.Images.Media.MIME_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf("image/jpeg", "image/png")
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val folderMap = mutableMapOf<String, String>()

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                    )
                    val bucketName = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    )
                    val path = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    )

                    if (bucketId != null && bucketName != null) {
                        if (!folderMap.containsKey(bucketId)) {
                            folderMap[bucketId] = bucketName
                            imageFolders.add(ImageFolder(bucketId, bucketName, path))
                            Log.d(TAG, "getAllImageFolders: Found folder - $bucketName")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllImageFolders: Error querying media", e)
        }

        Log.d(TAG, "getAllImageFolders: Total folders found - ${imageFolders.size}")
        return imageFolders
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            findNavController().popBackStack()
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
