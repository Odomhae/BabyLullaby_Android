package com.odom.lullaby

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

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

    // Observe player state for UI updates
    var isPlaying by remember { mutableStateOf(player.isPlaying) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying

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
           // player.clearMediaItems()
            isTimerRunning = false
            isPlaying = false
            timerSecondsLeft = timerSecondsTotal
        }
    }

    // Start timer when playback starts
    LaunchedEffect(isPlaying) {
        if (isPlaying && !isTimerRunning && timerSecondsTotal > 0) {
            isTimerRunning = true
        } else if (!isPlaying) {
            isTimerRunning = false
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

        LazyColumn(
            modifier = Modifier.weight(1f),
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
}
