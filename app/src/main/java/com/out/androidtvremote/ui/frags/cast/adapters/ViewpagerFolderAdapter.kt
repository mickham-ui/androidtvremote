package com.out.androidtvremote.ui.frags.cast.adapters

import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.out.androidtvremote.ui.frags.cast.fragments.ImageFragment
import com.out.androidtvremote.ui.frags.cast.data.ImageFolder

class ViewpagerFolderAdapter(fragment: Fragment, private val imageFolders: List<ImageFolder>) : FragmentStateAdapter(fragment) {

    private val imageLists = mutableListOf<List<Uri>>() // List of images for each folder

    override fun getItemCount(): Int = imageFolders.size

    override fun createFragment(position: Int): Fragment {
        return ImageFragment.newInstance(imageFolders[position].id)
    }

    fun updateImages(images: List<List<Uri>>) {
        imageLists.clear()
        imageLists.addAll(images)
        notifyDataSetChanged()
    }
}
