package com.example.lacitadellevote

import android.app.Application
import com.example.lacitadellevote.notif.NotificationHelper

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createDefaultChannel(this)
    }
}
