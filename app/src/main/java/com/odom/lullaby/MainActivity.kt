package com.odom.lullaby

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.MediaMetadata
import com.odom.lullaby.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.android.gms.ads.MobileAds

private const val PLAYLIST_NOTIFICATION_ID = 1
private const val WHITE_SOUND_NOTIFICATION_ID = 2
private const val CHANNEL_ID = "playback_channel"

@UnstableApi
class MainActivity : ComponentActivity() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var timerService: TimerService? = null
    private var isTimerServiceBound = false

    // isPlaying 상태를 업데이트하는 람다 함수를 저장할 변수 추가
    private var updateIsPlayingState: ((Boolean) -> Unit)? = null

    // TimerService connection
    private val timerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.LocalBinder
            timerService = binder.getService()
            isTimerServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isTimerServiceBound = false
            timerService = null
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BabyLullaby:Playback")
            wakeLock?.acquire() // No timeout = play indefinitely
        }
    }

    private fun releaseWakeLock() {
        try {
            // wakeLock이 null이 아니고, 현재 획득(held)된 상태인지 확인합니다.
            if (wakeLock != null && wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // 만약의 상황(동기화 이슈 등)을 대비해 예외 처리를 추가합니다.
            Log.e("MainActivity", "Error releasing WakeLock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to TimerService
        Intent(this, TimerService::class.java).also { intent ->
            bindService(intent, timerServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from TimerService
        if (isTimerServiceBound) {
            unbindService(timerServiceConnection)
            isTimerServiceBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        
        // Stop and reset TimerService to ensure full duration on next start
        if (isTimerServiceBound && timerService != null) {
            timerService?.stopTimer()
            timerService?.resetTimerState()
        }
        
        // Stop the TimerService completely
        val timerIntent = Intent(this, TimerService::class.java)
        stopService(timerIntent)
        
        // Clear selected white sound but keep timer state for persistence
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .remove("selected_white_sound")
            .apply()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // AdMob 초기화
        MobileAds.initialize(this) {}

        // 배터리 최적화 제외 요청 호출
        requestIgnoreBatteryOptimizations()

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
                
                // Set background color based on theme
                val backgroundColor = MaterialTheme.colorScheme.background

                // Create notification channel for Android O and above
                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val channel = android.app.NotificationChannel(
                            CHANNEL_ID,
                            "Playback Controls",
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            description = "Media playback controls"
                            setShowBadge(false)
                            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                            setSound(null, null) // 소리 없음
                            enableVibration(false) // 진동 없음
                        }

                        val notificationManager = contextInner.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.createNotificationChannel(channel)
                    }
                }

                // Start services
                LaunchedEffect(Unit) {
                    // Start TimerService first (no foreground required)
                    val timerIntent = Intent(contextInner, TimerService::class.java)
                    startService(timerIntent)
                    
                    // Wait a moment for TimerService to initialize
                    delay(500)
                }
                
                // Get players from service (they persist across activity destruction)
                val playlistPlayer = remember {
                    PlaybackService.getPlaylistPlayer() ?: ExoPlayer.Builder(contextInner).build().apply {
                        // Enable repeat mode to loop through entire playlist
                        repeatMode = Player.REPEAT_MODE_ALL
                        // Configure audio attributes for background playback
                        val audioAttributes = AudioAttributes.Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .setUsage(C.USAGE_MEDIA)
                            .build()
                        setAudioAttributes(audioAttributes, true)
                    }
                }
                
                val whiteSoundPlayer = remember {
                    PlaybackService.getWhiteSoundPlayer() ?: ExoPlayer.Builder(contextInner).build().apply {
                        // Set repeat mode to ONE to loop single file
                        repeatMode = Player.REPEAT_MODE_ONE
                        // Configure audio attributes for background playback
                        val audioAttributes = AudioAttributes.Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .setUsage(C.USAGE_MEDIA)
                            .build()
                        setAudioAttributes(audioAttributes, true)
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
                var timerSecondsLeft by remember { 
                    // Initialize with saved value from SharedPreferences (for theme change persistence)
                    val savedSecondsLeft = sharedPreferencesForTimer.getInt("timer_seconds_left", timerSecondsTotal)
                    mutableIntStateOf(if (savedSecondsLeft > 0) savedSecondsLeft else timerSecondsTotal)
                }
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

                            // [추가] 다른 앱에 의해 일시정지 되거나 직접 정지했을 때 타이머 정지 명령
                            if (!playing && !whiteSoundPlayer.isPlaying) {
                                Log.d("MainActivity", "Playlist stopped - stopping timer")
                                timerService?.stopTimer()
                            }
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

                            // [추가] 다른 앱에 의해 일시정지 되거나 직접 정지했을 때 타이머 정지 명령
                            if (!playing && !playlistPlayer.isPlaying) {
                                Log.d("MainActivity", "WhiteSound stopped - stopping timer")
                                timerService?.stopTimer()
                            }
                        }
                    }
                    whiteSoundPlayer.addListener(listener)
                    isWhiteSoundPlaying = whiteSoundPlayer.isPlaying
                    onDispose {
                        whiteSoundPlayer.removeListener(listener)
                    }
                }
                
                // Use bound TimerService for timer management
                LaunchedEffect(isTimerServiceBound) {
                    if (isTimerServiceBound && timerService != null) {
                        // Collect timer state from service
                        // timer가 실행 중일 때는 실시간으로 업데이트하고,
                        // timer가 멈췄을 때는 마지막 값을 유지 (일시정지 시 남은 시간 표시)
                        timerService!!.timerSecondsLeft.collect { secondsLeft ->
                            // 실행 중이 아니더라도 0이 들어오면 UI를 갱신해줘야 "00:00"을 봅니다.
                            Log.d("====ttt RemainTime " , secondsLeft.toString())
                            timerSecondsLeft = secondsLeft

                            // 만약 0이 되었다면 강제로 정지 상태를 UI에 동기화
                            if (secondsLeft <= 0) {
                                isTimerRunning = false

                                // [추가] 플레이리스트 플레이어를 첫 번째 곡의 처음으로 리셋
                                if (playlistPlayer.mediaItemCount > 0) {
                                    playlistPlayer.seekTo(0, 0L) // 0번째 인덱스, 0ms 지점으로 이동
                                    playlistPlayer.pause()       // 확실히 정지
                                }

                                timerSecondsLeft = timerSecondsTotal
                            }
                        }
                    }
                }
                
                LaunchedEffect(isTimerServiceBound) {
                    if (isTimerServiceBound && timerService != null) {
                        // Collect timer running state from service
                        timerService!!.isTimerRunning.collect { running ->
                            isTimerRunning = running
                        }
                    }
                }
                
                // Start timer when any playback starts
                LaunchedEffect(isPlaylistPlaying, isWhiteSoundPlaying, isTimerServiceBound) {
                    val isAnyPlaying = isPlaylistPlaying || isWhiteSoundPlaying
                    Log.d("MainActivity", "Timer check - isAnyPlaying: $isAnyPlaying, !isTimerRunning: $!isTimerRunning, timerSecondsTotal: $timerSecondsTotal, isTimerServiceBound: $isTimerServiceBound")
                    
                    if (isAnyPlaying && !isTimerRunning && timerSecondsTotal > 0 && isTimerServiceBound) {
                        // Check if we're resuming from pause vs fresh start
                        if (timerSecondsLeft < timerSecondsTotal && timerSecondsLeft > 0) {
                            Log.d("MainActivity", "Resuming timer with $timerSecondsLeft seconds")
                            timerService?.startTimer(timerSecondsLeft) // Resume with remaining time
                        } else {
                            Log.d("MainActivity", "Starting fresh timer with $timerSecondsTotal seconds")
                            timerService?.startTimer(timerSecondsTotal) // Fresh start with full duration
                        }
                    } else if (!isAnyPlaying && isTimerServiceBound) {
                        Log.d("MainActivity", "Stopping timer - no playback")
                        timerService?.stopTimer()
                        // 일시정지 시에는 timerSecondsLeft를 리셋하지 않음 (TimerService에서 유지됨)
                        // 타이머가 완전히 끝났을 때만 리셋됨 (아래 timerFinished 이벤트에서 처리)
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
                val playlistMediaSession = remember(playlistPlayer, sessionActivityPendingIntent) {
                    MediaSession.Builder(contextInner, playlistPlayer)
                        .setId("playlist_session")
                        .setSessionActivity(sessionActivityPendingIntent)
                        .build()
                }
                
                val whiteSoundMediaSession = remember(whiteSoundPlayer, sessionActivityPendingIntent) {
                    MediaSession.Builder(contextInner, whiteSoundPlayer)
                        .setId("whitesound_session")
                        .setSessionActivity(sessionActivityPendingIntent)
                        .build()
                }

                // Create PlayerNotificationManager for playlist player
                val playlistNotificationManager = remember(playlistMediaSession, sessionActivityPendingIntent) {
                    PlayerNotificationManager.Builder(
                        contextInner,
                        PLAYLIST_NOTIFICATION_ID,
                        CHANNEL_ID
                    )
                    .setMediaDescriptionAdapter(
                        object : PlayerNotificationManager.MediaDescriptionAdapter {
                            override fun getCurrentContentTitle(player: Player): CharSequence {
                                val mediaItem = player.currentMediaItem
                                val title = mediaItem?.mediaMetadata?.title?.toString()
                                if (!title.isNullOrEmpty()) return title
                                val fileName = mediaItem?.mediaId?.let {
                                    try {
                                        val path = Uri.parse(it).lastPathSegment
                                        path?.let { p ->
                                            if (p.contains('.')) {
                                                p.substringBeforeLast(".")
                                            } else {
                                                p
                                            }
                                        }
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                return fileName ?: "Lullaby"
                            }

                            override fun getCurrentContentText(player: Player): CharSequence? {
                                val appName = getString(R.string.app_name)
                                return appName
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
                        setMediaSessionToken(playlistMediaSession.sessionCompatToken)
                        // 초기에는 플레이어를 연결하지 않음 (재생 중일 때만 연결)
                        setPlayer(null)
                    }
                }
                
                // Create PlayerNotificationManager for white sound player
                val whiteSoundNotificationManager = remember(whiteSoundMediaSession, sessionActivityPendingIntent) {
                    PlayerNotificationManager.Builder(
                        contextInner,
                        WHITE_SOUND_NOTIFICATION_ID,
                        CHANNEL_ID
                    )
                    .setMediaDescriptionAdapter(
                        object : PlayerNotificationManager.MediaDescriptionAdapter {
                            override fun getCurrentContentTitle(player: Player): CharSequence {
                                val mediaItem = player.currentMediaItem
                                val title = mediaItem?.mediaMetadata?.title?.toString()
                                if (!title.isNullOrEmpty()) return title
                                val fileName = mediaItem?.mediaId?.let {
                                    try {
                                        val path = Uri.parse(it).lastPathSegment
                                        path?.let { p ->
                                            if (p.contains('.')) {
                                                p.substringBeforeLast(".")
                                            } else {
                                                p
                                            }
                                        }
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                return fileName ?: "White Sound"
                            }

                            override fun getCurrentContentText(player: Player): CharSequence? {
                                return "White Noise"
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
                        setMediaSessionToken(whiteSoundMediaSession.sessionCompatToken)
                        // 초기에는 플레이어를 연결하지 않음 (재생 중일 때만 연결)
                        setPlayer(null)
                    }
                }
                
                // 재생 중일 때만 notification 표시
                LaunchedEffect(isPlaylistPlaying) {
                    if (isPlaylistPlaying) {
                        playlistNotificationManager.setPlayer(playlistPlayer)
                    } else {
                        // 일시정지 중일 때도 알림을 유지하고 싶다면 여기서 null을 세팅하지 않습니다.
                        // 대신 플레이어의 상태가 IDLE(완전 정지)인 경우에만 알림을 제거합니다.
                        if (playlistPlayer.playbackState == Player.STATE_IDLE) {
                            playlistNotificationManager.setPlayer(null)
                        }

                    }
                }
                
                LaunchedEffect(isWhiteSoundPlaying) {
                    if (isWhiteSoundPlaying) {
                        whiteSoundNotificationManager.setPlayer(whiteSoundPlayer)
                    } else {
                        whiteSoundNotificationManager.setPlayer(null)
                    }
                }
                
                LaunchedEffect(isTimerServiceBound) {
                    if (isTimerServiceBound && timerService != null) {
                        // Collect timer finished event from service
                        timerService!!.timerFinished.collect { finished ->
                            if (finished) {
                                Log.d("MainActivity", "Timer finished event received, stopping playback")
                                // Stop both players
                                playlistPlayer.pause()
                                whiteSoundPlayer.pause()
                                playlistPlayer.stop()
                                whiteSoundPlayer.stop()
                                
                                // Clear notifications
                                playlistNotificationManager.setPlayer(null)
                                whiteSoundNotificationManager.setPlayer(null)
                                
                                // Stop PlaybackService
                                val playbackIntent = Intent(contextInner, PlaybackService::class.java)
                                contextInner.stopService(playbackIntent)
                                
                                // Reset timer display
                                timerSecondsLeft = timerSecondsTotal
                                
                                // Release WakeLock
                                releaseWakeLock()
                            }
                        }
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
                val whiteSoundItems = remember {
                    // 코드상으로 순서를 지정할 수 있도록 리스트 생성
                    listOf(
                        WhiteSoundItem(
                            soundFileName = "birds.mp3",
                            displayName = resources.getString(R.string.sound_bird),
                            imageResId = R.drawable.birds
                        ),
                        WhiteSoundItem(
                            soundFileName = "ocean-waves.mp3",
                            displayName = resources.getString(R.string.sound_wave),
                            imageResId = R.drawable.ocean_wave
                        ),
                        WhiteSoundItem(
                            soundFileName = "strong-rain.mp3",
                            displayName = resources.getString(R.string.sound_rain),
                            imageResId = R.drawable.rain
                        ),
                        WhiteSoundItem(
                            soundFileName = "Shhh.m4a",
                            displayName = resources.getString(R.string.sound_shhh),
                            imageResId = R.drawable.shhh
                        ),
                        WhiteSoundItem(
                            soundFileName = "shoppingmall.m4a",
                            displayName = resources.getString(R.string.sound_shoppingmall),
                            imageResId = R.drawable.shoppingmall
                        ),
                        WhiteSoundItem(
                            soundFileName = "vinyl.m4a",
                            displayName = resources.getString(R.string.sound_vinyl),
                            imageResId = R.drawable.plasticbag
                        )

                    )
                }
                
                // Pager state for ViewPager-like functionality
                val pagerState = rememberPagerState(pageCount = { 2 })
                val tabTitles = listOf(stringResource(R.string.lullaby), stringResource(R.string.white_noise))

                // Load saved playlist on startup
                LaunchedEffect(Unit) {
                    try {
                        val savedPlaylistString = sharedPreferences.getString("playlist_order", null)
                        if (!savedPlaylistString.isNullOrEmpty()) {
                            val savedMediaIds = savedPlaylistString.split(",")
                            savedMediaIds.forEach { mediaId ->
                                if (mediaId.isNotEmpty()) {
                                    try {
                                        val uri = Uri.parse(mediaId)
                                        val fileName = uri.lastPathSegment?.let { path ->
                                            if (path.contains('.')) {
                                                path.substringBeforeLast(".")
                                            } else {
                                                path
                                            }
                                        } ?: "Unknown"
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
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Error loading playlist item: ${e.message}")
                                    }
                                }
                            }
                            if (playlist.isNotEmpty()) {
                                playlistPlayer.prepare()
                                // Reset to first song and stop playback on activity recreation
                                playlistPlayer.seekTo(0, 0L)
                                playlistPlayer.pause()
                                playlistPlayer.stop()
                            }
                        } else {
                            // If no saved playlist, add all songs in order
                            assetFiles.forEach { fileName ->
                                try {
                                    val mediaId = "asset:///$assetFolder/$fileName"
                                    val uri = Uri.parse(mediaId)
                                    val displayName = if (fileName.contains('.')) {
                                        fileName.substringBeforeLast(".")
                                    } else {
                                        fileName
                                    }
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
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "Error adding asset file: ${e.message}")
                                }
                            }
                            if (playlist.isNotEmpty()) {
                                playlistPlayer.prepare()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error loading playlist: ${e.message}")
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
                        playlistNotificationManager.setPlayer(null)
                        whiteSoundNotificationManager.setPlayer(null)
                        whiteSoundPlayer.release()
                        playlistMediaSession.release()
                        whiteSoundMediaSession.release()
                        playlistPlayer.release()
                        wakeLock?.release()
                        updateIsPlayingState = null // 메모리 누수 방지
                    }
                }

                Column(modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .background(backgroundColor)) {
                    // Top bar with theme toggle and sleep timer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, // 양 끝으로 배치
                        verticalAlignment = Alignment.CenterVertically // 수직 중앙 정렬
                    ) {
                        // 1. 왼쪽: 테마 토글 버튼
                        IconButton(onClick = {isDarkTheme = !isDarkTheme}){
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.BrightnessHigh else Icons.Default.Brightness2,
                                contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme",
                                modifier = Modifier.size(40.dp),
                                tint = if (isDarkTheme)
                                // Sun icon color when in dark theme - use warm colors
                                    Color(0xFFFFD700) // Gold/Yellow
                                else
                                // Moon icon color when in light theme - use cool colors
                                    Color(0xFF4A90E2) // Blue
                            )
                        }
                        
                        // Sleep Timer below theme toggle
                        Card(
                            modifier = Modifier
                                .wrapContentWidth()
                                .height(50.dp)
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
                                text = {
                                    Text(
                                    title, fontWeight = FontWeight.Bold
                                ) }
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
                                onAddToPlaylist = { uri -> 
                                    // WhiteSound 재생 중지 및 notification 제거
                                    if (whiteSoundPlayer.isPlaying) {
                                        whiteSoundPlayer.pause()
                                        whiteSoundPlayer.clearMediaItems()
                                        whiteSoundNotificationManager.setPlayer(null)
                                    }
                                    addToPlaylist(playlistPlayer, playlist, uri)
                                    // 재생 시작 시 notification 활성화
                                    if (playlistPlayer.isPlaying) {
                                        playlistNotificationManager.setPlayer(playlistPlayer)
                                    }
                                },
                                onRemoveFromPlaylist = { mediaId -> removeFromPlaylist(playlistPlayer, playlist, mediaId, currentIndex) }
                            )
                            1 -> WhiteSoundsPage(
                                whiteSoundItems = whiteSoundItems,
                                whiteSoundFolder = whiteSoundFolder,
                                player = whiteSoundPlayer,
                                onPlay = {
                                    acquireWakeLock() // Add WakeLock
                                    // Start PlaybackService when playback begins
                                    val playbackIntent = Intent(contextInner, PlaybackService::class.java)
                                    startForegroundService(playbackIntent)
                                    
                                    // Playlist 재생 중지 및 notification 제거
                                    if (playlistPlayer.isPlaying) {
                                        playlistPlayer.pause()
                                        playlistNotificationManager.setPlayer(null)
                                    }
                                    // 알림 관리자에 플레이어를 다시 연결하여 알림을 활성화함
                                    whiteSoundNotificationManager.setPlayer(whiteSoundPlayer)
                                },
                                onResetTimer = {
                                    timerSecondsLeft = timerSecondsTotal
                                    isTimerRunning = false
                                    whiteSoundNotificationManager.setPlayer(null)
                                    releaseWakeLock() // Release WakeLock when stopping
                                }
                            )
                        }
                    }

                    // Show PlaylistScreen only when playlist page is selected
                    if (pagerState.currentPage == 0) {
                      //  Spacer(modifier = Modifier.height(5.dp))

                        PlaylistScreen(
                            player = playlistPlayer,
                            playlist = playlist,
                            currentIndex = currentIndex,
                            isPlaying = isPlaying,
                            onPlay = {
                                acquireWakeLock() // Add WakeLock
                                // Start PlaybackService when playback begins
                                val playbackIntent = Intent(contextInner, PlaybackService::class.java)
                                startForegroundService(playbackIntent)
                                
                                // WhiteSound 재생 중지 및 notification 제거
                                if (whiteSoundPlayer.isPlaying) {
                                    whiteSoundPlayer.pause()
                                    whiteSoundPlayer.clearMediaItems()
                                    whiteSoundNotificationManager.setPlayer(null)
                                }
                                // 알림 관리자에 플레이어를 다시 연결하여 알림을 활성화함
                                playlistNotificationManager.setPlayer(playlistPlayer)
                            },
                            onResetTimer = {
                                timerSecondsLeft = timerSecondsTotal
                                isTimerRunning = false
                                playlistNotificationManager.setPlayer(null)
                                playlistPlayer.seekTo(0, 0L)
                                releaseWakeLock() // Release WakeLock when stopping
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .padding(horizontal = 16.dp)
                        )
                    }

                    // 하단 광고 배너 추가
                    BannerAdView(modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding())

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
                            // Start timer if already playing and service is bound
                            if ((isPlaylistPlaying || isWhiteSoundPlaying) && isTimerServiceBound) {
                                timerService?.startTimer(timerSecondsTotal)
                            }
                            showTimerDialog = false
                        },
                        onDismiss = { showTimerDialog = false }
                    )
                }
            }
        }
    }


private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName

        // 이미 최적화 제외 대상인지 확인
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                // 사용자에게 설정을 요청하는 인텐트 (이 작업은 배터리 사용량 최적화 목록으로 보냅니다)
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // 일부 기기나 버전에서 인텐트가 지원되지 않을 경우를 대비
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }
    }

}
