package com.homecam.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED && a != "android.intent.action.QUICKBOOT_POWERON") return
        if (Build.VERSION.SDK_INT >= 23 &&
            (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        ) return
        val svc = Intent(context, CameraService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc)
            else context.startService(svc)
        } catch (e: Exception) {
        }
    }
}
