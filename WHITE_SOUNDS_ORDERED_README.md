# White Sound Ordered Grid System

This system provides **ordered white sound grid** with custom Compose icons and display names.

## How It Works

1. **WhiteSoundItem.kt** - Data class:
   - `fileName`: Audio file name (e.g., "rain.mp3")
   - `displayName`: User-friendly name (e.g., "Rain")
   - `icon`: Compose icon (e.g., `Icons.Default.WaterDrop`)
   - `order`: Grid position (0-based)

2. **WhiteSoundConfig.kt** - Configuration that:
   - Maps file names to display names, icons, and orders
   - Generates ordered items from actual files in assets
   - Provides intelligent pattern matching

## File Recognition & Ordering

The system automatically matches file names to configurations:

| Order | File Pattern | Display Name | Compose Icon |
|--------|---------------|---------------|--------------|
| 0 | `rain.*`, `water.*` | Rain | `Icons.Default.WaterDrop` |
| 1 | `ocean.*`, `sea.*` | Ocean | `Icons.Default.Waves` |
| 2 | `forest.*`, `nature.*` | Forest | `Icons.Default.Forest` |
| 3 | `wind.*`, `air.*` | Wind | `Icons.Default.Air` |
| 4 | `fire.*` | Fire | `Icons.Default.LocalFireDepartment` |
| 5 | `thunder.*`, `lightning.*` | Thunder | `Icons.Default.FlashOn` |
| 6 | `birds.*`, `bird.*` | Birds | `Icons.Default.Flight` |
| 7 | `crickets.*`, `cricket.*` | Crickets | `Icons.Default.Pets` |
| 8 | `fan.*` | Fan | `Icons.Default.AcUnit` |
| 9 | `white.*`, `noise.*` | White Noise | `Icons.Default.AcUnit` |

## File Examples

| File Name | Display Name | Icon | Order |
|-----------|---------------|-------|-------|
| `rain.mp3` | Rain | 💧 | 0 |
| `heavy_rain.wav` | Heavy Rain | 💧 | 0 |
| `ocean_waves.m4a` | Ocean Waves | 🌊 | 1 |
| `forest_night.mp3` | Forest Night | 🌲 | 2 |
| `gentle_wind.mp3` | Gentle Wind | 🌬️ | 3 |
| `campfire.mp3` | Campfire | 🔥 | 4 |
| `thunder_storm.mp3` | Thunder Storm | ⚡ | 5 |
| `morning_birds.mp3` | Morning Birds | 🦅 | 6 |
| `summer_crickets.wav` | Summer Crickets | 🦗 | 7 |

## Smart Features

### 🧠 **Intelligent Matching**
- **Partial matching**: `rain_heavy.mp3` matches "rain" pattern
- **Case-insensitive**: `RAIN.mp3` works same as `rain.mp3`
- **Flexible patterns**: Supports various naming conventions
- **Order control**: Exact positioning in grid (0-9+)

### 🔄 **Dynamic Loading**
- **No code changes**: Just drop files in assets folder
- **Automatic icons**: Based on file name patterns
- **Maintains order**: Uses specified order for grid layout
- **Fallback handling**: Graceful degradation for unknown files

## Adding New Sounds

To add a new white sound:

1. **Add audio file** to `assets/whitesound/`:
   ```
   assets/whitesound/my_new_sound.mp3
   ```

2. **Update configuration** in `WhiteSoundConfig.kt`:
   ```kotlin
   "my_new_sound" to SoundConfig("My New Sound", Icons.Default.MusicNote, 10)
   ```

### Example Addition
```kotlin
// Add to soundConfigurations map
"my_sound" to SoundConfig("My Sound", Icons.Default.MusicNote, 10),

// This will create:
WhiteSoundItem(
    fileName = "my_sound.mp3",
    displayName = "My Sound", 
    icon = Icons.Default.MusicNote,
    order = 10
)
```

## Benefits

- **Ordered Grid**: Complete control over item positioning
- **Smart Recognition**: Automatic pattern matching
- **Compose Icons**: Native Material Design icons
- **Zero Configuration**: Just add files and update config
- **Maintainable**: Easy to extend and modify
- **Fallback Support**: Handles missing or unknown files gracefully

The system gives you complete control over the white sound grid order, appearance, and behavior while automatically detecting files from your assets folder!
