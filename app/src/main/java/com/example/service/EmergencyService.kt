package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class EmergencyService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var originalVolume: Int = -1

    companion object {
        const val CHANNEL_ID = "SOS_ALERT_EMERGENCY_CHANNEL"
        const val NOTIFICATION_ID = 911
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_INSTRUCTIONS = "EXTRA_INSTRUCTIONS"
        const val EXTRA_PRIORITY = "EXTRA_PRIORITY"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            super.attachBaseContext(newBase.createAttributionContext("location_attribution"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("EmergencyService", "Служба экстренных оповещений создана")
        initAttributes()
    }

    private fun initAttributes() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            Log.d("EmergencyService", "Получена команда ОСТАНОВИТЬ ТРЕВОГУ")
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "ЭКСТРЕННАЯ ТРЕВОГА"
        val instructions = intent?.getStringExtra(EXTRA_INSTRUCTIONS) ?: "Немедленно следуйте инструкциям безопасности!"
        val priority = intent?.getStringExtra(EXTRA_PRIORITY) ?: "CRITICAL"

        Log.d("EmergencyService", "Запуск звуковой сирены и вибрации для: $title, приоритет: $priority")

        // 1. Setup notification channel
        createNotificationChannel()

        // 2. Build full-screen intent
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("ALERT_ID_TRIGGER", "TRIGGERED")
            putExtra("ALERT_TITLE", title)
            putExtra("ALERT_INSTRUCTIONS", instructions)
            putExtra("ALERT_PRIORITY", priority)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ $title")
            .setContentText(instructions)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.BigTextStyle().bigText(instructions))
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // 3. Play alarm or siren siren at maximum volume
        playSiren()

        // 4. Continuous vibration
        vibratePhone()

        return START_STICKY
    }

    private fun playSiren() {
        try {
            // Safe cleanup of any pre-existing MediaPlayer to avoid native sound thread leaks
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                }
            } catch (ex: Exception) {
                Log.e("EmergencyService", "Error releasing old mediaPlayer", ex)
            }
            mediaPlayer = null

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // Save original volume to restore it later if needed, but force stream max volume
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            Log.d("EmergencyService", "Попытка воспроизведения сирены из ресурсов (siren.mp3)")
            // First attempt: play bundled raw/siren.mp3 with correct pre-preparation attribute order
            try {
                mediaPlayer = MediaPlayer().apply {
                    val afd = resources.openRawResourceFd(com.example.R.raw.siren)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d("EmergencyService", "Сирена из siren.mp3 успешно запущена")
            } catch (ex: Exception) {
                Log.e("EmergencyService", "Не удалось запустить siren.mp3 из ресурсов, переход на системный звук", ex)
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@EmergencyService, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            }
            Log.d("EmergencyService", "Сирена запущена на максимальной громкости: $maxVolume")
        } catch (e: Exception) {
            Log.e("EmergencyService", "Ошибка при запуске сирены", e)
            try {
                // Fallback to synthesizing sound or alternative
                val defaultTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@EmergencyService, defaultTone)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (fallbackEx: Exception) {
                Log.e("EmergencyService", "Ошибка альтернативного воспроизведения", fallbackEx)
            }
        }
    }

    private fun vibratePhone() {
        try {
            vibrator?.let { vib ->
                // Cancel existing vibrations first to prevent thread stacking
                vib.cancel()
                if (vib.hasVibrator()) {
                    val pattern = longArrayOf(0, 1000, 500, 1000, 500) // Vibrate 1s, pause 0.5s
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(pattern, 0)
                    }
                    Log.d("EmergencyService", "Запущена непрерывная вибрация")
                }
            }
        } catch (e: Exception) {
            Log.e("EmergencyService", "Не удалось запустить вибрацию", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Экстренные Оповещения SOS Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для мгновенных тревожных извещений граждан с сиреной"
                enableLights(true)
                lightColor = 0xFFFF0000.toInt()
                enableVibration(true)
                setBypassDnd(true) // Bypass "Do Not Disturb" mode
                setSound(null, null) // Sound is handled manually at max volume
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d("EmergencyService", "Остановка службы SOS Оповещения")
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
            } catch (e: Exception) {
                Log.e("EmergencyService", "Ошибка остановки воспроизведения медиаплеера", e)
            }
            try {
                mp.release()
            } catch (e: Exception) {
                Log.e("EmergencyService", "Ошибка освобождения медиаплеера", e)
            }
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("EmergencyService", "Ошибка остановки вибрации", e)
        }

        // Restore original stream volume if available
        if (originalVolume != -1) {
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            } catch (e: Exception) {
                Log.e("EmergencyService", "Ошибка восстановления громкости", e)
            }
        }

        super.onDestroy()
    }
}
