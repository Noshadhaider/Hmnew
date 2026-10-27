package com.homecam.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * Har 15 minute mein khud ko fire karta hai aur CameraService ko (agar kisi wajah se
 * band ho chuki ho) wapas start kar deta hai. Ye official AlarmManager + BroadcastReceiver
 * tareeqa hai — Play Store policy ke mutabiq, koi misused permission (jaise Accessibility
 * Service) istemal nahi karta.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = Intent(context, CameraService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc)
            else context.startService(svc)
        } catch (e: Exception) {
        }
        schedule(context)
    }

    companion object {
        private const val INTERVAL_MS = 3 * 60 * 1000L // 3 minute

        fun schedule(context: Context) {
            try {
                val intent = Intent(context, WatchdogReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= 23)
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                else PendingIntent.FLAG_UPDATE_CURRENT
                val pi = PendingIntent.getBroadcast(context, 2, intent, flags)
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
                // setAndAllowWhileIdle: Doze mode mein bhi chal jata hai, aur koi extra
                // "exact alarm" permission (Android 12+) ki zaroorat nahi padti — timing
                // thodi approximate ho sakti hai (watchdog ke liye kaafi hai).
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            } catch (e: Exception) {
            }
        }
    }
}
