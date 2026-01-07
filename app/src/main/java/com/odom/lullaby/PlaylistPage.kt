package com.odom.lullaby

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
@Composable
fun PlaylistPage(
    assetFiles: List<String>,
    assetFolder: String,
    playlist: SnapshotStateList<MediaItem>,
    player: ExoPlayer,
    currentIndex: Int,
    onAddToPlaylist: (Uri) -> Unit,
    onRemoveFromPlaylist: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
            itemsIndexed(assetFiles) { index, fileName ->
                val mediaId = "asset:///$assetFolder/$fileName"
                val playlistIndex = playlist.indexOfFirst { it.mediaId == mediaId }
                val isInPlaylist = playlistIndex >= 0
                val positionNumber = if (isInPlaylist) playlistIndex + 1 else null

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val uri = Uri.parse(mediaId)
                            if (isInPlaylist) {
                                onRemoveFromPlaylist(mediaId)
                            } else {
                                onAddToPlaylist(uri)
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isInPlaylist)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fileName,
                            modifier = Modifier.weight(1f)
                        )
                        if (positionNumber != null) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = positionNumber.toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

