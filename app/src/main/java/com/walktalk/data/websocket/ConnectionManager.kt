package com.walktalk.data.websocket

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.walktalk.util.Constants
import kotlinx.coroutines.*
import java.net.URI

class ConnectionManager(
    private val context: Context,
    private val familyId: String,
    private val deviceId: String,
    private val onConnectionReady: (SignalingClient) -> Unit,
    private val onFallbackToRtdb: () -> Unit
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var signalingClient: SignalingClient? = null
    private var isDiscovered = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class Mode {
        HYBRID,
        LOCAL_ONLY,
        CLOUD_ONLY
    }

    fun start(mode: Mode = Mode.HYBRID) {
        when (mode) {
            Mode.LOCAL_ONLY -> discoverLocalServer()
            Mode.CLOUD_ONLY -> resolveCloudTunnelUrl()
            Mode.HYBRID -> {
                // Discover local server with a 1.5s timeout, then fallback to Cloudflare Tunnel
                discoverLocalServerWithTimeout(1500L)
            }
        }
    }

    private fun discoverLocalServerWithTimeout(timeoutMs: Long) {
        discoverLocalServer()

        scope.launch {
            delay(timeoutMs)
            if (!isDiscovered) {
                Log.d("ConnectionManager", "Local mDNS discovery timed out. Falling back to Cloudflare Tunnel...")
                stopDiscovery()
                resolveCloudTunnelUrl()
            }
        }
    }

    private fun discoverLocalServer() {
        isDiscovered = false
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("walktalk") || serviceInfo.serviceName.contains("WalkTalk")) {
                    Log.d("ConnectionManager", "WalkTalk mDNS service found: ${serviceInfo.serviceName}")
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            if (isDiscovered) return
                            isDiscovered = true
                            val host = resolved.host.hostAddress ?: "127.0.0.1"
                            val port = resolved.port
                            val uri = URI("ws://$host:$port")
                            Log.d("ConnectionManager", "Resolved local server at: $uri")
                            connectSignalingClient(uri)
                        }

                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.w("ConnectionManager", "NSD resolve failed: code $errorCode")
                        }
                    })
                }
            }

            override fun onDiscoveryStarted(regType: String) {
                Log.d("ConnectionManager", "NSD discovery started for $regType")
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        try {
            nsdManager.discoverServices(Constants.MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Failed to start service discovery: ${e.message}")
            resolveCloudTunnelUrl()
        }
    }

    private fun stopDiscovery() {
        // Safe discovery stop
    }

    private fun resolveCloudTunnelUrl() {
        val rtdb = FirebaseDatabase.getInstance().reference
        rtdb.child("families").child(familyId).child("server_url").get()
            .addOnSuccessListener { snapshot ->
                val urlString = snapshot.child("url").getValue(String::class.java)
                if (!urlString.isNullOrEmpty()) {
                    val wssUrl = urlString.replace("https://", "wss://").replace("http://", "ws://")
                    Log.d("ConnectionManager", "Resolved Cloudflare Tunnel URL from RTDB: $wssUrl")
                    connectSignalingClient(URI(wssUrl))
                } else {
                    Log.w("ConnectionManager", "No server_url found in RTDB. Using RTDB fallback signaling.")
                    onFallbackToRtdb()
                }
            }
            .addOnFailureListener {
                Log.e("ConnectionManager", "Failed to read server_url from RTDB: ${it.message}")
                onFallbackToRtdb()
            }
    }

    private fun connectSignalingClient(uri: URI) {
        signalingClient?.disconnect()
        signalingClient = SignalingClient(
            serverUri = uri,
            familyId = familyId,
            deviceId = deviceId,
            listener = object : SignalingClient.SignalingListener {
                override fun onConnected() {
                    Log.d("ConnectionManager", "Signaling client successfully connected!")
                    signalingClient?.let { onConnectionReady(it) }
                }

                override fun onDisconnected() {
                    Log.w("ConnectionManager", "Signaling client disconnected. Retrying...")
                }

                override fun onIceServersReceived(iceServers: List<org.webrtc.PeerConnection.IceServer>) {}
                override fun onIncomingCall(callId: String, callerId: String, callerName: String, mode: String) {}
                override fun onSdpOfferReceived(callId: String, senderId: String, sdp: org.webrtc.SessionDescription) {}
                override fun onSdpAnswerReceived(callId: String, senderId: String, sdp: org.webrtc.SessionDescription) {}
                override fun onIceCandidateReceived(callId: String, senderId: String, candidate: org.webrtc.IceCandidate) {}
                override fun onFloorControl(action: String, senderId: String) {}
                override fun onCallEnded(callId: String) {}
            }
        )
        signalingClient?.connect()
    }

    fun destroy() {
        scope.cancel()
        signalingClient?.disconnect()
        signalingClient = null
    }
}
