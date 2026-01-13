package com.odom.lullaby

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TimerService : Service() {
    
    private val binder = LocalBinder()
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val _timerSecondsLeft = MutableStateFlow(0)
    val timerSecondsLeft: StateFlow<Int> = _timerSecondsLeft
    
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning
    
    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }
    
    companion object {
        private var instance: TimerService? = null
        
        fun getInstance(): TimerService? = instance
        
        // Static methods to control timer
        fun startTimer(seconds: Int) {
            instance?.startTimer(seconds)
        }
        
        fun stopTimer() {
            instance?.stopTimer()
        }
        
        fun getTimerState(): Pair<Int, Boolean> {
            return Pair(
                instance?.timerSecondsLeft?.value ?: 0,
                instance?.isTimerRunning?.value ?: false
            )
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    fun startTimer(totalSeconds: Int) {
        stopTimer() // Cancel any existing timer
        
        _timerSecondsLeft.value = totalSeconds
        _isTimerRunning.value = true
        
        acquireWakeLock()
        
        job = CoroutineScope(Dispatchers.Main).launch {
            while (_timerSecondsLeft.value > 0 && _isTimerRunning.value) {
                delay(1000)
                if (_isTimerRunning.value) {
                    _timerSecondsLeft.value -= 1
                }
            }
            
            // Timer finished - stop all playback
            if (_timerSecondsLeft.value <= 0) {
                stopAllPlayback()
                _isTimerRunning.value = false
                releaseWakeLock()
            }
        }
    }
    
    fun stopTimer() {
        job?.cancel()
        _isTimerRunning.value = false
        releaseWakeLock()
    }
    
    private fun stopAllPlayback() {
        // Stop both players through PlaybackService
        PlaybackService.getPlaylistPlayer()?.pause()
        PlaybackService.getWhiteSoundPlayer()?.pause()
        
        // Stop PlaybackService and dismiss notification
        PlaybackService.getInstance()?.stopSelf()
    }
    
    private fun acquireWakeLock() {
        if (wakeLock == null || !wakeLock!!.isHeld) {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BabyLullaby:TimerService"
            )
            wakeLock?.acquire()
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
    
    override fun onDestroy() {
        instance = null
        stopTimer()
        super.onDestroy()
    }
}
