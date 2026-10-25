package com.homecam.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Base64
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

class CameraService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var roomRef: DatabaseReference? = null
    private var cmdListener: ValueEventListener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var firstCmdSeen = false
    private var lastCmdId = 0L
    private var session: LiveSession? = null
    private var busyPhoto = false
    private var started = false

    private val heartbeat = object : Runnable {
        override fun run() {
            sendStatus()
            handler.postDelayed(this, 20_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (!started) {
            val room = Prefs.room(this)
            if (!Db.validRoom(room)) {
                stopSelf()
                return START_NOT_STICKY
            }
            started = true
            acquireLocks()
            Rtc.init(this)
            val egl = EglBase.create()
            eglBase = egl
            val adm = JavaAudioDeviceModule.builder(this)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .createAudioDeviceModule()
            factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(SoftwareVideoEncoderFactory())
                .setVideoDecoderFactory(SoftwareVideoDecoderFactory())
                .createPeerConnectionFactory()
            val ref = Db.deviceRef(this)
            roomRef = ref
            listenForCommands(ref)
            handler.post(heartbeat)
        }
        return START_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("homecam", "HomeCam", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "homecam")
        else Notification.Builder(this)
        val n = b.setContentTitle("HomeCam chal raha hai")
            .setContentText("Camera taiyar hai")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(
                    1, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            startForeground(1, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homecam:service").apply {
            setReferenceCounted(false)
            acquire()
        }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "homecam:wifi").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun sendStatus() {
        val ref = roomRef ?: return
        val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val plugged = (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        ref.child("status").setValue(
            mapOf(
                "ts" to ServerValue.TIMESTAMP,
                "name" to Prefs.deviceName(this),
                "live" to (session != null),
                "battery" to pct,
                "charging" to plugged
            )
        )
    }

    private fun listenForCommands(ref: DatabaseReference) {
        val l = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val m = snap.value as? Map<*, *> ?: return
                val id = (m["id"] as? Number)?.toLong() ?: return
                if (!firstCmdSeen) {
                    firstCmdSeen = true
                    lastCmdId = id
                    return
                }
                if (id == lastCmdId) return
                lastCmdId = id
                val action = m["action"] as? String ?: return
                val facing = m["facing"] as? String ?: "back"
                val sid = m["sid"] as? String ?: ""
                val flash = (m["flash"] as? Boolean ?: false) && facing != "front"
                when (action) {
                    "photo" -> takePhoto(facing, id, flash)
                    "live" -> startSession(sid, facing, true, flash)
                    "mic" -> startSession(sid, facing, false, false)
                    "stop" -> stopSession()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        cmdListener = l
        ref.child("cmd").addValueEventListener(l)
    }

    @Suppress("DEPRECATION")
    private fun setTorch(cap: CameraVideoCapturer?, on: Boolean): Boolean {
        if (cap == null) return false
        return try {
            val base = Class.forName("org.webrtc.CameraCapturer")
            val sf = base.getDeclaredField("currentSession")
            sf.isAccessible = true
            val sess = sf.get(cap) ?: return false
            val cf = sess.javaClass.getDeclaredField("camera")
            cf.isAccessible = true
            val cam = cf.get(sess) as android.hardware.Camera
            val p = cam.parameters
            val modes = p.supportedFlashModes ?: return false
            val want = if (on) android.hardware.Camera.Parameters.FLASH_MODE_TORCH
            else android.hardware.Camera.Parameters.FLASH_MODE_OFF
            if (!modes.contains(want)) return false
            p.flashMode = want
            cam.parameters = p
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createCapturer(facing: String, flash: Boolean): CameraVideoCapturer? {
        val front = facing == "front"
        val enumerator: CameraEnumerator =
            if (!flash && Camera2Enumerator.isSupported(this)) Camera2Enumerator(this)
            else Camera1Enumerator(false)
        val names = enumerator.deviceNames
        val pick = names.firstOrNull {
            if (front) enumerator.isFrontFacing(it) else enumerator.isBackFacing(it)
        } ?: names.firstOrNull()
        return pick?.let { enumerator.createCapturer(it, null) }
    }

    private fun startSession(sid: String, facing: String, video: Boolean, flash: Boolean) {
        stopSession()
        if (sid.isEmpty() || factory == null) return
        val s = LiveSession(sid)
        session = s
        try {
            s.start(facing, video, flash)
            handler.postDelayed(autoStop, 15 * 60 * 1000L)
        } catch (e: Exception) {
            stopSession()
        }
        sendStatus()
    }

    private val autoStop = Runnable { stopSession() }

    private fun stopSession() {
        handler.removeCallbacks(autoStop)
        session?.stop()
        session = null
        sendStatus()
    }

    private inner class LiveSession(val sid: String) {
        private val base = roomRef!!.child("rtc").child(sid)
        private var pc: PeerConnection? = null
        private var capturer: CameraVideoCapturer? = null
        private var helper: SurfaceTextureHelper? = null
        private var videoSource: VideoSource? = null
        private var videoTrack: VideoTrack? = null
        private var audioSource: AudioSource? = null
        private var audioTrack: AudioTrack? = null
        private var answerListener: ValueEventListener? = null
        private var iceListener: ChildEventListener? = null
        private val pendingIce = ArrayList<IceCandidate>()

        fun start(facing: String, video: Boolean, flash: Boolean) {
            val f = factory ?: return
            val egl = eglBase ?: return
            base.removeValue()

            val p = f.createPeerConnection(Rtc.config(), object : SimplePcObserver() {
                override fun onIceCandidate(c: IceCandidate?) {
                    if (c != null) base.child("camIce").push().setValue(Rtc.candidateToMap(c))
                }
            }) ?: return
            pc = p

            val ac = MediaConstraints()
            ac.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
            ac.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
            ac.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
            ac.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            audioSource = f.createAudioSource(ac)
            audioTrack = f.createAudioTrack("a0", audioSource)
            p.addTrack(audioTrack, listOf("s0"))

            if (video) {
                val cap = createCapturer(facing, flash)
                if (cap != null) {
                    val h = SurfaceTextureHelper.create("cap", egl.eglBaseContext)
                    helper = h
                    val vs = f.createVideoSource(false)
                    videoSource = vs
                    cap.initialize(h, applicationContext, vs.capturerObserver)
                    cap.startCapture(640, 480, 15)
                    capturer = cap
                    if (flash) {
                        handler.postDelayed({ setTorch(cap, true) }, 900)
                        handler.postDelayed({ setTorch(cap, true) }, 2500)
                    }
                    val vt = f.createVideoTrack("v0", vs)
                    videoTrack = vt
                    p.addTrack(vt, listOf("s0"))
                }
            }

            p.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(d: SessionDescription?) {
                    if (d == null) return
                    p.setLocalDescription(SimpleSdpObserver(), d)
                    base.child("offer").setValue(Rtc.descToMap(d))
                }
            }, Rtc.constraints())

            val al = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val m = snap.value as? Map<*, *> ?: return
                    val sdp = m["sdp"] as? String ?: return
                    if (p.remoteDescription != null) return
                    p.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            handler.post {
                                pendingIce.forEach { p.addIceCandidate(it) }
                                pendingIce.clear()
                            }
                        }
                    }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            answerListener = al
            base.child("answer").addValueEventListener(al)

            val il = object : ChildEventListener {
                override fun onChildAdded(snap: DataSnapshot, prev: String?) {
                    val m = snap.value as? Map<*, *> ?: return
                    val c = Rtc.mapToCandidate(m) ?: return
                    if (p.remoteDescription != null) p.addIceCandidate(c) else pendingIce.add(c)
                }

                override fun onChildChanged(snap: DataSnapshot, prev: String?) {}
                override fun onChildRemoved(snap: DataSnapshot) {}
                override fun onChildMoved(snap: DataSnapshot, prev: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            }
            iceListener = il
            base.child("viewIce").addChildEventListener(il)
        }

        fun stop() {
            answerListener?.let { base.child("answer").removeEventListener(it) }
            iceListener?.let { base.child("viewIce").removeEventListener(it) }
            try { capturer?.stopCapture() } catch (e: Exception) {}
            capturer?.dispose()
            videoTrack?.dispose()
            videoSource?.dispose()
            helper?.dispose()
            audioTrack?.dispose()
            audioSource?.dispose()
            pc?.close()
            pc?.dispose()
            pc = null
            base.removeValue()
        }
    }

    private fun takePhoto(facing: String, cmdId: Long, flash: Boolean) {
        val f = factory ?: return
        val egl = eglBase ?: return
        if (busyPhoto || session != null) return
        val cap = createCapturer(facing, flash) ?: return
        busyPhoto = true

        val h = SurfaceTextureHelper.create("photo", egl.eglBaseContext)
        val vs = f.createVideoSource(false)
        cap.initialize(h, applicationContext, vs.capturerObserver)
        val vt = f.createVideoTrack("p0", vs)
        val t0 = System.currentTimeMillis()
        val waitMs = if (flash) 2000L else 700L
        var done = false

        fun cleanup() {
            try { cap.stopCapture() } catch (e: Exception) {}
            cap.dispose()
            vt.dispose()
            vs.dispose()
            h.dispose()
            busyPhoto = false
        }

        val sink = VideoSink { frame ->
            if (done) return@VideoSink
            if (System.currentTimeMillis() - t0 < waitMs) return@VideoSink
            done = true
            val jpeg = frameToJpeg(frame)
            handler.post {
                cleanup()
                if (jpeg != null) {
                    roomRef?.child("photo")?.setValue(
                        mapOf(
                            "data" to Base64.encodeToString(jpeg, Base64.NO_WRAP),
                            "ts" to ServerValue.TIMESTAMP,
                            "flash" to flash,
                            "id" to cmdId
                        )
                    )
                }
            }
        }
        vt.addSink(sink)
        cap.startCapture(640, 480, 15)
        if (flash) handler.postDelayed({ setTorch(cap, true) }, 600)

        handler.postDelayed({
            if (!done) {
                done = true
                cleanup()
            }
        }, 10_000)
    }

    private fun frameToJpeg(frame: VideoFrame): ByteArray? {
        val i420 = frame.buffer.toI420() ?: return null
        try {
            val w = i420.width
            val h = i420.height
            val nv21 = ByteArray(w * h * 3 / 2)
            val y = i420.dataY
            val ys = i420.strideY
            for (row in 0 until h) {
                y.position(row * ys)
                y.get(nv21, row * w, w)
            }
            val u = i420.dataU
            val v = i420.dataV
            val us = i420.strideU
            val vs = i420.strideV
            var o = w * h
            for (row in 0 until h / 2) {
                for (col in 0 until w / 2) {
                    nv21[o++] = v.get(row * vs + col)
                    nv21[o++] = u.get(row * us + col)
                }
            }
            val out = ByteArrayOutputStream()
            YuvImage(nv21, ImageFormat.NV21, w, h, null)
                .compressToJpeg(Rect(0, 0, w, h), 70, out)
            var bytes = out.toByteArray()
            val rot = frame.rotation
            if (rot != 0) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                val out2 = ByteArrayOutputStream()
                r.compress(Bitmap.CompressFormat.JPEG, 70, out2)
                bytes = out2.toByteArray()
                bmp.recycle()
                r.recycle()
            }
            return bytes
        } finally {
            i420.release()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSession()
        cmdListener?.let { roomRef?.child("cmd")?.removeEventListener(it) }
        roomRef?.child("status")?.child("ts")?.setValue(0)
        factory?.dispose()
        eglBase?.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
