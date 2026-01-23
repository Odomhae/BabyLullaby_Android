package com.odom.lullaby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    
    private var notificationManager: NotificationManager? = null
    private var playlistMediaSession: MediaSession? = null
    private var whiteSoundMediaSession: MediaSession? = null
    private var playlistPlayer: ExoPlayer? = null
    private var whiteSoundPlayer: ExoPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var timerBroadcastReceiver: BroadcastReceiver? = null
    
    // Static access to service instance
    companion object {
        private var instance: PlaybackService? = null
        
        fun getInstance(): PlaybackService? = instance
        
        fun getPlaylistPlayer(): ExoPlayer? = instance?.playlistPlayer
        fun getWhiteSoundPlayer(): ExoPlayer? = instance?.whiteSoundPlayer
        
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "playback_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        audioManager = getSystemService(AudioManager::class.java)
        
        // Register timer broadcast receiver
        registerTimerReceiver()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Return the appropriate session based on which player is active
        return when {
            playlistPlayer?.isPlaying == true -> playlistMediaSession
            whiteSoundPlayer?.isPlaying == true -> whiteSoundMediaSession
            else -> playlistMediaSession // Default to playlist session
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep service running when app is removed from recent tasks
        val player = getCurrentPlayer()
        if (player?.isPlaying == true) {
            // Start foreground service to keep playback alive
            startForeground(NOTIFICATION_ID, createNotification())
            acquireWakeLock()
        } else {
            // Stop service if nothing is playing
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_PLAYBACK") {

            // todo jihoon
            playlistPlayer?.let {
                it.pause()
                it.stop()
               // it.clearMediaItems() // 알림창 제거를 위해 목록 비우기
            }

            whiteSoundPlayer?.let {
                it.pause()
                it.stop()
             //   it.clearMediaItems()
            }

            releaseWakeLock()
            releaseAudioFocus()

            stopForeground(true)
            stopSelf() // 서비스 종료

            return START_NOT_STICKY
        }

        // Start foreground immediately to avoid timeout exception
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Ensure service stays alive
        val player = getCurrentPlayer()
        if (player?.isPlaying == true) {
            acquireWakeLock()
        }
        return START_STICKY // Restart if killed by system
    }

    override fun onDestroy() {
        instance = null
        releaseWakeLock()
        releaseAudioFocus()
        
        // Unregister timer broadcast receiver
        unregisterTimerReceiver()
        playlistMediaSession?.release()
        whiteSoundMediaSession?.release()
        playlistPlayer?.release()
        whiteSoundPlayer?.release()
        super.onDestroy()
    }

    fun setPlayers(
        playlistPlayer: ExoPlayer,
        whiteSoundPlayer: ExoPlayer,
        playlistMediaSession: MediaSession,
        whiteSoundMediaSession: MediaSession
    ) {
        this.playlistPlayer = playlistPlayer
        this.whiteSoundPlayer = whiteSoundPlayer
        this.playlistMediaSession = playlistMediaSession
        this.whiteSoundMediaSession = whiteSoundMediaSession
        
        // Configure audio attributes for background playback
        configureAudioAttributes(playlistPlayer)
        configureAudioAttributes(whiteSoundPlayer)
        
        // Request audio focus
        requestAudioFocus()
        
        // Monitor playback state to start foreground service only when playing
        playlistPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    if (playlistPlayer.isPlaying) {
                        startForeground(NOTIFICATION_ID, createNotification())
                        acquireWakeLock()
                    }
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    acquireWakeLock()
                    requestAudioFocus()
                } else {
                    // Only release wake lock if neither player is playing
                    if (whiteSoundPlayer?.isPlaying != true) {
                        releaseWakeLock()
                    }
                    // Stop service if neither player is playing
                    if (playlistPlayer?.isPlaying != true && whiteSoundPlayer?.isPlaying != true) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        })
        
        whiteSoundPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    if (whiteSoundPlayer.isPlaying) {
                        startForeground(NOTIFICATION_ID, createNotification())
                        acquireWakeLock()
                    }
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    acquireWakeLock()
                    requestAudioFocus()
                } else {
                    // Only release wake lock if neither player is playing
                    if (playlistPlayer?.isPlaying != true) {
                        releaseWakeLock()
                    }
                    // Stop service if neither player is playing
                    if (playlistPlayer?.isPlaying != true && whiteSoundPlayer?.isPlaying != true) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        })
    }
    
    private fun configureAudioAttributes(player: ExoPlayer) {
        val audioAttributes = Media3AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        player.setAudioAttributes(audioAttributes, true)
    }
    
    private fun acquireWakeLock() {
        if (wakeLock == null || !wakeLock!!.isHeld) {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BabyLullaby:PlaybackService"
            )
            wakeLock?.acquire() // No timeout - keep playing until timer finishes
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    private fun requestAudioFocus() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                // Pause playback temporarily
                                playlistPlayer?.pause()
                                whiteSoundPlayer?.pause()
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                // Resume playback
                                if (playlistPlayer?.playWhenReady == true) {
                                    playlistPlayer?.play()
                                }
                                if (whiteSoundPlayer?.playWhenReady == true) {
                                    whiteSoundPlayer?.play()
                                }
                            }
                        }
                    }
                    .build()
                audioFocusRequest = focusRequest
                am.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }
    
    private fun releaseAudioFocus() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
        audioFocusRequest = null
    }

    private fun getCurrentPlayer(): Player? {
        return when {
            playlistPlayer?.isPlaying == true -> playlistPlayer
            whiteSoundPlayer?.isPlaying == true -> whiteSoundPlayer
            else -> playlistPlayer
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keep music playing in background"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val player = getCurrentPlayer()
        val title = player?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Baby Lullaby"
        
        // Create intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Playing in background")
            .setSmallIcon(R.drawable.launcher6)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun registerTimerReceiver() {
        timerBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "TIMER_COMPLETED") {
                    val action = intent.getStringExtra("action")
                    Log.d("PlaybackService", "Received TIMER_COMPLETED broadcast with action: $action")
                    
                    if (action == "STOP_PLAYBACK") {
                        Log.d("PlaybackService", "Stopping playback due to timer completion")
                        stopAllPlayback()
                    }
                }
            }
        }
        
        val filter = IntentFilter("TIMER_COMPLETED")
        registerReceiver(timerBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        Log.d("PlaybackService", "Timer broadcast receiver registered")
    }
    
    private fun unregisterTimerReceiver() {
        timerBroadcastReceiver?.let {
            unregisterReceiver(it)
            timerBroadcastReceiver = null
            Log.d("PlaybackService", "Timer broadcast receiver unregistered")
        }
    }
    
    private fun stopAllPlayback() {
        playlistPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                player.stop()
                Log.d("PlaybackService", "Stopped playlist player")
            }
        }
        
        whiteSoundPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                player.stop()
                Log.d("PlaybackService", "Stopped white sound player")
            }
        }
        
        // Clear notification
        stopForeground(true)
        stopSelf()
    }
}
