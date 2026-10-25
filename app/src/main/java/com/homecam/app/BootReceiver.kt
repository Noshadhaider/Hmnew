package com.homecam.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED && a != "android.intent.action.QUICKBOOT_POWERON") return
        if (!Prefs.cameraEnabled(context) || !Db.validRoom(Prefs.room(context))) return
        val svc = Intent(context, CameraService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc)
            else context.startService(svc)
        } catch (e: Exception) {
        }
    }
}
