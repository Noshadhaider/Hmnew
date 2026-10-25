package com.homecam.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var nameInput: EditText
    private lateinit var info: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        col.addView(TextView(this).apply {
            text = "HomeCam camera"
            textSize = 26f
            gravity = Gravity.CENTER_HORIZONTAL
        })

        col.addView(TextView(this).apply {
            text = "Is phone ka naam (viewer mein yehi dikhega):"
            textSize = 14f
            setPadding(0, dp(16), 0, dp(4))
        })

        nameInput = EditText(this).apply {
            setText(Prefs.deviceName(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        col.addView(nameInput)

        col.addView(Button(this).apply {
            text = "Camera chalu karo"
            isAllCaps = false
            setOnClickListener { startCameraMode() }
        })
        col.addView(Button(this).apply {
            text = "Camera band karo"
            isAllCaps = false
            setOnClickListener { stopCameraMode() }
        })

        info = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(16), 0, 0)
            text = if (Prefs.cameraEnabled(this@MainActivity))
                "Camera chalu hai. Screen band kar sakte hain."
            else "Abhi camera band hai."
        }
        col.addView(info)

        setContentView(ScrollView(this).apply { addView(col) })

        if (!Prefs.cameraEnabled(this)) startCameraMode()
    }

    private fun stopCameraMode() {
        Prefs.setCameraEnabled(this, false)
        stopService(Intent(this, CameraService::class.java))
        info.text = "Camera band kar diya."
    }

    private fun startCameraMode() {
        val name = nameInput.text.toString().trim()
        if (name.isNotEmpty()) Prefs.setDeviceName(this, name)

        val need = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.CAMERA)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.POST_NOTIFICATIONS)

        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), 100)
        } else {
            launchService()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code != 100) return
        val camOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (camOk && micOk) launchService()
        else info.text = "Camera aur mic ki permission dena zaroori hai. \"Camera chalu karo\" dobara dabayen."
    }

    private fun launchService() {
        Prefs.setCameraEnabled(this, true)
        val svc = Intent(this, CameraService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
        info.text = "Camera chalu ho gaya. Ab screen band kar sakte hain.\n" +
            "Battery wale sawal par \"Allow\" kar den."
        askIgnoreBatteryOptimization()
    }

    private fun askIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < 23) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
        }
    }
}
