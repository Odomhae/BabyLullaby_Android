package com.odom.lullaby

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TimerState(
    val secondsLeft: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val totalSeconds: Int = 0
)

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context: Context = application.applicationContext
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()
    
    private var timerBroadcastReceiver: BroadcastReceiver? = null
    
    init {
        Log.d("TimerViewModel", "ViewModel initialized")
        
        // Load initial state from SharedPreferences
        loadInitialTimerState()
        
        // Register broadcast receiver to listen for timer updates
        registerTimerReceiver()
    }
    
    private fun loadInitialTimerState() {
        val secondsLeft = sharedPreferences.getInt("timer_seconds_left", 0)
        val isRunning = sharedPreferences.getBoolean("is_timer_running", false)
        val totalMinutes = sharedPreferences.getInt("sleep_timer_minutes", 15)
        val totalSeconds = totalMinutes * 60
        
        Log.d("TimerViewModel", "Initial state loaded - secondsLeft: $secondsLeft, isRunning: $isRunning")
        
        _timerState.value = TimerState(
            secondsLeft = secondsLeft,
            isRunning = isRunning,
            totalSeconds = totalSeconds
        )
    }
    
    private fun registerTimerReceiver() {
        timerBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "TIMER_STATE_UPDATED" -> {
                        val secondsLeft = intent.getIntExtra("seconds_left", 0)
                        val isRunning = intent.getBooleanExtra("is_running", false)
                        
                        Log.d("TimerViewModel", "Received timer update - secondsLeft: $secondsLeft, isRunning: $isRunning")
                        
                        _timerState.value = _timerState.value.copy(
                            secondsLeft = secondsLeft,
                            isRunning = isRunning
                        )
                    }
                    "TIMER_COMPLETED" -> {
                        Log.d("TimerViewModel", "Timer completed")
                        _timerState.value = _timerState.value.copy(
                            secondsLeft = 0,
                            isRunning = false,
                            isFinished = true
                        )
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction("TIMER_STATE_UPDATED")
            addAction("TIMER_COMPLETED")
        }
        
        context.registerReceiver(timerBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        Log.d("TimerViewModel", "Timer broadcast receiver registered")
    }
    
    fun updateTimerFromService(secondsLeft: Int, isRunning: Boolean) {
        Log.d("TimerViewModel", "Updating timer from service - secondsLeft: $secondsLeft, isRunning: $isRunning")
        _timerState.value = _timerState.value.copy(
            secondsLeft = secondsLeft,
            isRunning = isRunning
        )
    }
    
    fun setTotalMinutes(minutes: Int) {
        val totalSeconds = minutes * 60
        _timerState.value = _timerState.value.copy(
            totalSeconds = totalSeconds,
            secondsLeft = if (!_timerState.value.isRunning) totalSeconds else _timerState.value.secondsLeft
        )
        
        // Save to SharedPreferences
        sharedPreferences.edit()
            .putInt("sleep_timer_minutes", minutes)
            .apply()
    }
    
    fun resetFinishedState() {
        _timerState.value = _timerState.value.copy(isFinished = false)
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Unregister broadcast receiver
        timerBroadcastReceiver?.let {
            context.unregisterReceiver(it)
            timerBroadcastReceiver = null
            Log.d("TimerViewModel", "Timer broadcast receiver unregistered")
        }
        
        Log.d("TimerViewModel", "ViewModel cleared")
    }
}
