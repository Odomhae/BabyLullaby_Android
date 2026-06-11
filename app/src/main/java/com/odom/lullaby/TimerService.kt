package com.odom.lullaby

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class TimerService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _timerSecondsLeft = MutableStateFlow(0)
    val timerSecondsLeft: StateFlow<Int> = _timerSecondsLeft

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    // 일회성 이벤트는 SharedFlow 사용 (StateFlow + 지연 리셋 패턴은 수집 공백 동안 이벤트가 유실됨)
    private val _timerFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val timerFinished: SharedFlow<Unit> = _timerFinished

    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
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
        acquireWakeLock()

        job = serviceScope.launch {
            // delay(1000) 반복 호출은 틱마다 오차가 누적되므로 마감 시각 기준으로 남은 시간을 계산
            val endTime = SystemClock.elapsedRealtime() + totalSeconds * 1000L
            while (true) {
                val remainingMs = endTime - SystemClock.elapsedRealtime()
                if (remainingMs <= 0) break
                _timerSecondsLeft.value = ((remainingMs + 999) / 1000).toInt()
                delay(minOf(1000L, remainingMs))
            }

            Log.d("TimerService", "Timer finished, stopping playback")
            _isTimerRunning.value = false
            _timerSecondsLeft.value = 0
            releaseWakeLock()
            stopPlayback()
            _timerFinished.tryEmit(Unit)
        }
    }

    // Activity가 파괴된 상태에서도 타이머 종료 시 재생을 멈출 수 있도록 서비스에서 직접 정지
    // (Activity의 timerFinished 수집기는 UI 정리만 담당)
    private fun stopPlayback() {
        try {
            PlaybackService.getPlaylistPlayer()?.let { player ->
                player.pause()
                player.stop()
                player.seekTo(0, 0L)
            }
            PlaybackService.getWhiteSoundPlayer()?.let { player ->
                player.pause()
                player.stop()
            }
        } catch (e: Exception) {
            Log.e("TimerService", "Error stopping playback: ${e.message}")
        }
    }

    fun stopTimer() {
        job?.cancel()
        job = null
        _isTimerRunning.value = false
        releaseWakeLock()
        // timerSecondsLeft는 리셋하지 않음 (일시정지 후 남은 시간부터 이어가도록)
    }

    fun resetTimerState() {
        stopTimer()
        _timerSecondsLeft.value = 0
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
        serviceScope.cancel()
        super.onDestroy()
    }
}
