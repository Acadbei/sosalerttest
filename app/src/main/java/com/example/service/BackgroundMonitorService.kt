package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BackgroundMonitorService : Service() {

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MONITOR_CHANNEL",
                "Мониторинг SOS",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "MONITOR_CHANNEL")
            .setContentTitle("Ogoh-Alert активен")
            .setContentText("Мониторинг экстренных оповещений работает в фоне")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
            
        startForeground(1002, notification)
        return START_STICKY
    }
}
