package com.walktalk.data.websocket

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.walktalk.data.firebase.FirebaseSignalingClient
import com.walktalk.data.signaling.SignalingChannel
import com.walktalk.data.signaling.SignalingListener
import com.walktalk.util.Constants
import kotlinx.coroutines.*
import java.net.URI

class ConnectionManager(
    private val context: Context,
    private val familyId: String,
    private val deviceId: String,
    private val listener: SignalingListener,
    private val onChannelChanged: (SignalingChannel, String) -> Unit
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var webSocketClient: SignalingClient? = null
    private var firebaseClient: FirebaseSignalingClient? = null
    private var activeChannel: SignalingChannel? = null
    private var isDiscovered = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class Mode {
        FIREBASE_SERVERLESS, // 100% Cloud - No local PC needed (Default)
        HYBRID,              // Auto: Checks for Local PC, falls back to Firebase
        LOCAL_ONLY           // Local PC on Home Wi-Fi only
    }

    private var currentMode: Mode = Mode.FIREBASE_SERVERLESS

    fun start(mode: Mode = Mode.FIREBASE_SERVERLESS) {
        currentMode = mode

        // Always initialize Firebase serverless client so the app works immediately with NO PC required
        if (firebaseClient == null) {
            firebaseClient = FirebaseSignalingClient(familyId, deviceId, listener)
        }

        when (mode) {
            Mode.FIREBASE_SERVERLESS -> {
                Log.d("ConnectionManager", "Running in 100% Serverless Cloud Mode (No PC required)")
                firebaseClient?.connect()
                activeChannel = firebaseClient
                activeChannel?.let { onChannelChanged(it, "Firebase Cloud (No PC Needed)") }
            }

            Mode.HYBRID -> {
                Log.d("ConnectionManager", "Running in Hybrid Auto Mode: Activating Firebase + probing for PC...")
                // Start Firebase immediately so communication works without waiting
                firebaseClient?.connect()
                activeChannel = firebaseClient
                activeChannel?.let { onChannelChanged(it, "Firebase Cloud (Active)") }

                // Probe local network with a 1.5s timeout; if PC is on, upgrade to WebSocket
                discoverLocalServerWithTimeout(1500L)
            }

            Mode.LOCAL_ONLY -> {
                Log.d("ConnectionManager", "Running in Local LAN Only Mode")
                discoverLocalServer()
            }
        }
    }

    private fun discoverLocalServerWithTimeout(timeoutMs: Long) {
        discoverLocalServer()

        scope.launch {
            delay(timeoutMs)
            if (!isDiscovered) {
                Log.d("ConnectionManager", "No local PC found on LAN. Probing Cloudflare Tunnel URL...")
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
                    Log.d("ConnectionManager", "WalkTalk PC found on LAN via mDNS: ${serviceInfo.serviceName}")
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            if (isDiscovered) return
                            isDiscovered = true
                            val host = resolved.host.hostAddress ?: "127.0.0.1"
                            val port = resolved.port
                            val uri = URI("ws://$host:$port")
                            Log.d("ConnectionManager", "Resolved local PC server at: $uri")
                            connectWebSocketClient(uri, "Local PC (Home LAN)")
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

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("ConnectionManager", "mDNS service lost: ${serviceInfo.serviceName}")
                // If local PC drops, seamlessly revert to Firebase
                if (currentMode == Mode.HYBRID) {
                    activeChannel = firebaseClient
                    activeChannel?.let { onChannelChanged(it, "Firebase Cloud (PC Offline)") }
                }
            }

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
            Log.e("ConnectionManager", "Service discovery error: ${e.message}")
            if (currentMode == Mode.HYBRID) {
                resolveCloudTunnelUrl()
            }
        }
    }

    private fun stopDiscovery() {
        // Safe discovery teardown
    }

    private fun resolveCloudTunnelUrl() {
        val rtdb = com.walktalk.data.firebase.FirebaseHelper.getDatabase().reference
        rtdb.child("families").child(familyId).child("server_url").get()
            .addOnSuccessListener { snapshot ->
                val urlString = snapshot.child("url").getValue(String::class.java)
                if (!urlString.isNullOrEmpty()) {
                    val wssUrl = urlString.replace("https://", "wss://").replace("http://", "ws://")
                    Log.d("ConnectionManager", "Found Cloudflare Tunnel for PC: $wssUrl")
                    connectWebSocketClient(URI(wssUrl), "Cloudflare Tunnel (PC)")
                } else {
                    Log.d("ConnectionManager", "No PC server URL in RTDB. Staying on Firebase Serverless.")
                    activeChannel = firebaseClient
                    activeChannel?.let { onChannelChanged(it, "Firebase Cloud (Serverless)") }
                }
            }
            .addOnFailureListener {
                Log.d("ConnectionManager", "RTDB server_url read failed. Staying on Firebase Serverless.")
                activeChannel = firebaseClient
                activeChannel?.let { onChannelChanged(it, "Firebase Cloud (Serverless)") }
            }
    }

    private fun connectWebSocketClient(uri: URI, statusLabel: String) {
        webSocketClient?.disconnect()
        webSocketClient = SignalingClient(
            serverUri = uri,
            familyId = familyId,
            deviceId = deviceId,
            listener = listener
        )
        webSocketClient?.connect()
        activeChannel = webSocketClient
        activeChannel?.let { onChannelChanged(it, statusLabel) }
    }

    fun getActiveChannel(): SignalingChannel? = activeChannel ?: firebaseClient

    fun destroy() {
        scope.cancel()
        webSocketClient?.disconnect()
        webSocketClient = null
        firebaseClient?.disconnect()
        firebaseClient = null
        activeChannel = null
    }
}
