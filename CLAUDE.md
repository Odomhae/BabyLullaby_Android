# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run a single unit test class
./gradlew test --tests "com.odom.lullaby.ExampleUnitTest"
```

Build from Android Studio is also standard: Run > Run 'app' (Shift+F10).

## Architecture

Single-activity app (`MainActivity`) with Jetpack Compose UI, two background services, and two independent ExoPlayer instances.

### Key invariant: mutual exclusion
The lullaby playlist player and the white sound player cannot both play simultaneously. Starting one always stops the other. This is enforced in `MainActivity.onCreate` at the `onPlay`/`onAddToPlaylist` callbacks passed into each page composable.

### Service layer

**`PlaybackService`** (`MediaSessionService`) — foreground service for background playback. The ExoPlayer instances are created by `MainActivity` and registered into the companion object via `PlaybackService.registerPlayers(...)` (registered in `DisposableEffect(playlistPlayer)`, unregistered in its `onDispose`). `getPlaylistPlayer()`/`getWhiteSoundPlayer()` resolve from the service instance first, then the registered players. Note: `setPlayers(...)` exists but is never called — the instance-field path is vestigial.

**`TimerService`** — bound service (not foreground). Counts down against a `SystemClock.elapsedRealtime()` deadline (no `delay(1000)` drift) and exposes `timerSecondsLeft`/`isTimerRunning` as `StateFlow` plus `timerFinished` as a one-shot `SharedFlow`. `MainActivity` binds/unbinds in `onStart`/`onStop`; the bound reference is held in `mutableStateOf` properties so `LaunchedEffect` keys restart on connection. **On timer finish, `TimerService` stops playback itself** via `PlaybackService.getPlaylistPlayer()`/`getWhiteSoundPlayer()` (works even if the activity is gone); `MainActivity`'s `timerFinished` collector only does UI cleanup (notifications, timer display, wakelock). `stopTimer()` preserves remaining seconds (pause/resume); `resetTimerState()` zeroes them (stop button / white sound unselect call this).

### UI layer (all Compose)

`MainActivity` hosts everything in a single `setContent` block:
- `HorizontalPager` with two tabs: **Lullaby** (page 0) and **White Noises** (page 1)
- `PlaylistScreen` — playback controls (prev/play/stop/next) rendered below the pager, visible only on the Lullaby tab
- `PlaylistPage` — `LazyColumn` of all MP3s from `assets/lullaby/`. Tap toggles a track in/out of the playlist; shows playlist position badge when added.
- `WhiteSoundsPage` — 2-column `LazyVerticalGrid` of `WhiteSoundItem`s from `assets/whitesound/`. Tap to play (loops with `REPEAT_MODE_ONE`); tap again to stop.
- `SleepTimerDialog` — `AlertDialog` for entering timer minutes (1–180).
- `BannerAdView` — AdMob banner shown at bottom of screen and inside the exit bottom sheet.
- `DoubleBackToExitApp` — `ModalBottomSheet` triggered on back press; also triggers Google Play in-app review flow.

### Data flow

- Timer auto-starts via `LaunchedEffect(isPlaylistPlaying, isWhiteSoundPlaying, isTimerServiceBound)` when any playback begins. It resumes from `timerSecondsLeft` if paused mid-count.
- Playlist order is persisted to `SharedPreferences("playlist_prefs")` under key `playlist_order` (comma-separated media IDs) whenever the playlist `SnapshotStateList` changes.
- Selected white sound is persisted to `SharedPreferences("app_prefs")` under key `selected_white_sound` (media ID string).
- Timer duration is persisted under `app_prefs` key `sleep_timer_minutes` (Int, default 15).
- Dark/light theme preference is persisted under `app_prefs` key `is_dark_theme` (Boolean, default `true`).

### Media IDs

All media items use the URI string as media ID, in the format:
```
asset:///lullaby/filename.mp3
asset:///whitesound/birds.mp3
```

### Notifications

Two `PlayerNotificationManager` instances (IDs 1 and 2, channel `playback_channel`) are created in `MainActivity` and attached to the player only while that player is playing. They are detached (set to `null`) on stop/idle.

## AdMob IDs

`strings.xml` contains both test and real AdMob IDs as named string resources. The app currently uses `TEST_admob_app_id` / `TEST_admob_banner_id`. Before release, switch to `REAL_admob_app_id` / `REAL_admob_banner_id`.

## Adding New White Sounds

1. Drop the audio file into `app/src/main/assets/whitesound/`.
2. Add a drawable image resource for it.
3. Add a `WhiteSoundItem` entry to the hardcoded list in `MainActivity.onCreate` (search for `whiteSoundItems = remember`).
4. Add a `sound_*` string resource to all `res/values-*/strings.xml` files for localization.

## Adding New Lullaby Tracks

Drop the MP3 (or M4A/WAV/OGG/AAC) file into `app/src/main/assets/lullaby/`. It is automatically discovered and listed — no code change needed.
