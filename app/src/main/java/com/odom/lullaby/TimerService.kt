package com.odom.lullaby

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
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
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var alarmManager: AlarmManager
    
    companion object {
        private const val TIMER_ALARM_REQUEST_CODE = 1001
    }
    
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
        sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        // Only reset if timer is not actually running
        val wasRunning = sharedPreferences.getBoolean("is_timer_running", false)
        if (!wasRunning) {
            resetTimerState()
        } else {
            // Restore timer state from SharedPreferences
            val savedSeconds = sharedPreferences.getInt("timer_seconds_left", 0)
            if (savedSeconds > 0) {
                _timerSecondsLeft.value = savedSeconds
                _isTimerRunning.value = true
                Log.d("TimerService", "Restored timer state: $savedSeconds seconds")

                // CRITICAL: Restart the timer countdown
                acquireWakeLock()
                scheduleAlarm(savedSeconds)

                job = CoroutineScope(Dispatchers.Main).launch {
                    while (_timerSecondsLeft.value > 0 && _isTimerRunning.value) {
                        delay(1000)
                        if (_isTimerRunning.value) {
                            _timerSecondsLeft.value -= 1
                            sharedPreferences.edit()
                                .putInt("timer_seconds_left", _timerSecondsLeft.value)
                                .apply()

                            Log.d("TimerService", "Timer tick: ${_timerSecondsLeft.value} seconds left")
                            
                            // Notify ViewModel of timer update
                            notifyViewModelTimerUpdate(_timerSecondsLeft.value, _isTimerRunning.value)
                        }
                    }

                    // Handle completion...
                    Log.d("TimerService", "Timer finished! Seconds left: ${_timerSecondsLeft.value}, emitting finished event")
                    if (_timerSecondsLeft.value <= 0) {
                        _timerFinished.value = true
                        _isTimerRunning.value = false

                        // Cancel alarm since timer completed normally
                        cancelAlarm()

                        // Clear timer state from SharedPreferences when finished
                        sharedPreferences.edit()
                            .putInt("timer_seconds_left", 0)
                            .putBoolean("is_timer_running", false)
                            .apply()

                        releaseWakeLock()

                        // Notify PlaybackService that timer completed
                        notifyPlaybackServiceTimerCompleted()
                        
                        // Reset after a short delay
                        kotlinx.coroutines.GlobalScope.launch {
                            delay(1000)
                            _timerFinished.value = false
                        }
                    }
                }
            }
        }
        
        Log.d("TimerService", "Timer state reset on app restart")
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
        
     //   stopTimer() // Cancel any existing timer
        job?.cancel()

        // 2. 알람도 새 시간을 위해 취소
        cancelAlarm()

        _timerSecondsLeft.value = totalSeconds
        _isTimerRunning.value = true
        
        // Save timer state to SharedPreferences
        sharedPreferences.edit()
            .putInt("timer_seconds_left", totalSeconds)
            .putBoolean("is_timer_running", true)
            .apply()
        
        // Schedule alarm as backup
        scheduleAlarm(totalSeconds)
        
        Log.d("TimerService", "Timer started, seconds left: ${_timerSecondsLeft.value}, running: ${_isTimerRunning.value}")
        acquireWakeLock()
        
        job = CoroutineScope(Dispatchers.Main).launch {
            while (_timerSecondsLeft.value > 0 && _isTimerRunning.value) {
                delay(1000)
                if (_isTimerRunning.value) {
                    _timerSecondsLeft.value -= 1
                    
                    // Update saved state every second
                    sharedPreferences.edit()
                        .putInt("timer_seconds_left", _timerSecondsLeft.value)
                        .apply()
                    
                    Log.d("TimerService", "Timer tick: ${_timerSecondsLeft.value} seconds left")
                    
                    // Notify ViewModel of timer update
                    notifyViewModelTimerUpdate(_timerSecondsLeft.value, _isTimerRunning.value)
                }
            }
            
            // Timer finished - notify MainActivity
            Log.d("TimerService", "Timer finished! Seconds left: ${_timerSecondsLeft.value}, emitting finished event")
            if (_timerSecondsLeft.value <= 0) {
                _timerFinished.value = true
                _isTimerRunning.value = false
                
                // Cancel alarm since timer completed normally
                cancelAlarm()
                
                // Clear timer state from SharedPreferences when finished
                sharedPreferences.edit()
                    .putInt("timer_seconds_left", 0)
                    .putBoolean("is_timer_running", false)
                    .apply()
                
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
        
        // Cancel alarm
        cancelAlarm()
        
        // Update SharedPreferences to reflect stopped state
        sharedPreferences.edit()
            .putBoolean("is_timer_running", false)
            .apply()
        
        releaseWakeLock()
        // timerSecondsLeft는 리셋하지 않음 (다음 재생 시 이어서 재생할 수 있도록)
        // 단, timer가 완전히 끝났을 때는 0으로 리셋됨
    }
    
    fun resetTimerState() {
        job?.cancel()
        _timerSecondsLeft.value = 0
        _isTimerRunning.value = false
        _timerFinished.value = false
        
        // Cancel alarm
        cancelAlarm()
        
        // Clear all timer state from SharedPreferences
        sharedPreferences.edit()
            .putInt("timer_seconds_left", 0)
            .putBoolean("is_timer_running", false)
            .apply()
        
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
    
    private fun notifyPlaybackServiceTimerCompleted() {
        try {
            val intent = Intent("TIMER_COMPLETED")
            intent.putExtra("action", "STOP_PLAYBACK")
            sendBroadcast(intent)
            Log.d("TimerService", "Sent TIMER_COMPLETED broadcast to PlaybackService")
        } catch (e: Exception) {
            Log.e("TimerService", "Failed to send broadcast: ${e.message}")
        }
    }
    
    private fun notifyViewModelTimerUpdate(secondsLeft: Int, isRunning: Boolean) {
        try {
            val intent = Intent("TIMER_STATE_UPDATED")
            intent.putExtra("seconds_left", secondsLeft)
            intent.putExtra("is_running", isRunning)
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("TimerService", "Failed to send timer update broadcast: ${e.message}")
        }
    }
    
    private fun scheduleAlarm(secondsFromNow: Int) {
        try {
            val intent = Intent(this, TimerBroadcastReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                TIMER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val triggerTime = System.currentTimeMillis() + (secondsFromNow * 1000L)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            
            Log.d("TimerService", "Alarm scheduled for $secondsFromNow seconds from now")
        } catch (e: Exception) {
            Log.e("TimerService", "Failed to schedule alarm: ${e.message}")
        }
    }
    
    private fun cancelAlarm() {
        try {
            val intent = Intent(this, TimerBroadcastReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                TIMER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
            Log.d("TimerService", "Alarm cancelled")
        } catch (e: Exception) {
            Log.e("TimerService", "Failed to cancel alarm: ${e.message}")
        }
    }
}
