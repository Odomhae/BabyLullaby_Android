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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaMetadata
import com.odom.lullaby.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private const val PLAYBACK_NOTIFICATION_ID = 1
private const val CHANNEL_ID = "playback_channel"

@UnstableApi
class MainActivity : ComponentActivity() {

    private var notificationManager: PlayerNotificationManager? = null

    // isPlaying 상태를 업데이트하는 람다 함수를 저장할 변수 추가
    private var updateIsPlayingState: ((Boolean) -> Unit)? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            Modifier.systemBarsPadding().background(Color.Gray)

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

//                // Create notification channel for Android O and above
//                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//                    val channel = android.app.NotificationChannel(
//                        CHANNEL_ID,
//                        "Playback Controls",
//                        NotificationManager.IMPORTANCE_LOW
//                    ).apply {
//                        description = "Media playback controls"
//                        setShowBadge(false)
//                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
//                    }
//
//                    val notificationManager = contextInner.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//                    notificationManager.createNotificationChannel(channel)
//                }

                // Create players with proper lifecycle management
                val playlistPlayer = remember {
                    ExoPlayer.Builder(contextInner).build().apply {
                        // Enable repeat mode to loop through entire playlist
                        repeatMode = Player.REPEAT_MODE_ALL
                    }
                }
                
                val whiteSoundPlayer = remember {
                    ExoPlayer.Builder(contextInner).build().apply {
                        // Set repeat mode to ONE to loop single file
                        repeatMode = Player.REPEAT_MODE_ONE
                    }
                }
                
                // Shared sleep timer state
                val sharedPreferencesForTimer = remember {
                    contextInner.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                }
                
                val savedTimerMinutes = remember {
                    sharedPreferencesForTimer.getInt("sleep_timer_minutes", 15)
                }
                
                var timerSecondsTotal by remember { mutableIntStateOf(savedTimerMinutes * 60) }
                var timerSecondsLeft by remember { mutableIntStateOf(timerSecondsTotal) }
                var isTimerRunning by remember { mutableStateOf(false) }
                var showTimerDialog by remember { mutableStateOf(false) }
                var timerInputMinutes by remember { mutableStateOf(savedTimerMinutes.toString()) }
                
                // Observe both players for playback state
                var isPlaylistPlaying by remember { mutableStateOf(playlistPlayer.isPlaying) }
                var isWhiteSoundPlaying by remember { mutableStateOf(whiteSoundPlayer.isPlaying) }
                
                DisposableEffect(playlistPlayer) {
                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaylistPlaying = playing
                        }
                    }
                    playlistPlayer.addListener(listener)
                    isPlaylistPlaying = playlistPlayer.isPlaying
                    onDispose {
                        playlistPlayer.removeListener(listener)
                    }
                }
                
                DisposableEffect(whiteSoundPlayer) {
                    val listener = object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isWhiteSoundPlaying = playing
                        }
                    }
                    whiteSoundPlayer.addListener(listener)
                    isWhiteSoundPlaying = whiteSoundPlayer.isPlaying
                    onDispose {
                        whiteSoundPlayer.removeListener(listener)
                    }
                }
                
                // Countdown effect for shared timer
                LaunchedEffect(isTimerRunning) {
                    if (!isTimerRunning) return@LaunchedEffect
                    
                    while (isTimerRunning && timerSecondsLeft > 0) {
                        delay(1000)
                        if (isTimerRunning) {
                            timerSecondsLeft -= 1
                        }
                    }
                    
                    // Time finished - pause both players
                    if (timerSecondsLeft <= 0) {
                        playlistPlayer.pause()
                        whiteSoundPlayer.pause()
                        isTimerRunning = false
                        isPlaylistPlaying = false
                        isWhiteSoundPlaying = false
                        timerSecondsLeft = timerSecondsTotal
                    }
                }
                
                // Start timer when any playback starts
                LaunchedEffect(isPlaylistPlaying, isWhiteSoundPlaying) {
                    val isAnyPlaying = isPlaylistPlaying || isWhiteSoundPlaying
                    if (isAnyPlaying && !isTimerRunning && timerSecondsTotal > 0) {
                        isTimerRunning = true
                    } else if (!isAnyPlaying) {
                        isTimerRunning = false
                    }
                }
                
                // Format seconds to mm:ss
                fun formatTime(seconds: Int): String {
                    val m = seconds / 60
                    val s = seconds % 60
                    return "%02d:%02d".format(m, s)
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
                val mediaSession = remember(playlistPlayer, sessionActivityPendingIntent) {
                    MediaSession.Builder(contextInner, playlistPlayer)
                        .setSessionActivity(sessionActivityPendingIntent)
                        .build()
                }

                // Create PlayerNotificationManager for notification and lock screen controls
//                 notificationManager  = remember(mediaSession, sessionActivityPendingIntent) {
//                    PlayerNotificationManager.Builder(
//                        contextInner,
//                        PLAYBACK_NOTIFICATION_ID,
//                        CHANNEL_ID
//                    )
//                    .setMediaDescriptionAdapter(
//                        object : PlayerNotificationManager.MediaDescriptionAdapter {
//                            override fun getCurrentContentTitle(player: Player): CharSequence {
//                                val mediaItem = player.currentMediaItem
//                                val title = mediaItem?.mediaMetadata?.title?.toString()
//                                if (!title.isNullOrEmpty()) return title
//                                val fileName = mediaItem?.mediaId?.let {
//                                    Uri.parse(it).lastPathSegment?.substringBeforeLast(".")
//                                }
//                                return fileName ?: "Unknown"
//                            }
//
//                            override fun getCurrentContentText(player: Player): CharSequence? {
//                                return player.currentMediaItem?.mediaMetadata?.artist?.toString()
//                                    ?: "Audio Player"
//                            }
//
//                            override fun getCurrentLargeIcon(
//                                player: Player,
//                                callback: PlayerNotificationManager.BitmapCallback
//                            ): android.graphics.Bitmap? {
//                                return null
//                            }
//
//                            override fun createCurrentContentIntent(player: Player): PendingIntent? {
//                                return sessionActivityPendingIntent
//                            }
//                        }
//                    )
//                    .build()
//                    .apply {
//                        setMediaSessionToken(mediaSession.sessionCompatToken)
//                        setPlayer(player)
//                    }
//                }

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
                val tabTitles = listOf(stringResource(R.string.lullaby), stringResource(R.string.white_noise))

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
                                playlistPlayer.addMediaItem(item)
                            }
                        }
                        if (playlist.isNotEmpty()) {
                            playlistPlayer.prepare()
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
                            playlistPlayer.addMediaItem(item)
                        }
                        if (playlist.isNotEmpty()) {
                            playlistPlayer.prepare()
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

                // Observe playlist player state changes
                DisposableEffect(playlistPlayer) {
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            currentIndex = playlistPlayer.currentMediaItemIndex
                        }

                        override fun onIsPlayingChanged(isPlaying2: Boolean) {
                            isPlaying = playlistPlayer.isPlaying
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            isPlaying = playlistPlayer.isPlaying
                        }
                    }
                    playlistPlayer.addListener(listener)
                    isPlaying = playlistPlayer.isPlaying
                    currentIndex = playlistPlayer.currentMediaItemIndex

                    // isPlaying 상태를 변경하는 람다를 MainActivity의 프로퍼티에 할당
                    updateIsPlayingState = { newState ->
                        isPlaying = newState
                    }

                    onDispose {
                        playlistPlayer.removeListener(listener)
                        whiteSoundPlayer.release()
                        mediaSession.release()
                        playlistPlayer.release()
                        updateIsPlayingState = null // 메모리 누수 방지
                    }

                }

                Column(modifier = Modifier
                    .fillMaxSize()) {
                    // Top bar with theme toggle and sleep timer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                        
                        // Sleep Timer below theme toggle
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .wrapContentWidth()
                                .height(64.dp)
                                .clickable { showTimerDialog = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .fillMaxHeight()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = if (isDarkTheme)
                                    // Sun icon color when in dark theme - use warm colors
                                        Color(0xFFFFD700) // Gold/Yellow
                                    else
                                    // Moon icon color when in light theme - use cool colors
                                        Color(0xFF4A90E2) // Blue
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
                                player = playlistPlayer,
                                currentIndex = currentIndex,
                                onAddToPlaylist = { uri -> addToPlaylist(playlistPlayer, playlist, uri) },
                                onRemoveFromPlaylist = { mediaId -> removeFromPlaylist(playlistPlayer, playlist, mediaId, currentIndex) }
                            )
                            1 -> WhiteSoundsPage(
                                whiteSoundFiles = whiteSoundFiles,
                                whiteSoundFolder = whiteSoundFolder,
                                player = whiteSoundPlayer
                            )
                        }
                    }

                    // Show PlaylistScreen only when playlist page is selected
                    if (pagerState.currentPage == 0) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Playlist controls at the bottom
                        Text(
                            stringResource(R.string.playlist),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        PlaylistScreen(
                            player = playlistPlayer,
                            playlist = playlist,
                            currentIndex = currentIndex,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(16.dp)
                        )
                    }
                    
                    // Sleep Timer Dialog
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
                            sharedPreferencesForTimer.edit()
                                .putInt("sleep_timer_minutes", minutes)
                                .apply()
                            // Only start timer if already playing
                            if (!isTimerRunning) {
                                isTimerRunning = (isPlaylistPlaying || isWhiteSoundPlaying) && timerSecondsTotal > 0
                            }
                            showTimerDialog = false
                        },
                        onDismiss = { showTimerDialog = false }
                    )
                }
            }
        }
    }
}
