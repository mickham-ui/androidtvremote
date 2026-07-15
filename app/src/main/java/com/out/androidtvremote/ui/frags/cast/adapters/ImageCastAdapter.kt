package com.out.androidtvremote.ui.frags.cast.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.out.androidtvremote.R
import com.out.androidtvremote.ui.frags.cast.data.MediaItem

class ImageCastAdapter(
    private val images: ArrayList<MediaItem>,
    private val onImageClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<ImageCastAdapter.ImageViewHolder>() {
    val TAG = ImageCastAdapter::class.simpleName
    private var selectedPosition = RecyclerView.NO_POSITION
    

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cast_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder: $images")
        holder.bind(images[position])
    }

    override fun getItemCount() = images.size


    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var imageView: ImageView = view.findViewById(R.id.imageItem)
        var cardView: MaterialCardView = view.findViewById(R.id.imageItemCard)

        fun bind(item: MediaItem) {
            Glide.with(itemView.context).load(item.path).into(imageView)


            // Handle selection state
            if (selectedPosition == position) {
                // Highlighted state
                cardView.strokeWidth = 4
                cardView.strokeColor = ContextCompat.getColor(
                    itemView.context,
                    R.color.md_theme_primary
                )
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.md_theme_primaryContainer
                    )
                )
            } else {
                // Normal state
                cardView.strokeWidth = 0
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.transparent
                    )
                )
            }
            // Click listener
            itemView.setOnClickListener {
                // Update selection
                val previousSelected = selectedPosition
                selectedPosition = adapterPosition

                // Notify items that need to be redrawn
                if (previousSelected != RecyclerView.NO_POSITION) {
                    notifyItemChanged(previousSelected)
                }
                notifyItemChanged(selectedPosition)
                // Invoke click callback
                onImageClick(item)

            }
        }
    }

    // Method to set initial selected position
    fun setInitialSelectedPosition(path: String) {
        selectedPosition = images.indexOfFirst { it.path == path }
        notifyDataSetChanged()
    }
}
