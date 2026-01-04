package com.odom.lullaby

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Alignment
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaMetadata
import com.odom.lullaby.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private const val PLAYBACK_NOTIFICATION_ID = 1
private const val CHANNEL_ID = "playback_channel"

class MainActivity : ComponentActivity() {
    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            Modifier.systemBarsPadding()

            val context = LocalContext.current
            val sharedPreferencesForTheme = remember {
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            }
            
            // Theme management - default to dark theme
            var isDarkTheme by remember {
                mutableStateOf(sharedPreferencesForTheme.getBoolean("is_dark_theme", true))
            }

            val systemUiController = rememberSystemUiController()
            val useDarkIcons = true // !isDarkTheme todo jihoon

            SideEffect {
                systemUiController.setSystemBarsColor(
                    color = Color.Transparent,
                    darkIcons = useDarkIcons
                )
                systemUiController.setStatusBarColor(
                    color = Color.Transparent,
                    darkIcons = useDarkIcons
                )
                systemUiController.setNavigationBarColor(
                    color = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White,
                    darkIcons = useDarkIcons
                )
            }
            
            // Save theme preference when it changes
            LaunchedEffect(isDarkTheme) {
                sharedPreferencesForTheme.edit()
                    .putBoolean("is_dark_theme", isDarkTheme)
                    .apply()
            }
            
            MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = false) {
                val contextInner = LocalContext.current

                // Create notification channel for Android O and above
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        CHANNEL_ID,
                        "Playback Controls",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Media playback controls"
                        setShowBadge(false)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }

                    val notificationManager = contextInner.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.createNotificationChannel(channel)
                }

                // Create player with proper lifecycle management
                val player = remember {
                    ExoPlayer.Builder(contextInner).build().apply {
                        // Enable repeat mode to loop through entire playlist
                        repeatMode = Player.REPEAT_MODE_ALL
                    }
                }

                // Create session activity pending intent (used by both MediaSession and NotificationManager)
                // Use FLAG_ACTIVITY_SINGLE_TOP to prevent restarting activity and pausing playback
                val sessionActivityPendingIntent = remember {
                    PendingIntent.getActivity(
                        contextInner,
                        0,
                        Intent(contextInner, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }

                // Create MediaSession for background playback and media controls
                val mediaSession = remember(player, sessionActivityPendingIntent) {
                    MediaSession.Builder(contextInner, player)
                        .setSessionActivity(sessionActivityPendingIntent)
                        .build()
                }

                // Create PlayerNotificationManager for notification and lock screen controls
                val notificationManager : PlayerNotificationManager = remember(mediaSession, sessionActivityPendingIntent) {
                    PlayerNotificationManager.Builder(
                        contextInner,
                        PLAYBACK_NOTIFICATION_ID,
                        CHANNEL_ID
                    )
                    .setMediaDescriptionAdapter(
                        object : PlayerNotificationManager.MediaDescriptionAdapter {
                            override fun getCurrentContentTitle(player: Player): CharSequence {
                                val mediaItem = player.currentMediaItem
                                val title = mediaItem?.mediaMetadata?.title?.toString()
                                if (!title.isNullOrEmpty()) return title
                                val fileName = mediaItem?.mediaId?.let {
                                    Uri.parse(it).lastPathSegment?.substringBeforeLast(".")
                                }
                                return fileName ?: "Unknown"
                            }

                            override fun getCurrentContentText(player: Player): CharSequence? {
                                return player.currentMediaItem?.mediaMetadata?.artist?.toString()
                                    ?: "Audio Player"
                            }

                            override fun getCurrentLargeIcon(
                                player: Player,
                                callback: PlayerNotificationManager.BitmapCallback
                            ): android.graphics.Bitmap? {
                                return null
                            }

                            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                                return sessionActivityPendingIntent
                            }
                        }
                    )
                    .build()
                    .apply {
                        setMediaSessionToken(mediaSession.sessionCompatToken)
                        setPlayer(player)
                    }
                }

                val playlist = remember { mutableStateListOf<MediaItem>() }
                val sharedPreferences = remember {
                    contextInner.getSharedPreferences("playlist_prefs", Context.MODE_PRIVATE)
                }
                
                var isInitialLoad by remember { mutableStateOf(true) }

                val assetFolder = "lullaby"
                val assetFiles = remember {
                    contextInner.assets.list(assetFolder)?.toList()?.filter { fileName ->
                    // Filter for common audio file extensions
                    fileName.endsWith(".mp3", ignoreCase = true) ||
                            fileName.endsWith(".m4a", ignoreCase = true) ||
                            fileName.endsWith(".wav", ignoreCase = true) ||
                            fileName.endsWith(".ogg", ignoreCase = true) ||
                            fileName.endsWith(".aac", ignoreCase = true)
                    } ?: emptyList()
                }
                
                // Load white sound files from assets/whitesound folder
                val whiteSoundFolder = "whitesound"
                val whiteSoundFiles = remember {
                    contextInner.assets.list(whiteSoundFolder)?.toList()?.filter { fileName ->
                        // Filter for common audio file extensions
                        fileName.endsWith(".mp3", ignoreCase = true) ||
                                fileName.endsWith(".m4a", ignoreCase = true) ||
                                fileName.endsWith(".wav", ignoreCase = true) ||
                                fileName.endsWith(".ogg", ignoreCase = true) ||
                                fileName.endsWith(".aac", ignoreCase = true)
                    } ?: emptyList()
                }
                
                // Pager state for ViewPager-like functionality
                val pagerState = rememberPagerState(pageCount = { 2 })
                val tabTitles = listOf("Playlist", "White Sounds")

                // Load saved playlist on startup
                LaunchedEffect(Unit) {
                    val savedPlaylistString = sharedPreferences.getString("playlist_order", null)
                    if (!savedPlaylistString.isNullOrEmpty()) {
                        val savedMediaIds = savedPlaylistString.split(",")
                        savedMediaIds.forEach { mediaId ->
                            if (mediaId.isNotEmpty()) {
                                val uri = Uri.parse(mediaId)
                                val fileName = uri.lastPathSegment?.substringBeforeLast(".") ?: "Unknown"
                                val item = MediaItem.Builder()
                                    .setUri(uri)
                                    .setMediaId(mediaId)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(fileName)
                                            .build()
                                    )
                                    .build()
                                playlist.add(item)
                                player.addMediaItem(item)
                            }
                        }
                        if (playlist.isNotEmpty()) {
                            player.prepare()
                        }
                    } else {
                        // If no saved playlist, add all songs in order
                        assetFiles.forEach { fileName ->
                            val mediaId = "asset:///$assetFolder/$fileName"
                            val uri = Uri.parse(mediaId)
                            val displayName = fileName.substringBeforeLast(".")
                            val item = MediaItem.Builder()
                                .setUri(uri)
                                .setMediaId(mediaId)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(displayName)
                                        .build()
                                )
                                .build()
                            playlist.add(item)
                            player.addMediaItem(item)
                        }
                        if (playlist.isNotEmpty()) {
                            player.prepare()
                        }
                    }
                    isInitialLoad = false
                }

                // Save playlist whenever it changes (preserve order)
                val playlistKey by remember {
                    derivedStateOf {
                        playlist.joinToString(",") { it.mediaId }
                    }
                }

                LaunchedEffect(playlistKey) {
                    if (!isInitialLoad) {
                        sharedPreferences.edit()
                            .putString("playlist_order", playlistKey)
                            .apply()
                    }
                }

                // Reactive player state
                var currentIndex by remember { mutableIntStateOf(-1) }
                var isPlaying by remember { mutableStateOf(false) }

                // Observe player state changes
                DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentIndex = player.currentMediaItemIndex
                    }

                    override fun onIsPlayingChanged(isPlaying2: Boolean) {
                        isPlaying = player.isPlaying
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isPlaying = player.isPlaying
                    }
                }
                player.addListener(listener)
                isPlaying = player.isPlaying
                currentIndex = player.currentMediaItemIndex

                    onDispose {
                        player.removeListener(listener)
                        notificationManager.setPlayer(null)
                        mediaSession.release()
                        player.release()
                        //    player = null
                    }

                }

                Column(modifier = Modifier
                    .fillMaxSize()) {
                    // Top bar with theme toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                      //  Text("Baby Lullaby", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { isDarkTheme = !isDarkTheme }) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.BrightnessHigh else Icons.Default.Brightness2,
                                contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme",
                                tint = if (isDarkTheme) 
                                    // Sun icon color when in dark theme - use warm colors
                                    Color(0xFFFFD700) // Gold/Yellow
                                else 
                                    // Moon icon color when in light theme - use cool colors
                                    Color(0xFF4A90E2) // Blue
                            )
                        }
                    }
                    
                    // Tabs for page switching
                    val scope = rememberCoroutineScope()
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { 
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = { Text(title) }
                            )
                        }
                    }

                    // Horizontal Pager for swiping between pages
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> PlaylistPage(
                                assetFiles = assetFiles,
                                assetFolder = assetFolder,
                                playlist = playlist,
                                player = player,
                                currentIndex = currentIndex,
                                onAddToPlaylist = { uri -> addToPlaylist(player, playlist, uri) },
                                onRemoveFromPlaylist = { mediaId -> removeFromPlaylist(player, playlist, mediaId, currentIndex) }
                            )
                            1 -> WhiteSoundsPage(
                                whiteSoundFiles = whiteSoundFiles,
                                whiteSoundFolder = whiteSoundFolder,
                                player = player
                            )
                        }
                    }

                    // Show PlaylistScreen only when playlist page is selected
                    if (pagerState.currentPage == 0) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Playlist controls at the bottom
                        Text(
                            "재생목록",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        PlaylistScreen(
                            player = player,
                            playlist = playlist,
                            currentIndex = currentIndex,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
