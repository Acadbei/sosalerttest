package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.service.BackgroundMonitorService

class MainApplication : Application() {
    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            super.attachBaseContext(newBase.createAttributionContext("location_attribution"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val monitorIntent = Intent(this, BackgroundMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(monitorIntent)
            } else {
                startService(monitorIntent)
            }
        } catch (e: Exception) {
            // Ignore if OS blocks FGS starts from background immediately
        }
    }
}
