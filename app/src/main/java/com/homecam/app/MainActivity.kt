package com.homecam.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
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
    private lateinit var web: WebView
    private lateinit var settingsPanel: LinearLayout
    private lateinit var gateScreen: ScrollView
    private var cameraStarted = false

    private data class GateItem(
        val statusView: TextView,
        val check: () -> Boolean,
        val request: () -> Unit
    )
    private val gateItems = ArrayList<GateItem>()

    private val askedPrefs: SharedPreferences
        get() = getSharedPreferences("homecam_asked", MODE_PRIVATE)

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
        root.addView(
            web, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // ---- Settings panel (naam, status), shuru mein chhupa hua ----
        settingsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(0xF2FFFFFF.toInt())
            setPadding(dp(16), dp(56), dp(16), dp(16))
        }
        settingsPanel.addView(TextView(this).apply {
            text = "اس فون کا نام (ویور میں یہی دکھے گا):"
            textSize = 14f
        })
        nameInput = EditText(this).apply {
            setText(Prefs.deviceName(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        settingsPanel.addView(nameInput)
        settingsPanel.addView(Button(this).apply {
            text = "نام محفوظ کریں"
            isAllCaps = false
            setOnClickListener {
                val n = nameInput.text.toString().trim()
                if (n.isNotEmpty()) {
                    Prefs.setDeviceName(this@MainActivity, n)
                    info.text = "نام محفوظ ہو گیا: $n"
                }
            }
        })
        settingsPanel.addView(Button(this).apply {
            text = "اجازتیں دوبارہ چیک کریں"
            isAllCaps = false
            setOnClickListener { showGate() }
        })
        info = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        }
        settingsPanel.addView(info)
        root.addView(
            settingsPanel, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ---- Gate screen: jab tak sab permissions allow na ho, yehi dikhegi ----
        gateScreen = buildGateScreen()
        root.addView(
            gateScreen, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
        web.loadUrl(Config.WEB_URL)

        refreshGate()
    }

    private fun buildGateScreen(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0d0a23"))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }
        col.addView(TextView(this).apply {
            text = "\u06A9\u06CC\u0645\u0631\u06C1 \u0686\u0644\u0627\u0646\u06D2 \u06A9\u06D2 \u0644\u06CC\u06D2 \u06CC\u06C1 \u0633\u0628 \u0627\u062C\u0627\u0632\u062A \u062F\u06CC\u06BA"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(6))
        })
        col.addView(TextView(this).apply {
            text = "\u062C\u0628 \u062A\u06A9 \u0633\u0628 \u2705 \u0646\u06C1 \u06C1\u0648 \u062C\u0627\u0626\u06CC\u06BA\u060C \u06A9\u06CC\u0645\u0631\u06C1 \u0634\u0631\u0648\u0639 \u0646\u06C1\u06CC\u06BA \u06C1\u0648 \u06AF\u0627\u06D4"
            textSize = 13f
            setTextColor(Color.parseColor("#a99fd6"))
            setPadding(0, 0, 0, dp(24))
        })

        fun addItem(label: String, check: () -> Boolean, request: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            val txt = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val status = TextView(this).apply {
                text = "\u2026"
                textSize = 18f
                setPadding(dp(8), 0, dp(8), 0)
            }
            val btn = Button(this).apply {
                text = "\u0627\u062C\u0627\u0632\u062A \u062F\u06CC\u06BA"
                isAllCaps = false
                setOnClickListener { request() }
            }
            row.addView(txt)
            row.addView(status)
            row.addView(btn)
            col.addView(row)
            gateItems.add(GateItem(status, check, request))
        }

        fun askedBefore(key: String): Boolean = askedPrefs.getBoolean(key, false)
        fun markAsked(key: String) = askedPrefs.edit().putBoolean(key, true).apply()

        fun requestOrOpenSettings(perm: String, code: Int, key: String) {
            if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) return
            val permanentlyDenied = askedBefore(key) && !shouldShowRequestPermissionRationale(perm)
            if (permanentlyDenied) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                }
            } else {
                markAsked(key)
                requestPermissions(arrayOf(perm), code)
            }
        }

        addItem(
            "\u06A9\u06CC\u0645\u0631\u06C1",
            { checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED },
            { requestOrOpenSettings(Manifest.permission.CAMERA, 101, "cam") }
        )

        addItem(
            "\u0645\u0627\u0626\u06CC\u06A9\u0631\u0648\u0641\u0648\u0646",
            { checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            { requestOrOpenSettings(Manifest.permission.RECORD_AUDIO, 102, "mic") }
        )

        if (Build.VERSION.SDK_INT >= 33) {
            addItem(
                "نوٹیفیکیشن",
                { checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED },
                { requestOrOpenSettings(Manifest.permission.POST_NOTIFICATIONS, 103, "notif") }
            )
        }

        addItem(
            "\u0628\u06CC\u0679\u0631\u06CC: \u06A9\u0648\u0626\u06CC \u067E\u0627\u0628\u0646\u062F\u06CC \u0646\u06C1\u06CC\u06BA",
            {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(packageName)
            },
            {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                }
            }
        )

        if (Build.VERSION.SDK_INT >= 31) {
            addItem(
                "واچ ڈاگ (درست الارم)",
                {
                    val am = getSystemService(ALARM_SERVICE) as AlarmManager
                    am.canScheduleExactAlarms()
                },
                {
                    try {
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:$packageName"))
                        )
                    } catch (e: Exception) {
                    }
                }
            )
        }

        col.addView(TextView(this).apply {
            text = "ہر چیز اجازت دینے کے بعد خود بخود اگلی اسکرین کھل جائے گی۔"
            textSize = 12f
            setTextColor(Color.parseColor("#a99fd6"))
            setPadding(0, dp(20), 0, 0)
        })

        scroll.addView(col)
        return scroll
    }

    private fun refreshGate() {
        if (gateItems.isEmpty()) return
        var allOk = true
        for (item in gateItems) {
            val ok = item.check()
            item.statusView.text = if (ok) "\u2705" else "\u274c"
            if (!ok) allOk = false
        }
        if (allOk) {
            gateScreen.visibility = View.GONE
            if (!cameraStarted) startCamera()
        } else {
            gateScreen.visibility = View.VISIBLE
        }
    }

    private fun showGate() {
        gateScreen.visibility = View.VISIBLE
        refreshGate()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && gateScreen.visibility != View.VISIBLE && web.canGoBack()) {
            web.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshGate()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        refreshGate()
    }

    private fun startCamera() {
        cameraStarted = true
        Prefs.setCameraEnabled(this, true)
        val svc = Intent(this, CameraService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
            info.text = "کیمرہ ہمیشہ چالو ہے (بیک گراؤنڈ میں)۔ اب اسکرین بند کر سکتے ہیں۔\n" +
                "نام: ${Prefs.deviceName(this)}"
        } catch (e: Exception) {
            info.text = "کیمرہ شروع نہیں ہوا۔"
            cameraStarted = false
        }
    }
}
