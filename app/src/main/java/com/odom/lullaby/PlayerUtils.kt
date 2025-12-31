package com.odom.lullaby

import android.net.Uri
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

fun addToPlaylist(player: ExoPlayer, playlist: SnapshotStateList<MediaItem>, uri: Uri) {
    val fileName = uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown"
    val item = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(uri.toString())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(fileName)
                .build()
        )
        .build()

    playlist.add(item)
    player.addMediaItem(item)

    // Prepare and play only if this is the first item, otherwise let it queue
    if (player.mediaItemCount == 1) {
        player.prepare()
        player.play()
    }
}

fun removeFromPlaylist(
    player: ExoPlayer,
    playlist: SnapshotStateList<MediaItem>,
    mediaId: String,
    currentIndex: Int
) {
    // Find the index of the item to remove
    val indexToRemove = playlist.indexOfFirst { it.mediaId == mediaId }
    if (indexToRemove >= 0 && indexToRemove < playlist.size) {
        playlist.removeAt(indexToRemove)
        player.removeMediaItem(indexToRemove)

        // If we removed the currently playing item, handle playback
        if (indexToRemove == currentIndex) {
            // If there are still items in the playlist, ExoPlayer will automatically move to next
            // If it was the last item and there are still items, it will wrap to first (due to repeat mode)
            if (playlist.isEmpty()) {
                player.stop()
                player.clearMediaItems()
            }
        }
    }
}

