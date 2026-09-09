package com.walktalk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.walktalk.data.firebase.FamilyLocation
import com.walktalk.data.firebase.LocationRepository
import com.walktalk.data.firebase.MemberPresence
import com.walktalk.data.firebase.PresenceManager
import com.walktalk.data.location.SosManager
import com.walktalk.data.signaling.SignalingChannel
import com.walktalk.data.signaling.SignalingListener
import com.walktalk.data.webrtc.WebRTCManager
import com.walktalk.data.websocket.ConnectionManager
import com.walktalk.service.CallService
import com.walktalk.service.LocationService
import com.walktalk.ui.map.FamilyMapScreen
import com.walktalk.ui.settings.SettingsScreen
import com.walktalk.ui.talk.PttCallScreen
import com.walktalk.ui.talk.TalkScreen
import com.walktalk.util.Constants
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.UUID

class MainActivity : ComponentActivity(), SignalingListener {

    private lateinit var presenceManager: PresenceManager
    private lateinit var locationRepository: LocationRepository
    private lateinit var sosManager: SosManager
    private var connectionManager: ConnectionManager? = null
    private var webRtcManager: WebRTCManager? = null
    private var activeSignalingChannel: SignalingChannel? = null
    private var activeCallId: String? = null

    private var cachedIceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
    )

    private var serverStatusLabel by mutableStateOf("Firebase Cloud (No PC Needed)")
    private var currentServerModeString by mutableStateOf("Firebase Cloud (No PC Needed)")
    private var activeCallContactState by mutableStateOf<MemberPresence?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            try {
                LocationService.start(this)
            } catch (e: Throwable) {
                Log.e("MainActivity", "Error starting LocationService: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val familyId = prefs.getString(Constants.KEY_FAMILY_ID, Constants.DEFAULT_FAMILY_ID) ?: Constants.DEFAULT_FAMILY_ID
        val deviceId = prefs.getString(Constants.KEY_DEVICE_ID, Build.MODEL) ?: Build.MODEL
        val userName = prefs.getString(Constants.KEY_USER_NAME, Build.MODEL) ?: Build.MODEL

        // Initialize singletons
        presenceManager = PresenceManager(familyId, deviceId)
        presenceManager.startPresence(userName)

        locationRepository = LocationRepository(familyId, deviceId)
        sosManager = SosManager(this, familyId, deviceId)

        // Request runtime permissions
        requestRequiredPermissions()

        // Start Connection Manager defaulting to Serverless Firebase (No PC Required)
        connectionManager = ConnectionManager(
            context = this,
            familyId = familyId,
            deviceId = deviceId,
            listener = this,
            onChannelChanged = { channel, statusLabel ->
                activeSignalingChannel = channel
                serverStatusLabel = statusLabel
            }
        )
        connectionManager?.start(ConnectionManager.Mode.FIREBASE_SERVERLESS)

        setContent {
            WalkTalkAppUI(
                familyId = familyId,
                deviceId = deviceId,
                userName = userName,
                presenceManager = presenceManager,
                locationRepository = locationRepository,
                sosManager = sosManager,
                serverStatus = serverStatusLabel,
                currentServerMode = currentServerModeString,
                activeCallContact = activeCallContactState,
                onStartCall = { contact ->
                    startCall(contact, userName)
                },
                onBroadcastCall = {
                    startCall(MemberPresence(name = "Everyone (All Family)"), userName)
                },
                onPttPressStart = {
                    webRtcManager?.setPttTransmitting(true)
                    val contact = activeCallContactState
                    if (contact != null) {
                        val targetId = if (contact.name.startsWith("Everyone")) "everyone" else contact.deviceId
                        activeSignalingChannel?.sendFloorControl(targetId, "take")
                    }
                },
                onPttPressEnd = {
                    webRtcManager?.setPttTransmitting(false)
                    val contact = activeCallContactState
                    if (contact != null) {
                        val targetId = if (contact.name.startsWith("Everyone")) "everyone" else contact.deviceId
                        activeSignalingChannel?.sendFloorControl(targetId, "release")
                    }
                },
                onToggleHandsFree = {
                    webRtcManager?.toggleHandsFreeLock()
                },
                onEndCall = {
                    endCallInternal(notifyRemote = true)
                },
                onServerModeChanged = { modeStr ->
                    currentServerModeString = modeStr
                    val mode = when (modeStr) {
                        "Firebase Cloud (No PC Needed)" -> ConnectionManager.Mode.FIREBASE_SERVERLESS
                        "Hybrid (Auto - Use PC if On)" -> ConnectionManager.Mode.HYBRID
                        "Local LAN Only (Home Wi-Fi PC)" -> ConnectionManager.Mode.LOCAL_ONLY
                        else -> ConnectionManager.Mode.FIREBASE_SERVERLESS
                    }
                    connectionManager?.start(mode)
                }
            )
        }
    }

    private fun initWebRtc(targetId: String, callId: String) {
        webRtcManager?.close()
        webRtcManager = WebRTCManager(this, object : WebRTCManager.WebRtcListener {
            override fun onLocalSdpCreated(sdp: SessionDescription) {
                if (sdp.type == SessionDescription.Type.OFFER) {
                    activeSignalingChannel?.sendSdpOffer(targetId, callId, sdp)
                } else if (sdp.type == SessionDescription.Type.ANSWER) {
                    activeSignalingChannel?.sendSdpAnswer(targetId, callId, sdp)
                }
            }

            override fun onIceCandidateGenerated(candidate: IceCandidate) {
                activeSignalingChannel?.sendIceCandidate(targetId, callId, candidate)
            }

            override fun onConnectionEstablished() {
                Log.d("MainActivity", "WebRTC PeerConnection Connected successfully")
            }

            override fun onConnectionClosed() {
                Log.d("MainActivity", "WebRTC PeerConnection Closed")
            }
        })
        webRtcManager?.startPeerConnection(cachedIceServers)
    }

    private fun startCall(contact: MemberPresence, userName: String) {
        val callId = UUID.randomUUID().toString()
        activeCallId = callId
        activeCallContactState = contact
        CallService.start(this, contact.name)
        val targetId = if (contact.name.startsWith("Everyone")) "everyone" else contact.deviceId
        initWebRtc(targetId, callId)
        activeSignalingChannel?.sendCallRequest(targetId, callId, userName, "ptt")
        webRtcManager?.createOffer()
    }

    private fun endCallInternal(notifyRemote: Boolean = true) {
        val contact = activeCallContactState
        val callId = activeCallId
        if (notifyRemote && contact != null && callId != null) {
            val targetId = if (contact.name.startsWith("Everyone")) "everyone" else contact.deviceId
            activeSignalingChannel?.sendEndCall(targetId, callId)
        }
        webRtcManager?.close()
        webRtcManager = null
        activeCallId = null
        activeCallContactState = null
        CallService.stop(this)
    }

    // SignalingListener overrides
    override fun onConnected() {
        Log.d("MainActivity", "Signaling channel connected")
    }

    override fun onDisconnected() {
        Log.d("MainActivity", "Signaling channel disconnected")
    }

    override fun onIceServersReceived(iceServers: List<PeerConnection.IceServer>) {
        if (iceServers.isNotEmpty()) {
            cachedIceServers = iceServers
        }
    }

    override fun onIncomingCall(callId: String, callerId: String, callerName: String, mode: String) {
        runOnUiThread {
            activeCallId = callId
            activeCallContactState = MemberPresence(deviceId = callerId, name = callerName, status = "in_call")
            CallService.start(this, callerName)
            initWebRtc(callerId, callId)
        }
    }

    override fun onSdpOfferReceived(callId: String, senderId: String, sdp: SessionDescription) {
        runOnUiThread {
            if (webRtcManager == null) {
                initWebRtc(senderId, callId)
            }
            webRtcManager?.handleRemoteOfferAndAnswer(sdp)
        }
    }

    override fun onSdpAnswerReceived(callId: String, senderId: String, sdp: SessionDescription) {
        runOnUiThread {
            webRtcManager?.handleRemoteAnswer(sdp)
        }
    }

    override fun onIceCandidateReceived(callId: String, senderId: String, candidate: IceCandidate) {
        runOnUiThread {
            webRtcManager?.addRemoteIceCandidate(candidate)
        }
    }

    override fun onFloorControl(action: String, senderId: String) {
        Log.d("MainActivity", "Floor control action: $action by $senderId")
    }

    override fun onCallEnded(callId: String) {
        runOnUiThread {
            endCallInternal(notifyRemote = false)
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            try {
                LocationService.start(this)
            } catch (e: Throwable) {
                Log.e("MainActivity", "Error starting LocationService: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        endCallInternal(notifyRemote = true)
        connectionManager?.destroy()
        super.onDestroy()
    }
}

@Composable
fun WalkTalkAppUI(
    familyId: String,
    deviceId: String,
    userName: String,
    presenceManager: PresenceManager,
    locationRepository: LocationRepository,
    sosManager: SosManager,
    serverStatus: String,
    currentServerMode: String,
    activeCallContact: MemberPresence?,
    onStartCall: (MemberPresence) -> Unit,
    onBroadcastCall: () -> Unit,
    onPttPressStart: () -> Unit,
    onPttPressEnd: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onEndCall: () -> Unit,
    onServerModeChanged: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val familyMembers by presenceManager.observeFamilyPresence().collectAsState(initial = emptyList())
    val familyLocations by locationRepository.observeFamilyLocations().collectAsState(initial = emptyList())

    if (activeCallContact != null) {
        val contact = activeCallContact
        var isHandsFreeLocked by remember { mutableStateOf(false) }

        PttCallScreen(
            contactName = contact.name,
            locationContext = "Online (${contact.status})",
            isHandsFreeLocked = isHandsFreeLocked,
            onPttPressStart = onPttPressStart,
            onPttPressEnd = onPttPressEnd,
            onToggleHandsFree = {
                isHandsFreeLocked = !isHandsFreeLocked
                onToggleHandsFree()
            },
            onQuickReactionSent = { _ -> },
            onEndCall = onEndCall
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1E1E1E)) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Talk") },
                        label = { Text("Intercom") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00E676))
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Place, contentDescription = "Map") },
                        label = { Text("Map") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00E676))
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00E676))
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    0 -> TalkScreen(
                        familyMembers = familyMembers,
                        serverStatus = serverStatus,
                        onMemberSelected = onStartCall,
                        onBroadcastEveryone = onBroadcastCall
                    )
                    1 -> FamilyMapScreen(
                        familyLocations = familyLocations,
                        onTriggerSos = {
                            sosManager.triggerSos(userName, 28.6139, 77.2090)
                        }
                    )
                    2 -> SettingsScreen(
                        currentServerMode = currentServerMode,
                        familyId = familyId,
                        deviceId = deviceId,
                        tunnelUrl = "",
                        isGhostMode = false,
                        onServerModeChanged = onServerModeChanged,
                        onGhostModeToggled = {}
                    )
                }
            }
        }
    }
}
