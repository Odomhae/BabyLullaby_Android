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
        
        // totalSeconds가 0 이하이면 시작하지 않음
        if (totalSeconds <= 0) {
            Log.w("TimerService", "Invalid timer duration: $totalSeconds seconds")
            return
        }
        
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
        // timerSecondsLeft는 리셋하지 않음 (다음 재생 시 이어서 재생할 수 있도록)
        // 단, timer가 완전히 끝났을 때는 0으로 리셋됨
    }
    
    fun resetTimerState() {
        job?.cancel()
        _timerSecondsLeft.value = 0
        _isTimerRunning.value = false
        _timerFinished.value = false
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
