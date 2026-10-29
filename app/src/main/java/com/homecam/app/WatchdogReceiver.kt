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
                // EXACT alarm zaroori hai: Android 12+ mein sirf EXACT alarm se trigger hona
                // "background se foreground service start karne" ki exemption list mein aata
                // hai — inexact alarm se start karne ki koshish chup-chap fail ho jati hai.
                val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
                if (Build.VERSION.SDK_INT >= 23 && canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else if (Build.VERSION.SDK_INT >= 23) {
                    // Exact alarm permission nahi mili — inexact fallback (best effort)
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
            } catch (e: Exception) {
            }
        }
    }
}
