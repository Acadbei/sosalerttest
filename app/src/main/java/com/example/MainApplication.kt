package com.example

import android.app.Application
import android.content.Context
import android.os.Build

class MainApplication : Application() {
    override fun attachBaseContext(newBase: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            super.attachBaseContext(newBase.createAttributionContext("location_attribution"))
        } else {
            super.attachBaseContext(newBase)
        }
    }
}
