package com.example.lacitadellevote.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build

object ExactAlarmPermission {
    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true
    }
}
