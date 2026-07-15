package com.out.androidtvremote.ui.frags.cast.adapters


import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.out.androidtvremote.R
import com.out.androidtvremote.interfaces.IFTDeviceAppListener
import com.out.androidtvremote.ui.frags.cast.data.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioAdapter(
    private val context: Context,
    private val onCastItemClickListener: IFTDeviceAppListener.onCastItemClickListener<MediaItem>
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    private val adapterJob = SupervisorJob()
    private val adapterScope = CoroutineScope(adapterJob + Dispatchers.Main)

    private var mediaItems: List<MediaItem> = listOf()

    // Cache for album art to avoid repeated extraction
    private val albumArtCache = LruCache<String, ByteArray>(50) // Cache 50 images

    // Cache for durations
    private val durationCache = mutableMapOf<String, String>()

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterJob.cancel()
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val audio = mediaItems[position]

        // Set audio title
        holder.textViewTitle.text = audio.title

        // Set artist name
        holder.textViewArtist.text = audio.artist ?: "Unknown Artist"

        // Set duration - use cached value from MediaItem if available
        holder.textViewDuration.text = formatDuration(audio.duration)

        // Load album art with caching
        loadAlbumArt(holder, audio)

        // Set click listener
        holder.itemView.setOnClickListener {
            onCastItemClickListener.onCastItemClick(audio)
        }
    }

    private fun loadAlbumArt(holder: AudioViewHolder, audio: MediaItem) {
        // Check cache first
        val cachedArt = albumArtCache.get(audio.path)

        if (cachedArt != null) {
            // Load from cache immediately
            Glide.with(holder.imageViewAudio)
                .asBitmap()
                .load(cachedArt)
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .into(holder.imageViewAudio)
        } else {
            // Set placeholder first
            holder.imageViewAudio.setImageResource(R.drawable.ic_music)

            // Load in background
            adapterScope.launch {
                try {
                    val artistImage = withContext(Dispatchers.IO) {
                        getArtistImageBytes(audio)
                    }

                    // Cache the result
                    if (artistImage != null) {
                        albumArtCache.put(audio.path, artistImage)
                    }

                    // Check if this view is still showing the same item
                    if (holder.adapterPosition != RecyclerView.NO_POSITION &&
                        holder.adapterPosition < mediaItems.size &&
                        mediaItems[holder.adapterPosition].path == audio.path
                    ) {
                        if (artistImage != null) {
                            Glide.with(holder.imageViewAudio)
                                .asBitmap()
                                .load(artistImage)
                                .placeholder(R.drawable.ic_music)
                                .error(R.drawable.ic_music)
                                .into(holder.imageViewAudio)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AudioAdapter", "Error loading album art: ${e.message}")
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_audio, parent, false)
        return AudioViewHolder(view)
    }

    override fun getItemCount(): Int = mediaItems.size

    private fun getArtistImageBytes(audio: MediaItem): ByteArray? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(audio.path)
            retriever.embeddedPicture
        } catch (e: Exception) {
            Log.e("AudioAdapter", "Error extracting album art: ${e.message}")
            null
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Log.e("AudioAdapter", "Error releasing retriever: ${e.message}")
            }
        }
    }

    inner class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageViewAudio: ImageView = itemView.findViewById(R.id.imageViewAudio)
        val textViewTitle: TextView = itemView.findViewById(R.id.textViewTitle)
        val textViewArtist: TextView = itemView.findViewById(R.id.textViewArtist)
        val textViewDuration: TextView = itemView.findViewById(R.id.textViewDuration)
    }

    // Format duration from milliseconds - no file I/O needed!
    private fun formatDuration(durationMillis: Long): String {
        if (durationMillis <= 0) return "0:00"

        val durationInSeconds = durationMillis / 1000
        val minutes = durationInSeconds / 60
        val seconds = durationInSeconds % 60

        return String.format("%d:%02d", minutes, seconds)
    }

    fun setAudioList(mediaItem: List<MediaItem>) {
        val oldSize = mediaItems.size
        mediaItems = mediaItem

        // Use DiffUtil for better performance with large lists
        if (oldSize == 0) {
            notifyDataSetChanged()
        } else {
            notifyItemRangeInserted(oldSize, mediaItem.size - oldSize)
        }
    }

    // Call this when fragment is destroyed to clear cache
    fun clearCache() {
        albumArtCache.evictAll()
        durationCache.clear()
    }
}

