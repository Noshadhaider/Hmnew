package com.homecam.app

import android.content.Context
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

object Prefs {
    private const val FILE = "homecam"
    fun room(@Suppress("UNUSED_PARAMETER") ctx: Context): String = Config.ROOM

    fun deviceId(ctx: Context): String {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        var id = sp.getString("device_id", null)
        if (id == null) {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val r = java.security.SecureRandom()
            id = (1..10).map { chars[r.nextInt(chars.length)] }.joinToString("")
            sp.edit().putString("device_id", id).apply()
        }
        return id!!
    }

    fun deviceName(ctx: Context): String {
        val saved = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("device_name", null)
        if (!saved.isNullOrBlank()) return saved
        val man = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL
        return if (model.startsWith(man, ignoreCase = true)) model else "$man $model"
    }

    fun setDeviceName(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("device_name", v.trim()).apply()
    }

    fun cameraEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("camera_enabled", false)

    fun setCameraEnabled(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("camera_enabled", v).apply()
    }
}

object Db {
    fun deviceRef(ctx: Context): DatabaseReference =
        FirebaseDatabase.getInstance().getReference("rooms").child(Config.ROOM)
            .child("devices").child(Prefs.deviceId(ctx))

    fun validRoom(room: String) = room.length >= 12 && room.all { it.isLetterOrDigit() }
}

object Rtc {
    private var initialized = false

    fun init(ctx: Context) {
        if (initialized) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext)
                .createInitializationOptions()
        )
        initialized = true
    }

    fun iceServers(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    fun config(): PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

    fun constraints(): MediaConstraints = MediaConstraints()

    fun candidateToMap(c: IceCandidate): Map<String, Any> = mapOf(
        "sdpMid" to (c.sdpMid ?: ""),
        "sdpMLineIndex" to c.sdpMLineIndex,
        "candidate" to c.sdp
    )

    fun mapToCandidate(m: Map<*, *>): IceCandidate? {
        val sdp = m["candidate"] as? String ?: return null
        val mid = m["sdpMid"] as? String ?: ""
        val idx = (m["sdpMLineIndex"] as? Number)?.toInt() ?: 0
        return IceCandidate(mid, idx, sdp)
    }

    fun descToMap(d: SessionDescription): Map<String, Any> = mapOf(
        "type" to d.type.canonicalForm(),
        "sdp" to d.description
    )
}

open class SimplePcObserver : PeerConnection.Observer {
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(p0: Boolean) {}
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(p0: IceCandidate?) {}
    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
    override fun onAddStream(p0: MediaStream?) {}
    override fun onRemoveStream(p0: MediaStream?) {}
    override fun onDataChannel(p0: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
