package com.out.androidtvremote.ui.frags.cast.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.card.MaterialCardView
import com.out.androidtvremote.R
import com.out.androidtvremote.interfaces.IFTDeviceAppListener
import com.out.androidtvremote.ui.frags.cast.data.MediaItem
import com.out.androidtvremote.utils.Constant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class ImageAdapter(
    private val onCastItemClickListener: IFTDeviceAppListener.onCastItemClickListener<MediaItem>,
    private var isVideo: Boolean
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
    var tag = "ImageAdapter"

    private var mediaItems: List<MediaItem> = listOf()
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val mediaItem = mediaItems[position]
        holder.bind(mediaItem, position == selectedPosition)

        holder.itemView.setOnClickListener {
            // Update cast media list only if needed
            if (Constant.castMediaList != mediaItems) {
                Timber.tag(tag).d("Updating cast media list with %d items", mediaItems.size)
                Constant.castMediaList.clear()
                Constant.castMediaList.addAll(mediaItems)
            }

            // Update selection state
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition

            // Notify changes for smooth animation
            if (previousPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousPosition)
            }
            notifyItemChanged(selectedPosition)

            onCastItemClickListener.onCastItemClick(mediaItem)
        }
    }

    override fun getItemCount(): Int = mediaItems.size

    fun submitList(newMediaItems: List<MediaItem>) {
        val diffCallback = MediaItemDiffCallback(mediaItems, newMediaItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        mediaItems = newMediaItems
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Set the initial selected position (e.g., when opening an image from gallery)
     */
    fun setSelectedPosition(path: String) {
        val position = mediaItems.indexOfFirst { it.path == path }
        if (position != -1 && position != selectedPosition) {
            val previousPosition = selectedPosition
            selectedPosition = position

            if (previousPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousPosition)
            }
            notifyItemChanged(selectedPosition)
        }
    }

    // DiffUtil callback for efficient list updates
    private class MediaItemDiffCallback(
        private val oldList: List<MediaItem>,
        private val newList: List<MediaItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageItem)
        private val mediaTypeBadge: MaterialCardView = itemView.findViewById(R.id.mediaTypeBadge)
        private val ivMediaType: ImageView = itemView.findViewById(R.id.iv_video)
        private val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)
        private val selectionCheckmark: MaterialCardView = itemView.findViewById(R.id.selectionCheckmark)

        fun bind(mediaItem: MediaItem, isSelected: Boolean) {
            Timber.tag(tag).d("bind: %s", mediaItem.path)
            Timber.tag(tag).d("bind: mimeType %s", mediaItem.mimeType)

            // Load thumbnail based on media type
            when (mediaItem.mimeType.substringBefore("/")) {
                "image" -> {
                    loadImageThumbnail(mediaItem)
                    mediaTypeBadge.visibility = View.GONE
                }

                "video" -> {
                    loadVideoThumbnail(mediaItem)
                    showMediaBadge(R.drawable.ic_cast_video)
                }

                "audio" -> {
                    loadAudioThumbnail(mediaItem)
                    showMediaBadge(R.drawable.ic_music)
                }

                else -> {
                    imageView.setImageResource(R.drawable.ic_cast_photo)
                    mediaTypeBadge.visibility = View.GONE
                }
            }

            // Update selection state with animation
            updateSelectionState(isSelected)
        }

        private fun loadImageThumbnail(mediaItem: MediaItem) {
            imageView.load(mediaItem.path) {
                crossfade(true)
                size(256, 256)
                placeholder(R.drawable.ic_cast_photo)
                error(R.drawable.ic_cast_photo)
                memoryCachePolicy(CachePolicy.ENABLED)
                diskCachePolicy(CachePolicy.ENABLED)
                allowHardware(true)
            }
        }

        private fun loadVideoThumbnail(mediaItem: MediaItem) {
            Glide.with(itemView.context)
                .load(mediaItem.path)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.drawable.ic_cast_photo)
                .error(R.drawable.ic_cast_photo)
                .override(256, 256)
                .centerCrop()
                .into(imageView)
        }

        private fun loadAudioThumbnail(mediaItem: MediaItem) {
            // For audio, show a generic music icon or album art if available
            imageView.load(R.drawable.ic_music) {
                crossfade(true)
            }
        }

        private fun showMediaBadge(iconRes: Int) {
            mediaTypeBadge.visibility = View.VISIBLE
            ivMediaType.setImageResource(iconRes)
        }

        private fun updateSelectionState(isSelected: Boolean) {
            if (isSelected) {
                // Show selection overlay with animation
                selectionOverlay.visibility = View.VISIBLE
                selectionOverlay.animate()
                    .alpha(0.3f)
                    .setDuration(200)
                    .start()

                // Show checkmark with scale animation
                selectionCheckmark.visibility = View.VISIBLE
                selectionCheckmark.scaleX = 0f
                selectionCheckmark.scaleY = 0f
                selectionCheckmark.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .start()
            } else {
                // Hide selection overlay
                selectionOverlay.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        selectionOverlay.visibility = View.GONE
                    }
                    .start()

                // Hide checkmark
                selectionCheckmark.animate()
                    .scaleX(0f)
                    .scaleY(0f)
                    .setDuration(150)
                    .withEndAction {
                        selectionCheckmark.visibility = View.GONE
                    }
                    .start()
            }
        }
    }
}