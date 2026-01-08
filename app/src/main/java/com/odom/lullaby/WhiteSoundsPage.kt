package com.odom.lullaby

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun WhiteSoundsPage(
    whiteSoundFiles: List<String>,
    whiteSoundFolder: String,
    player: ExoPlayer
) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
    
    // Load saved selected media ID
    val savedSelectedMediaId = remember {
        sharedPreferences.getString("selected_white_sound", null)
    }
    
    // Selected white sound state - restore from saved state
    var selectedMediaId by remember { mutableStateOf<String?>(savedSelectedMediaId) }
    
    // Observe player state for UI updates
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentMediaId by remember { mutableStateOf(player.currentMediaItem?.mediaId) }
    
    // Restore saved selection on first load
    LaunchedEffect(Unit) {
        if (savedSelectedMediaId != null && player.mediaItemCount == 0) {
            // Restore the saved selection
            val uri = Uri.parse(savedSelectedMediaId)
            val fileName = uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown"
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(savedSelectedMediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(fileName)
                        .build()
                )
                .build()
            
            player.addMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.prepare()
            // Don't auto-play, just restore the selection state
        }
    }
    
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaId = mediaItem?.mediaId
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        currentMediaId = player.currentMediaItem?.mediaId
        
        onDispose {
            player.removeListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding() ,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(whiteSoundFiles) { fileName ->
                val mediaId = "asset:///$whiteSoundFolder/$fileName"
                val displayName = fileName.substringBeforeLast(".")
                val isCurrentlySelected = selectedMediaId == mediaId
                val isCurrentlyPlaying = currentMediaId == mediaId && isPlaying
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable {
                            if (isCurrentlySelected) {
                                // Unselect: stop playing
                                player.pause()
                                player.clearMediaItems()
                                selectedMediaId = null
                                currentMediaId = null
                                isPlaying = false
                                // Clear saved selection
                                sharedPreferences.edit()
                                    .remove("selected_white_sound")
                                    .apply()
                            } else {
                                // Select: play the white sound file and start timer
                                val uri = Uri.parse(mediaId)
                                val mediaItem = MediaItem.Builder()
                                    .setUri(uri)
                                    .setMediaId(mediaId)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(displayName)
                                            .build()
                                    )
                                    .build()
                                
                                // Clear current playlist and play only this white sound
                                player.clearMediaItems()
                                player.addMediaItem(mediaItem)
                                // Set repeat mode to ONE to loop this single file
                                player.repeatMode = Player.REPEAT_MODE_ONE
                                player.prepare()
                                player.play()
                                
                                // Save selected media ID
                                selectedMediaId = mediaId
                                sharedPreferences.edit()
                                    .putString("selected_white_sound", mediaId)
                                    .apply()
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentlySelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isCurrentlyPlaying) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = if (isCurrentlySelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

