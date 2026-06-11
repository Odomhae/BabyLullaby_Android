# Activity Reset Functionality

Implemented automatic reset of timer and playlist when the activity is recreated (after onDestroy).

## What Happens on Activity Recreation

### 🔄 **Automatic Reset Behavior**

When the MainActivity is destroyed and recreated (e.g., app restart, configuration change), the following happens automatically:

### ⏰ **Timer Reset**

1. **Stop Running Timer**: Any active timer is immediately stopped
2. **Reset Timer State**: Timer state is reset to initial values
3. **Clear Timer Duration**: Timer duration preference is reset to default (15 minutes)

```kotlin
// Reset timer when activity is recreated
LaunchedEffect(Unit) {
    if (isTimerServiceBound && timerService != null) {
        // Stop any running timer
        timerService?.stopTimer()
        // Reset timer state
        timerSecondsLeft = timerSecondsTotal
        isTimerRunning = false
    }
}
```

### 🎵 **Playlist Reset**

1. **Reset to First Song**: Playlist position is reset to index 0 (first song)
2. **Stop Playback**: All playback is stopped
3. **Clear Selection**: Any selected white sound is cleared

```kotlin
if (playlist.isNotEmpty()) {
    playlistPlayer.prepare()
    // Reset to first song and stop playback on activity recreation
    playlistPlayer.seekTo(0)
    playlistPlayer.pause()
    playlistPlayer.stop()
}
```

### 🗑️ **State Cleanup**

In `onDestroy()`, all saved states are cleared to force fresh start:

```kotlin
override fun onDestroy() {
    super.onDestroy()
    releaseWakeLock()
    
    // Clear saved states to force reset on next start
    val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    sharedPreferences.edit()
        .remove("selected_white_sound")
        .remove("sleep_timer_minutes") // Reset timer duration
        .apply()
}
```

## When This Happens

### 📱 **Activity Recreation Scenarios**

- **App Restart**: User closes and reopens the app
- **Configuration Change**: Screen rotation, theme change
- **Memory Pressure**: System kills and recreates activity
- **Developer Options**: "Don't keep activities" enabled

## Benefits

### ✅ **Fresh User Experience**

- **Clean Start**: Users always start with a fresh state
- **No Confusion**: No leftover playback or timer states
- **Predictable Behavior**: Consistent experience every time

### 🎯 **Simplified State Management**

- **No Orphaned States**: Prevents stuck timers or playback
- **Memory Efficient**: Clears unused state data
- **Bug Prevention**: Avoids state-related crashes

### 🔧 **Development Benefits**

- **Easier Debugging**: Fresh state on each run
- **Consistent Testing**: Predictable starting conditions
- **Clean Architecture**: Proper lifecycle management

## User Experience

### 🚀 **What Users See**

1. **App Opens**: Fresh interface with no active playback
2. **Timer Reset**: Timer shows default duration (15 minutes)
3. **Playlist Ready**: First song selected but not playing
4. **Clean State**: No leftover selections or states

### 🎮 **Interaction Flow**

1. User opens app → Fresh state
2. User selects song → Playback starts
3. User sets timer → Timer counts down
4. App is closed/reopened → Everything resets to step 1

## Customization Options

### 🔧 **Disable Reset (Optional)**

If you want to preserve state across activity recreation, you can:

1. **Comment out onDestroy cleanup**:
```kotlin
// override fun onDestroy() {
//     super.onDestroy()
//     releaseWakeLock()
//     // Don't clear states to preserve across recreation
// }
```

2. **Remove timer reset LaunchedEffect**:
```kotlin
// Remove this block to preserve timer state
// LaunchedEffect(Unit) { ... }
```

3. **Keep playlist position**:
```kotlin
// Don't reset playlist position
// playlistPlayer.seekTo(0)
```

## Result

The app now provides a **clean, predictable experience** where every time the user opens the app (or the activity is recreated), they get a fresh start with:
- ✅ Timer reset to default
- ✅ Playlist reset to first song
- ✅ All playback stopped
- ✅ Clean UI state

This ensures users never encounter confusing leftover states and always have a consistent experience!
