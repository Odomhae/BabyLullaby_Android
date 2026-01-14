package com.odom.lullaby

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
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
    
    private val _timerFinished = MutableStateFlow(false)
    val timerFinished: StateFlow<Boolean> = _timerFinished
    
    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }
    
    override fun onCreate() {
        super.onCreate()
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    fun startTimer(totalSeconds: Int) {
        Log.d("TimerService", "startTimer called with $totalSeconds seconds")
        stopTimer() // Cancel any existing timer
        
        _timerSecondsLeft.value = totalSeconds
        _isTimerRunning.value = true
        
        Log.d("TimerService", "Timer started, seconds left: ${_timerSecondsLeft.value}, running: ${_isTimerRunning.value}")
        acquireWakeLock()
        
        job = CoroutineScope(Dispatchers.Main).launch {
            while (_timerSecondsLeft.value > 0 && _isTimerRunning.value) {
                delay(1000)
                if (_isTimerRunning.value) {
                    _timerSecondsLeft.value -= 1
                    Log.d("TimerService", "Timer tick: ${_timerSecondsLeft.value} seconds left")
                }
            }
            
            // Timer finished - notify MainActivity
            Log.d("TimerService", "Timer finished! Seconds left: ${_timerSecondsLeft.value}, emitting finished event")
            if (_timerSecondsLeft.value <= 0) {
                _timerFinished.value = true
                _isTimerRunning.value = false
                releaseWakeLock()
                
                // Reset after a short delay
                kotlinx.coroutines.GlobalScope.launch {
                    delay(1000)
                    _timerFinished.value = false
                }
            }
        }
    }
    
    fun stopTimer() {
        job?.cancel()
        _isTimerRunning.value = false
        releaseWakeLock()
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
        stopTimer()
        super.onDestroy()
    }
}
