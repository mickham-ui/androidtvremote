package com.out.androidtvremote.services.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.out.androidtvremote.R
import com.out.androidtvremote.services.CastService
import com.out.androidtvremote.services.CastService.Companion.ACTION_QUIT
import com.out.androidtvremote.services.CastService.Companion.ACTION_TOGGLE_PAUSE
import com.out.androidtvremote.ui.frags.cast.data.MediaItem
import com.out.androidtvremote.ui.activities.player.CastPlayerActivity
import com.out.androidtvremote.utils.Constant
import com.out.androidtvremote.utils.VersionUtils

import android.app.TaskStackBuilder

@SuppressLint("RestrictedApi")
class PlayingNotificationImpl24(
    val context: CastService,
    mediaSessionCompat: MediaSessionCompat
) : PlayingNotification(context) {

    private var isCurrentlyPlaying = true

    init {
        // Tap notification → open CastPlayerActivity, providing MainActivity as its parent stack
        val action = Intent(context, CastPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (VersionUtils.hasMarshmallow()) PendingIntent.FLAG_IMMUTABLE else 0

        val clickIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(action)
            getPendingIntent(0, pendingIntentFlags)
        }

        // Swipe-dismiss → quit service
        val serviceName = ComponentName(context, CastService::class.java)
        val deleteIntent = PendingIntent.getService(
            context, 0,
            Intent(ACTION_QUIT).apply { component = serviceName },
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (VersionUtils.hasMarshmallow()) PendingIntent.FLAG_IMMUTABLE else 0
        )

        setSmallIcon(R.drawable.ic_notifications_black_24dp)
        setContentIntent(clickIntent)
        setDeleteIntent(deleteIntent)
        setShowWhen(false)
        setOngoing(true)
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Initial actions
        rebuildActions(isCurrentlyPlaying)

        setStyle(
            MediaStyle().setShowActionsInCompactView(0, 1)
        )
    }

    /** Rebuild the action list so play/pause icon updates correctly. */
    private fun rebuildActions(playing: Boolean) {
        mActions.clear()
        addAction(buildPlayAction(playing))
        addAction(buildStopAction())
    }

    override fun updateMetadata(castItem: MediaItem, onUpdate: () -> Unit) {
        setContentTitle(castItem.title)
        setContentText(Constant.castDescription.ifBlank { "Casting to TV" })
        setSubText(castItem.mimeType?.substringBefore("/")?.replaceFirstChar { it.uppercase() })

        val bigSize = context.resources.getDimensionPixelSize(R.dimen.ic_notifications_black_24dp)
        Glide.with(context)
            .asBitmap()
            .load(castItem.path)
            .centerCrop()
            .into(object : CustomTarget<Bitmap>(bigSize, bigSize) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    setLargeIcon(resource)
                    onUpdate()
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    setLargeIcon(
                        BitmapFactory.decodeResource(context.resources, R.drawable.ic_notifications_black_24dp)
                    )
                    onUpdate()
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    setLargeIcon(
                        BitmapFactory.decodeResource(context.resources, R.drawable.ic_notifications_black_24dp)
                    )
                    onUpdate()
                }
            })
    }

    override fun setPlaying(isPlaying: Boolean) {
        isCurrentlyPlaying = isPlaying
        rebuildActions(isPlaying)
    }

    override fun updateFavorite(isFavorite: Boolean) { /* unused */ }

    private fun buildPlayAction(isPlaying: Boolean): NotificationCompat.Action {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        return NotificationCompat.Action.Builder(
            icon,
            context.getString(R.string.action_play_pause),
            retrievePlaybackAction(ACTION_TOGGLE_PAUSE)
        ).build()
    }

    private fun buildStopAction(): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            androidx.mediarouter.R.drawable.ic_dialog_close_dark,
            context.getString(R.string.action_cancel),
            retrievePlaybackAction(ACTION_QUIT)
        ).build()
    }

    private fun retrievePlaybackAction(action: String): PendingIntent {
        val requestCode = when (action) {
            ACTION_TOGGLE_PAUSE -> 1
            ACTION_QUIT        -> 2
            else               -> 3
        }
        val serviceName = ComponentName(context, CastService::class.java)
        return PendingIntent.getService(
            context, requestCode,
            Intent(action).apply { component = serviceName },
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (VersionUtils.hasMarshmallow()) PendingIntent.FLAG_IMMUTABLE else 0
        )
    }

    companion object {
        fun from(
            context: CastService,
            notificationManager: NotificationManager,
            mediaSessionCompat: MediaSessionCompat
        ): PlayingNotification {
            if (VersionUtils.hasOreo()) {
                createNotificationChannel(context, notificationManager)
            }
            return PlayingNotificationImpl24(context, mediaSessionCompat)
        }
    }
}