# Prayer Notification with Audio Guide

This guide explains how to use the new audio-enhanced prayer notification functionality in the Tasbeeh Counter app.

## Overview

The app now includes a comprehensive system for sending prayer time notifications with Arabic audio playback. The audio plays "Allahu Akbar" (اللہ اکبر) when prayer notifications appear, even when the app is in the background.

## Features

- ✅ Local notifications for prayer times
- ✅ Arabic audio playback ("Allahu Akbar")
- ✅ Background audio support
- ✅ Custom audio file support
- ✅ Vibration patterns
- ✅ Bilingual notification text (English + Arabic)
- ✅ Automatic scheduling based on prayer times
- ✅ Fallback audio system

## Components

### 1. AudioService
Handles audio playback in the background using MediaPlayer.

**Key Methods:**
- `playPrayerAudio()` - Plays default "Allahu Akbar" audio
- `playCustomAudio(audioFileName)` - Plays custom audio file
- `stopAudio()` - Stops current audio playback
- `isPlaying()` - Checks if audio is currently playing

### 2. NotificationService
Enhanced with audio functionality for prayer notifications.

**Key Methods:**
- `sendPrayerNotificationWithAudio(context, prayerName)` - Sends notification with default audio
- `sendPrayerNotificationWithCustomAudio(context, prayerName, audioFileName)` - Sends notification with custom audio

### 3. PrayerNotificationHelper
Utility class with comprehensive examples and helper methods.

## Usage Examples

### Basic Usage

```kotlin
// Send a prayer notification with default "Allahu Akbar" audio
val notificationService = NotificationService()
notificationService.sendPrayerNotificationWithAudio(context, "fajr")
```

### Custom Audio Usage

```kotlin
// Send a prayer notification with custom audio file
val notificationService = NotificationService()
notificationService.sendPrayerNotificationWithCustomAudio(context, "fajr", "custom_adhan")
```

### Using the Helper Class

```kotlin
// Basic prayer notification
PrayerNotificationHelper.sendBasicPrayerNotification(context, "dhuhr")

// Custom audio notification
PrayerNotificationHelper.sendPrayerNotificationWithCustomAudio(context, "asr", "asr_adhan")

// Test audio only (without notification)
PrayerNotificationHelper.testAudioOnly(context, "allahu_akbar")

// Stop current audio
PrayerNotificationHelper.stopAudio(context)
```

## Audio File Setup

### Adding Audio Files

1. **Place audio files in the raw directory:**
   ```
   app/src/main/res/raw/
   ├── allahu_akbar.mp3
   ├── fajr_adhan.mp3
   ├── dhuhr_adhan.mp3
   ├── asr_adhan.mp3
   ├── maghrib_adhan.mp3
   └── isha_adhan.mp3
   ```

2. **Supported formats:** MP3, OGG, WAV

3. **File naming:** Use lowercase letters, numbers, and underscores only

### Audio File Recommendations

- **Duration:** 3-10 seconds for prayer notifications
- **Quality:** 44.1kHz, 16-bit or higher
- **Format:** MP3 (most compatible) or OGG
- **Content:** Clear pronunciation of "Allahu Akbar" or full adhan

## Permissions

The following permissions are automatically included:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

## Testing

### Test Buttons in Settings

The app includes test buttons in the Settings screen:

1. **Test Prayer Notification with Audio** - Tests the basic functionality
2. **Test Next Prayer Notification** - Tests the existing notification system
3. **Test Missed Tasbeeh Alert** - Tests missed tasbeeh notifications

### Programmatic Testing

```kotlin
// Test all prayers
PrayerNotificationHelper.sendAllPrayerNotifications(context)

// Test with delay
PrayerNotificationHelper.sendPrayerNotificationAtTime(context, "fajr", 5000) // 5 seconds

// Test audio only
PrayerNotificationHelper.testAudioOnly(context)
```

## Integration with Existing System

The new audio functionality is automatically integrated with the existing prayer reminder system:

1. **PrayerReminderReceiver** now uses audio-enhanced notifications
2. **Scheduled reminders** automatically include audio
3. **Settings** control both notifications and audio
4. **Background operation** is fully supported

## Customization Options

### Notification Content

The notifications include:
- Prayer name in English
- "Allahu Akbar" in both English and Arabic
- Vibration pattern
- High priority for timely delivery

### Audio Behavior

- **Automatic playback** when notification appears
- **Background support** even when app is closed
- **Error handling** with fallback to system sounds
- **Resource management** with automatic cleanup

### Timing Control

- **Exact alarms** for precise timing (Android 12+)
- **Fallback alarms** for older devices
- **Automatic rescheduling** for daily prayers

## Troubleshooting

### Common Issues

1. **Audio not playing:**
   - Check if audio files are in the correct location
   - Verify file format is supported
   - Check device volume settings

2. **Notifications not appearing:**
   - Verify notification permissions are granted
   - Check if notifications are enabled in app settings
   - Ensure exact alarm permissions are granted (Android 12+)

3. **Background audio issues:**
   - Check if the app is battery optimized
   - Verify foreground service permissions
   - Test with different Android versions

### Debug Information

Enable logging to troubleshoot issues:

```kotlin
// Check if audio is playing
val isPlaying = PrayerNotificationHelper.isAudioPlaying(context)

// Test audio service directly
PrayerNotificationHelper.testAudioOnly(context, "allahu_akbar")
```

## Best Practices

1. **Audio Files:**
   - Use high-quality recordings
   - Keep files small (< 1MB) for fast loading
   - Test on different devices

2. **Notifications:**
   - Respect user notification preferences
   - Provide clear, actionable content
   - Use appropriate priority levels

3. **Background Operation:**
   - Handle service lifecycle properly
   - Clean up resources when done
   - Test on various Android versions

## Example Implementation

Here's a complete example of how to implement prayer notifications with audio:

```kotlin
class PrayerManager {
    fun schedulePrayerReminder(context: Context, prayerName: String, prayerTime: String) {
        // Parse prayer time
        val timeParts = prayerTime.split(":")
        val hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        
        // Schedule alarm
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        
        // If time has passed, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // Create intent for prayer reminder
        val intent = Intent(context, PrayerReminderReceiver::class.java).apply {
            putExtra("prayer", prayerName)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Schedule the alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
    
    fun sendPrayerNotification(context: Context, prayerName: String) {
        // Use the audio-enhanced notification
        PrayerNotificationHelper.sendBasicPrayerNotification(context, prayerName)
    }
}
```

## Conclusion

The audio-enhanced prayer notification system provides a comprehensive solution for Islamic prayer reminders with authentic Arabic audio. The system is designed to work reliably in the background and provides multiple customization options for different use cases.

For support or questions, please refer to the app's feedback system or contact the developer. 