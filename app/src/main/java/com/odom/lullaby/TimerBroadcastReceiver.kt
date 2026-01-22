package com.odom.lullaby

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TimerBroadcastReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("TimerBroadcastReceiver", "Timer alarm received - stopping playback")
        
        // Stop PlaybackService
        val playbackIntent = Intent(context, PlaybackService::class.java).apply {
            action = "ACTION_STOP_PLAYBACK"
        }
        context.startService(playbackIntent)

        // Stop TimerService
        val timerIntent = Intent(context, TimerService::class.java)
        context.stopService(timerIntent)
        
        // Show completion notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        
        // Clear timer state
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putInt("timer_seconds_left", 0)
            .putBoolean("is_timer_running", false)
            .apply()

        context.stopService(playbackIntent)
        
        Log.d("TimerBroadcastReceiver", "Timer completed and playback stopped")
    }
}
