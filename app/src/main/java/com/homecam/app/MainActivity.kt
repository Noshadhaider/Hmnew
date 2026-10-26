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
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var nameInput: EditText
    private lateinit var info: TextView
    private var askedBattery = false
    private lateinit var web: WebView
    private lateinit var settingsPanel: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = android.widget.FrameLayout(this)

        // ---- WebView: poori screen par Config.WEB_URL ----
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = false
            }
            webChromeClient = WebChromeClient()
        }
        root.addView(web, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT))

        // ---- Settings panel (naam, permissions, status), shuru mein chhupa hua ----
        settingsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(0xF2FFFFFF.toInt())
            setPadding(dp(16), dp(56), dp(16), dp(16))
        }
        settingsPanel.addView(TextView(this).apply {
            text = "Is phone ka naam (viewer mein yehi dikhega):"
            textSize = 14f
        })
        nameInput = EditText(this).apply {
            setText(Prefs.deviceName(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        settingsPanel.addView(nameInput)
        settingsPanel.addView(Button(this).apply {
            text = "Naam save karo"
            isAllCaps = false
            setOnClickListener {
                val n = nameInput.text.toString().trim()
                if (n.isNotEmpty()) {
                    Prefs.setDeviceName(this@MainActivity, n)
                    info.text = "Naam save ho gaya: $n"
                }
            }
        })
        settingsPanel.addView(Button(this).apply {
            text = "Permissions do"
            isAllCaps = false
            setOnClickListener {
                askedBattery = false
                askPermissions()
            }
        })
        info = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        }
        settingsPanel.addView(info)
        root.addView(settingsPanel, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT))


        setContentView(root)

        web.loadUrl(Config.WEB_URL)

        askPermissions()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (hasCoreAccess()) startCamera()
    }

    private fun hasCoreAccess(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
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
            afterRuntimePermissions()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code != 100) return
        if (hasCoreAccess()) {
            afterRuntimePermissions()
        } else {
            info.text = "Camera aur mic ki permission nahi mili. Agar popup dobara na aaye to " +
                "\"Permissions do\" dabayen, phir app ki settings khulengi wahan Permissions mein Camera aur Microphone ko Allow karen."
            openAppSettingsIfBlocked()
        }
    }

    private fun openAppSettingsIfBlocked() {
        val blocked = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        if (blocked && !hasCoreAccess()) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
            }
        }
    }

    private fun afterRuntimePermissions() {
        startCamera()
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName) && !askedBattery) {
                askedBattery = true
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun startCamera() {
        Prefs.setCameraEnabled(this, true)
        val svc = Intent(this, CameraService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
            info.text = "Camera hamesha chalu hai (background mein). Ab screen band kar sakte hain.\n" +
                "Naam: ${Prefs.deviceName(this)}"
        } catch (e: Exception) {
            info.text = "Camera shuru nahi hua. \"Permissions do\" dabayen."
        }
    }
}
