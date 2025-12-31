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
import kotlinx.coroutines.delay

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
    
    // Load saved timer minutes on initialization
    val savedTimerMinutes = remember {
        sharedPreferences.getInt("sleep_timer_minutes", 15) // default 15 minutes
    }
    
    // Timer state
    var timerSecondsTotal by remember { mutableIntStateOf(savedTimerMinutes * 60) }
    var timerSecondsLeft by remember { mutableIntStateOf(timerSecondsTotal) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var timerInputMinutes by remember { mutableStateOf(savedTimerMinutes.toString()) }
    
    // Selected white sound state
    var selectedMediaId by remember { mutableStateOf<String?>(null) }
    
    // Observe player state for UI updates
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentMediaId by remember { mutableStateOf(player.currentMediaItem?.mediaId) }
    
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
    
    // Countdown effect
    LaunchedEffect(isTimerRunning) {
        if (!isTimerRunning) return@LaunchedEffect
        
        while (isTimerRunning && timerSecondsLeft > 0) {
            delay(1000)
            if (isTimerRunning) { // Check again after delay
                timerSecondsLeft -= 1
            }
        }
        
        // Time finished
        if (timerSecondsLeft <= 0) {
            player.pause()
            player.clearMediaItems()
            isTimerRunning = false
            selectedMediaId = null
            currentMediaId = null
            isPlaying = false
            timerSecondsLeft = timerSecondsTotal
        }
    }
    
    // Format seconds to mm:ss
    fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Timer display on top - clickable to set timer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { showTimerDialog = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sleep Timer",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatTime(timerSecondsLeft),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isTimerRunning) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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
                                // Unselect: stop playing and reset timer
                                player.pause()
                                player.clearMediaItems()
                                isTimerRunning = false
                                selectedMediaId = null
                                currentMediaId = null
                                isPlaying = false
                                timerSecondsLeft = timerSecondsTotal
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
                                
                                // Start timer
                                selectedMediaId = mediaId
                                timerSecondsLeft = timerSecondsTotal
                                isTimerRunning = timerSecondsTotal > 0
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
    
    // Timer dialog
    SleepTimerDialog(
        show = showTimerDialog,
        timerInputMinutes = timerInputMinutes,
        onMinutesChange = { value ->
            timerInputMinutes = value.filter { ch -> ch.isDigit() }
        },
        onConfirm = {
            val minutes = timerInputMinutes.toIntOrNull()?.coerceIn(1, 180) ?: 15
            timerSecondsTotal = minutes * 60
            timerSecondsLeft = timerSecondsTotal
            // Save the timer minutes to SharedPreferences
            sharedPreferences.edit()
                .putInt("sleep_timer_minutes", minutes)
                .apply()
            // Only start timer if already playing
            if (!isTimerRunning) {
                isTimerRunning = isPlaying && timerSecondsTotal > 0
            }
            showTimerDialog = false
        },
        onDismiss = { showTimerDialog = false }
    )
}

